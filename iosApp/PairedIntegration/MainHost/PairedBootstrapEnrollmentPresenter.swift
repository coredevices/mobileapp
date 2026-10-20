import AccessoryNotifications
import AccessorySetupKit
import Foundation
import UIKit

@MainActor
@available(iOS 26.5, *)
struct PairedBootstrapEnrollmentInput {
    let accessory: ASAccessory
    var requiresPhysicalComparison: Bool = true
    let readChallenge: @MainActor () async throws -> Data
    let readRelay: @MainActor () async throws -> Data
    let exchange: @MainActor (Data) async throws -> Data
    let sendSession: @MainActor (Data) async throws -> Void
    let requireConnection: @MainActor () throws -> Void
    let observeDisconnect: @MainActor (@escaping () -> Void) -> Void
    let retainConnection: @MainActor (UInt64) throws -> Void
    let cancelExchange: @MainActor () -> Void
    let completion: @MainActor (Result<UUID, any Error>) -> Void
}

/// Main presents Apple's permission for the selected ASK accessory. Security
/// and Transport each own their actual Apple session and watch BLE exchange.
@MainActor
@available(iOS 26.5, *)
final class PairedBootstrapEnrollmentPresenter {
    static let shared = PairedBootstrapEnrollmentPresenter()
    var isActive: Bool { token != nil }

    private var token: UUID?
    private var task: Task<Void, Never>?
    private var input: PairedBootstrapEnrollmentInput?
    private weak var alert: UIAlertController?

    func receive(_ input: PairedBootstrapEnrollmentInput) {
        guard PairedNotificationGate.enabled, token == nil,
              input.accessory.state == .authorized,
              let selected = input.accessory.bluetoothIdentifier,
              let scene = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .first(where: { $0.activationState == .foregroundActive }),
              var controller = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController else {
            PairedDiagnosticStatus.record(.mainFailure)
            input.cancelExchange()
            input.completion(.failure(PairedOwnerError.unavailable))
            return
        }
        while let presented = controller.presentedViewController { controller = presented }
        let current = UUID()
        token = current
        self.input = input
        let prompt = UIAlertController(title: "Secure notifications",
            message: "Continue to Apple's forwarding permission for the selected watch. The app will check delivery separately.",
            preferredStyle: .alert)
        prompt.addAction(UIAlertAction(title: "Cancel", style: .cancel) { [weak self] _ in
            self?.finish(current, result: .failure(PairedEnrollmentUserCancellation.cancelled))
        })
        prompt.addAction(UIAlertAction(title: "Continue", style: .default) { [weak self] _ in
            guard let self, self.token == current else { return }
            self.task = Task { [weak self] in
                guard let self else { return }
                do {
                    guard input.accessory.state == .authorized,
                          input.accessory.bluetoothIdentifier == selected else {
                        throw PairedOwnerError.stale
                    }
                    PairedDiagnosticStatus.record(.permissionRequested)
                    let decision = try await AccessoryNotificationCenter().requestForwarding(for: input.accessory)
                    guard self.token == current, input.accessory.state == .authorized,
                          input.accessory.bluetoothIdentifier == selected else {
                        throw PairedOwnerError.stale
                    }
                    guard decision == .allow || decision == .limited else {
                        PairedDiagnosticStatus.record(.permissionDenied)
                        throw PairedOwnerError.unavailable
                    }
                    PairedDiagnosticStatus.record(.permissionAllowed)
                    self.finish(current, result: .success(selected))
                } catch {
                    PairedDiagnosticStatus.record(.permissionFailed, error: error)
                    self.finish(current, result: .failure(error))
                }
            }
        })
        alert = prompt
        controller.present(prompt, animated: true)
    }

    private func finish(_ current: UUID, result: Result<UUID, any Error>) {
        guard token == current else { return }
        if case .failure(let error) = result {
            if error is PairedEnrollmentUserCancellation { PairedDiagnosticStatus.record(.mainCancelled) }
            else { PairedDiagnosticStatus.record(.mainFailure, error: error) }
        }
        token = nil
        task?.cancel()
        task = nil
        alert?.dismiss(animated: true)
        alert = nil
        let finished = input
        input = nil
        if case .failure = result { finished?.cancelExchange() }
        finished?.completion(result)
    }
}
