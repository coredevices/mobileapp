import PebbleProtocol
import UIKit
import SwiftUI
import Foundation
import CryptoKit
import AccessorySetupKit
import CoreBluetooth
import ComposeApp
import notify

enum PairedEnrollmentOfferError: Error, LocalizedError {
    case invalidFormat
    case differentWatch
    var errorDescription: String? {
        switch self {
        case .invalidFormat: return "The watch enrollment response has an unsupported format. Existing keys were preserved."
        case .differentWatch: return "The watch enrollment response does not match the saved watch identity. Existing keys were preserved."
        }
    }
}
enum PairedEnrollmentOffer {
    case proposal(watch: Data, challenge: Data)
    case acknowledgement(Data)
    static func parse(_ bytes: Data) throws -> Self {
        if bytes.count == 100, bytes.prefix(4) == Data("PEA1".utf8) { return .acknowledgement(bytes) }
        if bytes.count == 101, bytes.prefix(4) == Data("PEN1".utf8) {
            return .proposal(watch: bytes.subdata(in: 4..<69), challenge: bytes.suffix(32))
        }
        throw PairedEnrollmentOfferError.invalidFormat
    }
}

// Explicit user cancellation is distinct from genuine consent/verification failure.
enum PairedEnrollmentUserCancellation: Error { case cancelled }

// Pure state seams used by the native screen and source-only qualification fixtures.
struct PairedEnrollmentPresentationGate {
    private(set) var token: UUID?
    mutating func acquire() -> UUID? {
        guard token == nil else { return nil }
        let next = UUID(); token = next; return next
    }
    mutating func release(_ completed: UUID) {
        guard token == completed else { return }
        token = nil
    }
}
enum PairedEnrollmentScreenPolicy {
    static func canAddWatch(activated: Bool, busy: Bool, finished: Bool) -> Bool {
        activated && !busy && !finished
    }
    static func shouldPresentResult(_ result: Result<UUID, any Error>) -> Bool {
        if case .failure(let error) = result, error is PairedEnrollmentUserCancellation { return false }
        return true
    }
    static func canStart(activated: Bool, busy: Bool, finished: Bool,
                         selected: UUID?, accessory: UUID?, authorized: Bool) -> Bool {
        guard let selected, let accessory else { return false }
        return activated && !busy && !finished && authorized && selected == accessory
    }
}

