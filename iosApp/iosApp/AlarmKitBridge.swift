import Foundation
import SwiftUI
import UIKit
import AVFoundation
import AlarmKit
import AppIntents
import ComposeApp

/// Registers AlarmKit (iOS 26+) with the Kotlin side.
enum AlarmKitBridge {
    static func register() {
        IOSDelegate.shared.registerNativeAlarmKit(
            authorizationState: {
                guard #available(iOS 26.0, *) else { return "denied" }
                switch AlarmManager.shared.authorizationState {
                case .authorized: return "authorized"
                case .denied: return "denied"
                case .notDetermined: return "notDetermined"
                @unknown default: return "denied"
                }
            },
            requestAuthorization: { completion in
                guard #available(iOS 26.0, *) else {
                    completion(KotlinBoolean(false))
                    return
                }
                Task {
                    let state = try? await AlarmManager.shared.requestAuthorization()
                    completion(KotlinBoolean(value: state == .authorized))
                }
            },
            schedule: { id, title, fireAt, deepLink, completion in
                guard #available(iOS 26.0, *) else {
                    completion("AlarmKit requires iOS 26+")
                    return
                }
                Task {
                    do {
                        try await schedule(id: id, title: title, fireAt: fireAt, deepLink: deepLink)
                        completion(nil)
                    } catch {
                        completion(String(describing: error))
                    }
                }
            },
            cancel: { id in
                guard #available(iOS 26.0, *), let alarmId = UUID(uuidString: id) else { return }
                // An alerting alarm needs stop(); a pending one needs cancel(). Each throws for the other state.
                try? AlarmManager.shared.stop(id: alarmId)
                try? AlarmManager.shared.cancel(id: alarmId)
            }
        )
        if #available(iOS 26.0, *) {
            Task { @MainActor in ForegroundAlarmPresenter.shared.start() }
        }
    }

    /// Alert-only, with no countdown presentation: a countdown needs a widget extension, without
    /// which the system may drop the alarm.
    @available(iOS 26.0, *)
    private static func schedule(id: String, title: String, fireAt: Date, deepLink: String) async throws {
        guard let alarmId = UUID(uuidString: id) else {
            throw BridgeError("Invalid alarm id \(id)")
        }
        let manager = AlarmManager.shared
        // Authorization is requested through the app's permission flow; scheduling while
        // undetermined would prompt out of band.
        guard manager.authorizationState == .authorized else {
            throw BridgeError("Alarm permission not granted; enable it in the app's permissions")
        }
        let alert = AlarmPresentation.Alert(
            title: LocalizedStringResource(stringLiteral: title),
            stopButton: AlarmButton(text: "Stop", textColor: .white, systemImageName: "stop.fill"),
            secondaryButton: AlarmButton(text: "Open", textColor: .white, systemImageName: "arrow.up.right.square"),
            secondaryButtonBehavior: .custom
        )
        let attributes = AlarmAttributes<ClockMetadata>(
            presentation: AlarmPresentation(alert: alert),
            tintColor: Color(red: 0xFA / 255.0, green: 0x4A / 255.0, blue: 0x36 / 255.0)
        )
        let configuration = AlarmManager.AlarmConfiguration<ClockMetadata>.alarm(
            schedule: .fixed(fireAt),
            attributes: attributes,
            secondaryIntent: OpenTimersAndAlarmsIntent(deepLink: deepLink)
        )
        _ = try await manager.schedule(id: alarmId, configuration: configuration)
        await ForegroundAlarmPresenter.shared.remember(title: title, for: alarmId)
    }

    private struct ClockMetadata: AlarmMetadata {}

    private struct BridgeError: Error, CustomStringConvertible {
        let description: String
        init(_ description: String) { self.description = description }
    }
}

/// Backs the alert's Open button: brings the app to the foreground on the timers and alarms list.
@available(iOS 26.0, *)
struct OpenTimersAndAlarmsIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Open timers and alarms"
    static let openAppWhenRun = true

    @Parameter(title: "Deep link")
    var deepLink: String

    init() {
        deepLink = ""
    }

    init(deepLink: String) {
        self.deepLink = deepLink
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        if let url = URL(string: deepLink) {
            _ = IOSDelegate.shared.handleOpenUrl(url: url)
        }
        return .result()
    }
}

/// The system only presents an alarm while the app is in the background. In the foreground the
/// alarm merely moves to `.alerting`, so this plays the alarm tone and shows a Stop alert instead.
@available(iOS 26.0, *)
@MainActor
final class ForegroundAlarmPresenter {
    static let shared = ForegroundAlarmPresenter()

    private var presented: (id: UUID, alert: UIAlertController)?
    private var player: AVAudioPlayer?

    // Alarm updates carry no attributes, so the titles are kept here; they survive relaunch.
    private var titles: [String: String] {
        get { UserDefaults.standard.dictionary(forKey: Self.titlesKey) as? [String: String] ?? [:] }
        set { UserDefaults.standard.set(newValue, forKey: Self.titlesKey) }
    }

    func start() {
        Task {
            for await alarms in AlarmManager.shared.alarmUpdates {
                update(alarms)
            }
        }
    }

    func remember(title: String, for id: UUID) {
        titles[id.uuidString] = title
    }

    private func update(_ alarms: [Alarm]) {
        let ids = Set(alarms.map { $0.id.uuidString })
        titles = titles.filter { ids.contains($0.key) }
        let alerting = alarms.first { $0.state == .alerting }
        if let current = presented, current.id != alerting?.id {
            dismiss()
        }
        guard let alarm = alerting, presented == nil, UIApplication.shared.applicationState == .active else { return }
        present(alarm)
    }

    private func present(_ alarm: Alarm) {
        guard let presenter = topViewController() else { return }
        let alert = UIAlertController(title: titles[alarm.id.uuidString] ?? "Alarm", message: nil, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Stop", style: .default) { [weak self] _ in
            try? AlarmManager.shared.stop(id: alarm.id)
            self?.dismiss()
        })
        presenter.present(alert, animated: true)
        presented = (alarm.id, alert)
        startSound()
    }

    private func dismiss() {
        presented?.alert.dismiss(animated: true)
        presented = nil
        stopSound()
    }

    private func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var top = scene?.keyWindow?.rootViewController
        while let next = top?.presentedViewController {
            top = next
        }
        return top
    }

    // Same session handling as the Kotlin AudioPlayer; `.playback` also ignores the silent switch.
    private func startSound() {
        guard let url = Bundle.main.url(forResource: "ring-clock-alarm", withExtension: "caf") else { return }
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback)
        try? session.setActive(true)
        player = try? AVAudioPlayer(contentsOf: url)
        player?.numberOfLoops = -1
        player?.play()
    }

    private func stopSound() {
        guard player != nil else { return }
        player?.stop()
        player = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private static let titlesKey = "ring-clock-alarm-titles"
}
