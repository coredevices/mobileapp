import PebbleProtocol
import UIKit
import Foundation
import AccessorySetupKit

// The platform bootstrap bridge must supply Apple's actual provider UUID and
// HPKE identifier plus the selected watch's challenge/exchange. No fabricated
// UUID, key generation or remote enrollment fallback is supplied here.
@MainActor
@available(iOS 26.5, *)
struct PairedPlatformEnrollmentInput {
    let sessionID: UUID
    let accessory: ASAccessory
    let hpkeIdentifier: String
    let watchChallenge: Data
    let exchange: @MainActor (Data) async throws -> Data
    // Cancel/detach this proof exchange only; preserve the existing app/watch link.
    let cancelExchange: @MainActor () -> Void
    let completion: @MainActor (Result<UUID, any Error>) -> Void
}

extension Notification.Name {
    static let pebbledPairedEnrollmentRequested = Notification.Name(NotificationBridgeConfiguration.notificationName("paired-enrollment-requested"))
}

@MainActor
@available(iOS 26.5, *)
final class PairedMainEnrollmentPresenter {
    static let shared = PairedMainEnrollmentPresenter()
    private var generation: UUID?
    private var deadline: Task<Void, Never>?
    private var exchangeTask: Task<Void, Never>?
    private var publisher: PairedMainPublisher?
    private weak var alert: UIAlertController?
    private var input: PairedPlatformEnrollmentInput?
    private var selectedPeripheral: UUID?

    func receive(_ notification: Notification) {
        guard PairedNotificationGate.enabled else { return }
        if let bootstrap = notification.object as? PairedBootstrapEnrollmentInput {
            guard generation == nil else { bootstrap.completion(.failure(PairedPublicationError.unavailable)); return }
            PairedBootstrapEnrollmentPresenter.shared.receive(bootstrap); return
        }
        guard !PairedBootstrapEnrollmentPresenter.shared.isActive,
              let input = notification.object as? PairedPlatformEnrollmentInput else { return }
        guard generation == nil, let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene }).first(where: { $0.activationState == .foregroundActive }),
            var controller = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController else {
            input.completion(.failure(PairedPublicationError.unavailable)); return
        }
        while let presented = controller.presentedViewController { controller = presented }
        do {
            guard input.accessory.state == .authorized, let selectedPeripheral = input.accessory.bluetoothIdentifier else {
                throw PairedPublicationError.selectedWatchBindingRequired
            }
            let (watch, owner) = try PairedNativeSessionFactory.readIdentity()
            let directory = try PairedNativeSessionFactory.privateDirectory(create: true)
            let publisher = try PairedMainPublisher(watch: watch, owner: owner, directory: directory,
                clock: { (try? PairedNativeSessionFactory.nowMillis()) ?? UInt64.max },
                boot: { try PairedNativeSessionFactory.bootMillis() })
            let consent = try publisher.begin(challenge: input.watchChallenge,
                accessory: input.hpkeIdentifier, sessionID: input.sessionID, selectedPeripheral: selectedPeripheral)
            let token = UUID(); generation = token; self.publisher = publisher; self.input = input; self.selectedPeripheral = selectedPeripheral
            let hex = consent.fingerprint.map { String(format: "%02X", $0) }
            let groups = stride(from: 0, to: hex.count, by: 4).map { hex[$0..<min($0 + 4, hex.count)].joined() }
            let prompt = UIAlertController(title: "Pair secure notifications",
                message: "Compare this code with the watch and approve there first. Confirm only if every group matches.\n\n" + groups.joined(separator: " "),
                preferredStyle: .alert)
            prompt.addAction(UIAlertAction(title: "Cancel", style: .cancel) { [weak self] _ in
                self?.finish(token, result: .failure(PairedPublicationError.consent))
            })
            prompt.addAction(UIAlertAction(title: "Codes match — confirmed on watch", style: .default) { [weak self] _ in
                self?.confirm(token, fingerprint: consent.fingerprint)
            })
            alert = prompt; controller.present(prompt, animated: true)
            deadline = Task { [weak self] in
                try? await Task.sleep(for: .seconds(30))
                guard !Task.isCancelled else { return }
                self?.finish(token, result: .failure(PairedPublicationError.expired))
            }
        } catch { input.completion(.failure(error)) }
    }

    private func confirm(_ token: UUID, fingerprint: Data) {
        guard generation == token, let publisher, let input else { return }
        do {
            let request = try publisher.confirm(displayedFingerprint: fingerprint)
            exchangeTask = Task { [weak self] in
                do {
                    let proof = try await input.exchange(request)
                    try Task.checkCancellation()
                    guard self?.generation == token else { return }
                    guard input.accessory.state == .authorized, input.accessory.bluetoothIdentifier == self?.selectedPeripheral else {
                        throw PairedPublicationError.selectedWatchBindingRequired
                    }
                    let session = try publisher.publish(watchProof: proof)
                    self?.finish(token, result: .success(session))
                } catch { self?.finish(token, result: .failure(error)) }
            }
        } catch { finish(token, result: .failure(error)) }
    }

    private func finish(_ token: UUID, result: Result<UUID, any Error>) {
        guard generation == token else { return }
        generation = nil; publisher?.cancel(); publisher = nil
        deadline?.cancel(); deadline = nil; exchangeTask?.cancel(); exchangeTask = nil
        alert?.dismiss(animated: true); alert = nil
        let completed = input; input = nil; selectedPeripheral = nil
        completed?.cancelExchange(); completed?.completion(result)
    }
}