@MainActor
@available(iOS 26.5, *)
final class PairedManualEnrollmentController: UITableViewController {
    private static var presentationGate = PairedEnrollmentPresentationGate()
    private static var guidedCoordinator: PairedManualEnrollmentController?
    private var guided = false
    private weak var comparisonAlert: UIAlertController?
    // Retain the actual ASK event source after UI dismissal. This is a live
    // in-memory observer, never a persisted permission/readiness assertion.
    private static var authorizationMonitor: PairedManualEnrollmentController?
    private static var pendingAuthorizationMonitor: PairedManualEnrollmentController?
    private var authorizationObserverOnly = false
    // Restore actual Apple authorization on launch without opening a picker,
    // choosing a watch, comparing codes, or requesting forwarding permission.
    static func restoreAuthorizationObservation() {
        guard PairedNotificationGate.enabled, authorizationMonitor == nil,
              pendingAuthorizationMonitor == nil else { return }
        let observer = PairedManualEnrollmentController(style: .insetGrouped)
        observer.authorizationObserverOnly = true
        pendingAuthorizationMonitor = observer
        observer.loadViewIfNeeded()
    }
    private var presentationToken: UUID?
    private var attemptIdentifier: UUID?
    private var activationFailed = false
    private var selectedWatchIdentifier: UUID?
    private var openPickerOnActivation = false
    private var pickerAwaitingSelection = false
    private var pickerSelectedIdentifier: UUID?
    private let session = ASAccessorySession()
    private var choices: [ASAccessory] = []
    private var channel: PairedMainBLEChannel?
    private var work: Task<Void, Never>?
    private var busy = false
    private var finished = false
    private var activated = false
    private var failureStage = "Accessory setup"
    private var deliveryTraceText = "Send a real notification, then read the watch trace to locate the first failed boundary."
    private var probeObserverTokens: [Int32] = []
    private var statusText = "Select this watch to check approval or continue setup. Notification delivery has not been verified."
    private static func foregroundPresenter() -> UIViewController? {
        guard let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene })
                .first(where: { $0.activationState == .foregroundActive }),
              var root = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController else { return nil }
        while let presented = root.presentedViewController { root = presented }
        return root
    }
    static func show(selectedWatchIdentifier: UUID?, addWatch: Bool = false) {
        guard PairedNotificationGate.enabled, !PairedBootstrapEnrollmentPresenter.shared.isActive,
              let root = foregroundPresenter(), let token = presentationGate.acquire() else { return }
        let controller = PairedManualEnrollmentController(style: .insetGrouped)
        controller.presentationToken = token
        controller.selectedWatchIdentifier = selectedWatchIdentifier
        controller.openPickerOnActivation = addWatch
        controller.guided = addWatch
        if addWatch {
            // Retain before activation: Apple owns the picker, not a diagnostic sheet.
            guidedCoordinator = controller
            controller.loadViewIfNeeded()
        } else {
            root.present(UINavigationController(rootViewController: controller), animated: true)
        }
    }
    private func finishGuided() {
        guard guided else { return }
        finished = true; busy = false; attemptIdentifier = nil
        openPickerOnActivation = false; pickerAwaitingSelection = false
        authorizationObserverOnly = true
        endPresentation()
        if Self.guidedCoordinator === self { Self.guidedCoordinator = nil }
        // authorizationMonitor continues to own the live ASK session.
    }
    deinit { for token in probeObserverTokens { _ = notify_cancel(token) } }
    override func viewDidLoad() {
        super.viewDidLoad(); title = "Secure notifications"
        // Observation only; these callbacks never advance a setup/auth state.
        let probes: [(PairedCapabilityProbe.Event, PairedDiagnosticStatus.Event)] = [
            (.securityInitialized, .probeSecurityInitialized),
            (.securityAcceptEntered, .probeSecurityAcceptEntered),
            (.securityPublicKeySubmitted, .probeSecurityPublicKeySubmitted),
            (.securityMaterialCallbackEntered, .probeSecurityMaterialCallbackEntered),
            (.securityDefaultsWriteReturned, .probeSecurityDefaultsWriteReturned),
            (.providerInitialized, .probeProviderInitialized), (.providerActivated, .probeProviderActivated),
            (.privateFixtureCreated, .probePrivateFixtureCreated), (.privateFixtureReadPassed, .probePrivateFixtureReadPassed),
            (.privatePriorFixtureReadPassed, .probePrivatePriorFixtureReadPassed),
            (.privateFixtureUnavailable, .probePrivateFixtureUnavailable),
            (.sharedReadOnlyLockPassed, .probeSharedReadOnlyLockPassed),
            (.sharedReadOnlyLockUnavailable, .probeSharedReadOnlyLockUnavailable)]
        for (probe, event) in probes {
            var token: Int32 = 0
            if notify_register_dispatch(NotificationBridgeConfiguration.notificationName("probe.v1.") + probe.rawValue,
                                        &token, .main, { [weak self] _ in
                MainActor.assumeIsolated {
                    guard let self else { return }
                    PairedDiagnosticStatus.record(event)
                    self.tableView.reloadData()
                }
            }) == 0 { probeObserverTokens.append(token) }
        }
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 76
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "Cancel", style: .plain,
            target: self, action: #selector(cancel))
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "Add watch", style: .plain,
            target: self, action: #selector(addWatch))
        updateControls()
        isModalInPresentation = true
        session.activate(on: .main) { [weak self] event in
            guard event.eventType == .invalidated || event.eventType == .activated || event.eventType == .accessoryAdded ||
                    event.eventType == .accessoryRemoved || event.eventType == .accessoryChanged || event.eventType == .pickerDidDismiss else { return }
            MainActor.assumeIsolated {
                guard let self else { return }
                if event.eventType == .activated, Self.authorizationMonitor !== self {
                    let previous = Self.authorizationMonitor
                    Self.authorizationMonitor = self
                    if Self.pendingAuthorizationMonitor === self { Self.pendingAuthorizationMonitor = nil }
                    previous?.session.invalidate()
                }
                if Self.authorizationMonitor === self {
                    let identifiers = event.eventType == .invalidated ? [] : self.session.accessories.compactMap {
                        $0.state == .authorized ? $0.bluetoothIdentifier?.uuidString : nil
                    }
                    IOSDelegate.shared.updateAccessoryAuthorizations(identifiers: identifiers)
                }
                if event.eventType == .invalidated {
                    if Self.authorizationMonitor === self { Self.authorizationMonitor = nil }
                    if Self.pendingAuthorizationMonitor === self { Self.pendingAuthorizationMonitor = nil }
                    self.activated = false; self.activationFailed = true
                    self.choices = []; self.tableView.reloadData(); self.updateControls()
                    self.failureStage = "Apple accessory session activation"
                    self.channel?.cancel(); self.work?.cancel()
                    if !self.authorizationObserverOnly {
                        self.failed(event.error ?? PairedOwnerError.unavailable as NSError)
                    }
                    return
                }
                if event.eventType == .activated {
                    self.activated = true; self.activationFailed = false
                }
                // Only an accessory from this explicit picker may select a new UUID.
                // Activation/restoration never silently chooses an existing watch.
                if self.pickerAwaitingSelection, event.eventType == .accessoryAdded,
                   let offered = event.accessory, offered.state == .authorized,
                   let identifier = offered.bluetoothIdentifier,
                   self.selectedWatchIdentifier == nil || self.selectedWatchIdentifier == identifier {
                    self.selectedWatchIdentifier = identifier
                    self.pickerSelectedIdentifier = identifier
                }
                self.choices = self.session.accessories.filter {
                    $0.state == .authorized && $0.bluetoothIdentifier != nil &&
                        PairedSelectedWatchReadiness.matches(selected: self.selectedWatchIdentifier, accessory: $0.bluetoothIdentifier)
                }
                self.tableView.reloadData(); self.updateControls()
                guard !self.finished else { return }
                if event.eventType == .activated, self.openPickerOnActivation {
                    self.openPickerOnActivation = false
                    self.addWatch()
                }
                if event.eventType == .pickerDidDismiss {
                    self.pickerAwaitingSelection = false
                    if let picked = self.pickerSelectedIdentifier,
                       let exact = self.choices.first(where: { $0.bluetoothIdentifier == picked }) {
                        self.pickerSelectedIdentifier = nil
                        self.start(exact)
                    } else {
                        self.pickerSelectedIdentifier = nil
                        if self.guided { self.finishGuided(); return }
                        self.statusText = "Choose your authorized watch below to continue the same setup flow, or use Add watch. No watch was chosen automatically."
                        self.tableView.reloadData()
                    }
                }
                if self.busy, let selected = self.selectedWatchIdentifier,
                   !self.choices.contains(where: { $0.bluetoothIdentifier == selected }) {
                    if self.finished {
                        // Bootstrap owns completion now; revoke its actual link
                        // and let requireConnection report the genuine failure.
                        self.channel?.cancel(); self.work?.cancel(); return
                    }
                    self.failureStage = "Selected watch authorization"
                    self.failed(NSError(domain: "PebbleD.AccessorySetup", code: 2, userInfo: [
                        NSLocalizedDescriptionKey: "The selected watch is no longer authorized. No other watch will be used."
                    ]))
                }
            }
        }
    }
    @objc private func addWatch() {
        guard PairedEnrollmentScreenPolicy.canAddWatch(activated: activated, busy: busy, finished: finished),
              PairedNotificationGate.enabled else { return }
        let descriptor = ASDiscoveryDescriptor()
        descriptor.bluetoothServiceUUID = CBUUID(string: "0000FED9-0000-1000-8000-00805F9B34FB")
        descriptor.supportedOptions = .bluetoothPairingLE
        guard let service = descriptor.bluetoothServiceUUID,
              let declared = Bundle.main.object(forInfoDictionaryKey: "NSAccessorySetupBluetoothServices") as? [String],
              declared.contains(service.uuidString) else {
            failureStage = "Accessory discovery configuration"
            failed(NSError(domain: "PebbleD.AccessorySetup", code: 1, userInfo: [
                NSLocalizedDescriptionKey: "The discovery service is missing from this app's accessory allowlist."
            ]))
            return
        }
        let item = ASPickerDisplayItem(name: "Pebble", productImage: UIImage(systemName: "applewatch") ?? UIImage(),
            descriptor: descriptor)
        failureStage = "Apple accessory picker"
        pickerAwaitingSelection = true; pickerSelectedIdentifier = nil
        statusText = "Authorize the watch in Apple's picker. Then compare the genuine codes and approve notification permission in this same flow."
        tableView.reloadData()
        session.showPicker(for: [item]) { @Sendable [weak self] error in
            guard let error else { return }
            Task { @MainActor [weak self] in
                self?.pickerAwaitingSelection = false; self?.pickerSelectedIdentifier = nil
                self?.failed(error)
            }
        }
    }
    override func numberOfSections(in tableView: UITableView) -> Int { 3 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        section == 0 ? max(choices.count, 1) : 1
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        cell.textLabel?.numberOfLines = 0
        cell.detailTextLabel?.numberOfLines = 0
        if indexPath.section == 1 {
            cell.textLabel?.text = "Session status"
            cell.detailTextLabel?.text = statusText + "\n\n" + PairedDiagnosticStatus.summary() + "\n\n" +
                PairedCapabilityProbe.mainObservation(protectedDataAvailable: UIApplication.shared.isProtectedDataAvailable)
            cell.selectionStyle = .none
            return cell
        }
        if indexPath.section == 2 {
            cell.textLabel?.text = busy ? "Reading watch delivery trace…" : "Read watch delivery trace"
            cell.detailTextLabel?.text = deliveryTraceText
            cell.accessoryType = busy ? .none : .disclosureIndicator
            cell.selectionStyle = busy ? .none : .default
            return cell
        }
        if choices.isEmpty {
            if !activated {
                cell.textLabel?.text = activationFailed ? "Accessory setup is unavailable" : "Loading authorized watches…"
                cell.detailTextLabel?.text = activationFailed ? "Close this screen and reopen Secure notifications to retry. Existing identities are preserved." : "Add watch becomes available when Apple accessory setup is ready."
                cell.selectionStyle = .none
                return cell
            }
            cell.textLabel?.text = selectedWatchIdentifier == nil ? "Authorize your watch with Add watch" : "Selected watch is not authorized"
            if selectedWatchIdentifier != nil {
                cell.detailTextLabel?.text = "Use Add watch to authorize this exact watch. No other watch will be selected."
            }
            if selectedWatchIdentifier == nil {
                cell.detailTextLabel?.text = "An existing Bluetooth connection does not grant accessory permission."
            }
            cell.selectionStyle = .none
        } else {
            cell.textLabel?.text = choices[indexPath.row].displayName
            cell.detailTextLabel?.text = "Open watch Settings → Bluetooth → Secure notifications"
        }
        return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        if indexPath.section == 2 {
            guard !busy, !finished else { return }
            readDeliveryTrace()
            return
        }
        guard indexPath.section == 0, !busy, !finished, choices.indices.contains(indexPath.row) else { return }
        let accessory = choices[indexPath.row]
        // The explicit row tap can resume an already authorized watch; bind it
        // before any BLE/identity operation, never use an arbitrary restored row.
        if selectedWatchIdentifier == nil { selectedWatchIdentifier = accessory.bluetoothIdentifier }
        start(accessory)
    }
    private func readDeliveryTrace() {
        guard let selected = selectedWatchIdentifier,
              let accessory = session.accessories.first(where: {
                  $0.state == .authorized && $0.bluetoothIdentifier == selected
              }) else {
            deliveryTraceText = "The selected watch is not currently authorized. Use Add watch, then retry."
            tableView.reloadData()
            return
        }
        let attempt = UUID(); attemptIdentifier = attempt
        busy = true; updateControls(); tableView.reloadData()
        let probe = PairedMainBLEChannel(); channel = probe
        work = Task { [weak self] in
            do {
                try await probe.connect(accessory)
                let data = try await probe.readValue(PairedMainBLEChannel.deliveryStatus)
                let status = try PairedWatchDeliveryStatus(data)
                probe.cancel()
                guard let self, !self.finished, self.attemptIdentifier == attempt else { return }
                self.deliveryTraceText = status.summary
                self.busy = false; self.attemptIdentifier = nil; self.work = nil; self.channel = nil
                self.updateControls(); self.tableView.reloadData()
            } catch {
                probe.cancel()
                guard let self, !self.finished, self.attemptIdentifier == attempt else { return }
                let native = error as NSError
                self.deliveryTraceText = "Trace read failed: \(native.domain) (\(native.code)). Install the matching watch firmware and retry."
                self.busy = false; self.attemptIdentifier = nil; self.work = nil; self.channel = nil
                self.updateControls(); self.tableView.reloadData()
            }
        }
    }
    private func start(_ accessory: ASAccessory) {
        guard PairedNotificationGate.enabled,
              PairedEnrollmentScreenPolicy.canStart(activated: activated, busy: busy, finished: finished,
                selected: selectedWatchIdentifier, accessory: accessory.bluetoothIdentifier,
                authorized: accessory.state == .authorized) else { return }
        // Re-read live ASK authorization before publishing this exact UUID to
        // the ordinary PPoG discovery/WatchManager route. No name fallback,
        // broad scan, KnownWatch metadata invention or permission flag.
        guard let selected = selectedWatchIdentifier,
              let current = session.accessories.first(where: {
                  $0.state == .authorized && $0.bluetoothIdentifier == selected
              }) else { return }
        IOSDelegate.shared.updateAccessoryAuthorizations(identifiers: session.accessories.compactMap {
            $0.state == .authorized ? $0.bluetoothIdentifier?.uuidString : nil
        })
        failureStage = "Connecting to the authorized watch"
        let attempt = UUID(); attemptIdentifier = attempt
        busy = true; statusText = "Checking approval for the selected watch…"; tableView.reloadData(); updateControls()
        let channel = PairedMainBLEChannel(); self.channel = channel
        work = Task { [weak self] in
            do {
                try Task.checkCancellation()
                guard let controller = self, !controller.finished,
                      controller.attemptIdentifier == attempt,
                      controller.selectedWatchIdentifier == selected,
                      controller.session.accessories.contains(where: {
                          $0.state == .authorized && $0.bluetoothIdentifier == selected
                      }) else { throw PairedOwnerError.invalid }
                controller.failureStage = "Registering the authorized watch"
                try IOSDelegate.shared.connectAuthorizedWatch(identifier: selected.uuidString, name: current.displayName)
                self?.failureStage = "Reading the saved owner identity"
                let activeIdentity = try PairedPhysicalIdentity.existingActiveForExplicitResume()
                if activeIdentity != nil {
                    // Only the authenticated, exact-selected, current lease and
                    // signed readiness/ledger checks authorize this shortcut.
                    do {
                        let context = try PairedNativeSessionFactory.loadValidatedPublication()
                        guard context.selectedPeripheral == accessory.bluetoothIdentifier else { throw PairedOwnerError.invalid }
                        try PairedNativeSessionFactory.requireReadiness(context: context)
                        guard let self, !self.finished, self.attemptIdentifier == attempt else { return }
                        self.busy = false; self.attemptIdentifier = nil
                        self.work = nil; self.channel = nil
                        self.statusText = "Approved session available for this watch. Notification delivery has not been verified. Send a real notification and check the watch."
                        self.tableView.reloadData(); self.updateControls()
                        self.finishGuided()
                        return
                    } catch {
                        // Failed readiness grants no authority. Continue only the
                        // explicit existing-identity ceremony; all data gates remain.
                    }
                }
                self?.failureStage = "Waiting for this watch's ordinary connection"
                self?.statusText = "Connecting your watch normally before optional secure notification setup. Existing keys are preserved."
                self?.tableView.reloadData()
                guard let selected = accessory.bluetoothIdentifier else { throw PairedOwnerError.invalid }
                let ordinaryConnected = try await IOSDelegate.shared.awaitAuthorizedWatchConnection(identifier: selected.uuidString)
                guard ordinaryConnected.boolValue else {
                    throw NSError(domain: "PebbleD.OrdinaryConnection", code: 1, userInfo: [
                        NSLocalizedDescriptionKey: "The ordinary watch connection has not completed. Its connection attempt remains separate; no owner proposal or secure session was resent."
                    ])
                }
                try Task.checkCancellation()
                guard let currentController = self, !currentController.finished,
                      currentController.attemptIdentifier == attempt,
                      currentController.selectedWatchIdentifier == selected,
                      currentController.session.accessories.contains(where: {
                          $0.state == .authorized && $0.bluetoothIdentifier == selected
                      }) else { throw PairedOwnerError.invalid }
                try await channel.connect(accessory)
                self?.statusText = "Connected for setup. Approval and notification delivery are not yet verified."
                self?.tableView.reloadData()
                self?.failureStage = "Reading the saved owner identity"
                // Absence is proven only by Keychain item-not-found. Any protected,
                // corrupt or entitlement failure stops before reading/staging an offer.
                if try PairedPhysicalIdentity.existingActiveForExplicitResume() == nil {
                    self?.failureStage = "Reading the watch enrollment offer over Bluetooth"
                    let offerBytes = try await channel.readValue(PairedMainBLEChannel.enrollment)
                    self?.failureStage = "Parsing the watch enrollment offer"
                    let item: PairedPhysicalIdentity.Staged
                    switch try PairedEnrollmentOffer.parse(offerBytes) {
                    case .acknowledgement(let receipt):
                        self?.failureStage = "Loading the saved identity for watch confirmation"
                        item = try PairedPhysicalIdentity.existingForExplicitResume()
                        self?.failureStage = "Verifying the signed watch confirmation"
                        try PairedPhysicalIdentity.verifyReceipt(item, receipt)
                        self?.failureStage = "Persisting the confirmed existing identity"
                        try PairedPhysicalIdentity.activate(item, signedWatchAcknowledgement: receipt)
                    case .proposal(let watch, let watchChallenge):
                        self?.failureStage = "Loading or staging the explicitly selected owner identity"
                        item = try PairedPhysicalIdentity.stage(afterExplicitConsent: watch)
                        let code = try PairedPhysicalIdentity.code(watch: item.pinnedWatchPublic,
                            owner: item.owner.publicKey.x963Representation)
                        let proposal = try PairedPhysicalIdentity.proposal(item, challenge: watchChallenge)
                        self?.failureStage = "Sending the signed owner proposal"
                        try await channel.writeValue(proposal, to: PairedMainBLEChannel.enrollment)
                        guard let self else { throw PairedOwnerError.unavailable }
                        self.failureStage = "Waiting for physical code confirmation"
                        try await self.compare(code)
                        let start = ContinuousClock.now
                        while true {
                            try Task.checkCancellation()
                            self.failureStage = "Reading watch confirmation over Bluetooth"
                            let response = try await channel.readValue(PairedMainBLEChannel.enrollment)
                            self.failureStage = "Parsing watch confirmation"
                            switch try PairedEnrollmentOffer.parse(response) {
                            case .acknowledgement(let receipt):
                                self.failureStage = "Verifying the signed watch confirmation"
                                try PairedPhysicalIdentity.verifyReceipt(item, receipt)
                                self.failureStage = "Persisting the confirmed existing identity"
                                try PairedPhysicalIdentity.activate(item, signedWatchAcknowledgement: receipt)
                            case .proposal(let offeredWatch, _):
                                // Draw004 continues to return PEN1 while physical
                                // confirmation is pending. Never resend PEP1 here.
                                guard offeredWatch == item.pinnedWatchPublic else { throw PairedEnrollmentOfferError.differentWatch }
                                self.failureStage = "Waiting for the watch's signed confirmation"
                                guard ContinuousClock.now - start < .seconds(30) else { throw PairedOwnerError.stale }
                                try await Task.sleep(for: .milliseconds(250))
                                continue
                            }
                            break
                        }
                    }
                }
                try Task.checkCancellation()
                guard let self, !self.finished, self.attemptIdentifier == attempt else { throw PairedOwnerError.invalid }
                self.failureStage = "Apple consent and session approval"
                self.statusText = "Compare the genuine codes, then approve Apple's permission request. Notification delivery remains unverified."
                self.tableView.reloadData()
                PairedBootstrapEnrollmentPresenter.shared.receive(PairedBootstrapEnrollmentInput(
                    accessory: accessory,
                    requiresPhysicalComparison: activeIdentity == nil,
                    readChallenge: { try await channel.readValue(PairedMainBLEChannel.challenge) },
                    readRelay: { try await channel.readValue(PairedMainBLEChannel.relay) },
                    exchange: { try await channel.exchange($0) },
                    sendSession: { try await channel.sendSession($0) },
                    requireConnection: { try channel.requireCurrent() },
                    observeDisconnect: { channel.onLinkDropped = $0 },
                    retainConnection: { try channel.retainThroughSession(expiresMillis: $0) },
                    cancelExchange: { channel.cancel() },
                    completion: { [self] result in
                        guard !self.finished, self.attemptIdentifier == attempt else { return }
                        self.work = nil; self.channel = nil
                        switch result {
                        case .success:
                            self.busy = false; self.attemptIdentifier = nil
                            self.statusText = "Session approval completed. Notification delivery has not been verified. Send a real notification and check the watch; approval alone does not prove delivery."
                            self.tableView.reloadData(); self.updateControls()
                            self.finishGuided()
                        case .failure(let error) where error is PairedEnrollmentUserCancellation:
                            self.busy = false; self.attemptIdentifier = nil
                            self.statusText = "Setup cancelled. Existing identity is preserved. Select the watch when you want to continue."
                            self.tableView.reloadData(); self.updateControls()
                            self.finishGuided()
                        case .failure(let error): self.failed(error)
                        }
                    }))
            } catch {
                guard let self, !self.finished, self.attemptIdentifier == attempt else { return }
                self.failed(error)
            }
        }
    }
    private var comparison: CheckedContinuation<Void, any Error>?
    private var comparisonDeadline: Task<Void, Never>?
    private func compare(_ code: Data) async throws {
        let groups = stride(from: 0, to: code.count, by: 4).map {
            code[$0..<min($0 + 4, code.count)].map { String(format: "%02X", $0) }.joined()
        }.joined(separator: "\n")
        try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, any Error>) in
                guard let presenter = Self.foregroundPresenter() else {
                    continuation.resume(throwing: PairedOwnerError.unavailable)
                    return
                }
                comparison = continuation
                let prompt = UIAlertController(title: "Compare all eight groups",
                    message: groups + "\n\nIf every group matches, press Select on the watch. Then tap Watch confirmed here.", preferredStyle: .alert)
                prompt.addAction(UIAlertAction(title: "Cancel", style: .cancel) { [weak self] _ in self?.cancel() })
                prompt.addAction(UIAlertAction(title: "Watch confirmed", style: .default) { [weak self] _ in self?.completeComparison(true) })
                comparisonAlert = prompt
                presenter.present(prompt, animated: true)
                comparisonDeadline = Task { [weak self] in
                    try? await Task.sleep(for: .seconds(30))
                    guard !Task.isCancelled else { return }
                    self?.completeComparison(false)
                }
            }
        }, onCancel: { Task { @MainActor [weak self] in self?.completeComparison(false) } })
    }
    private func completeComparison(_ confirmed: Bool, onDismiss: (() -> Void)? = nil) {
        let continuation = comparison; comparison = nil
        comparisonDeadline?.cancel(); comparisonDeadline = nil
        let complete = {
            if confirmed { continuation?.resume() } else { continuation?.resume(throwing: PairedOwnerError.unavailable) }
            onDismiss?()
        }
        let prompt = comparisonAlert; comparisonAlert = nil
        if let prompt, prompt.presentingViewController != nil {
            prompt.dismiss(animated: true, completion: complete)
        } else { complete() }
    }
    @objc private func cancel() {
        guard !finished else { return }
        finished = true; attemptIdentifier = nil; updateControls()
        work?.cancel(); work = nil; channel?.cancel(); channel = nil; /* Keep live ASK authorization observation after Cancel. */
        completeComparison(false) { [self] in
            if guided { finishGuided() }
            else { dismiss(animated: true) { [weak self] in self?.endPresentation() } }
        }
        // Pending owner is retained: watch commit may have happened just before cancellation.
    }
    private func failed(_ error: any Error) {
        guard !finished else { return }
        let nativeError = error as NSError
        let diagnostic = "\(nativeError.domain) (\(nativeError.code)): \(nativeError.localizedDescription.prefix(240))"
        attemptIdentifier = nil; work?.cancel(); work = nil
        busy = false; channel?.cancel(); channel = nil; updateControls()
        statusText = "Setup stopped at: \(failureStage).\n\n\(diagnostic)\n\nExisting identity is preserved. Unlock the phone if needed, check this watch's Secure notifications screen, then select it to retry. If it is not listed, use Add watch. Approval does not prove notification delivery."
        completeComparison(false) { [self] in
            if guided {
                let stage = failureStage
                finishGuided()
                guard let root = Self.foregroundPresenter() else { return }
                let alert = UIAlertController(title: "Watch setup could not finish",
                    message: "Setup stopped at: \(stage). Try Add Watch again. Existing pairing is preserved.", preferredStyle: .alert)
                alert.addAction(UIAlertAction(title: "OK", style: .default))
                root.present(alert, animated: true)
            } else { tableView.reloadData() }
        }
    }

    private func updateControls() {
        navigationItem.rightBarButtonItem?.isEnabled = PairedEnrollmentScreenPolicy.canAddWatch(activated: activated, busy: busy, finished: finished)
        tableView.isUserInteractionEnabled = activated && !busy && !finished
    }
    private func endPresentation() {
        guard let token = presentationToken else { return }
        Self.presentationGate.release(token); presentationToken = nil
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        guard !guided else { return }
        if isBeingDismissed || navigationController?.isBeingDismissed == true {
            for token in probeObserverTokens { _ = notify_cancel(token) }
            probeObserverTokens = []
        }
        if navigationController?.presentingViewController == nil {
            if !finished {
                finished = true; attemptIdentifier = nil; work?.cancel(); work = nil
                channel?.cancel(); channel = nil; completeComparison(false)
            }
            endPresentation()
        }
    }

}


/// The selected-device entry never constructs an Apple session while the recovery gate is held.
@MainActor
enum PairedSelectedWatchReadiness {
    case unavailable(String)
    case ready(UUID)
    static func matches(selected: UUID?, accessory: UUID?) -> Bool {
        guard let accessory else { return false }
        return selected == nil || selected == accessory
    }
    static func resolve(identifier: String?, supported: Bool, enabled: Bool) -> Self {
        guard let identifier, let uuid = UUID(uuidString: identifier) else {
            return .unavailable("This selected device has no usable Bluetooth identity. No other watch will be chosen.")
        }
        guard supported else { return .unavailable("Secure notifications require iOS 26.5 or later. Existing notifications and identities are preserved.") }
        guard enabled else { return .unavailable("Secure notifications are temporarily unavailable in this recovery build. Existing notifications, pairings and identities are preserved. Setup will resume after the compatible connection path is verified.") }
        return .ready(uuid)
    }
}

@MainActor
enum PairedSelectedWatchMenu {
    static func receive(identifier: String?, addWatch: Bool = false) {
        if addWatch {
            if #available(iOS 26.5, *), PairedNotificationGate.enabled {
                PairedManualEnrollmentController.show(selectedWatchIdentifier: nil, addWatch: true)
                return
            }
            // Keep the existing unavailable explanation, never fall back to a
            // second legacy pairing path for a failed guided request.
        }
        let supported: Bool
        if #available(iOS 26.5, *) { supported = true } else { supported = false }
        switch PairedSelectedWatchReadiness.resolve(identifier: identifier, supported: supported,
                                                    enabled: PairedNotificationGate.enabled) {
        case .ready(let uuid):
            if #available(iOS 26.5, *) { PairedManualEnrollmentController.show(selectedWatchIdentifier: uuid) }
        case .unavailable(let reason):
            guard let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene })
                .first(where: { $0.activationState == .foregroundActive }),
                var root = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController else { return }
            while let presented = root.presentedViewController { root = presented }
            let alert = UIAlertController(title: "Secure notifications", message: reason, preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "OK", style: .default))
            root.present(alert, animated: true)
        }
    }
}
