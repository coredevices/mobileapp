import PebbleProtocol
import Foundation
import CryptoKit
import notify

// This gate remains false until provisioning and paired transport review are complete.
enum PairedNotificationGate { static var enabled: Bool { NotificationBridgeConfiguration.enabled } }

// No notification content is stored here; both copies use the same App Group preference.
enum NotificationDismissalSettings {
    private static let suite = NotificationBridgeConfiguration.appGroup
    private static let key = "notificationDismissOnBoth.v1"
    private static let changed = NotificationBridgeConfiguration.notificationName("notification-dismissal.v1")
    static var onBothDevices: Bool {
        get {
            guard let defaults = UserDefaults(suiteName: suite) else { return false }
            defaults.synchronize()
            return defaults.bool(forKey: key)
        }
        set {
            guard let defaults = UserDefaults(suiteName: suite) else { return }
            defaults.set(newValue, forKey: key)
            defaults.synchronize()
            notify_post(changed)
        }
    }
    static func observe(_ callback: @escaping @Sendable () -> Void) -> Int32? {
        var token: Int32 = 0
        return notify_register_dispatch(changed, &token, .global(qos: .userInitiated), { _ in callback() }) == 0 ? token : nil
    }
    static func stopObserving(_ token: Int32) { _ = notify_cancel(token) }
}


enum PairedOwnerError: Error { case invalid, stale, pending, unavailable, ambiguousResponse }
// Only a caller that knows no Apple API invocation occurred may use this error.
enum PairedResponseFailure: Error { case notSubmitted }
struct PairedOwnerEnvelope {
    enum Domain: String { case outer = "POT1", receipt = "POR1" }
    let domain: Domain
    enum Kind: UInt8 { case session = 1, data = 2, reply = 3, ack = 4 }
    let kind: Kind
    let direction: UInt8
    let nonce: Data
    let sequence: UInt64
    let request: Data
    let payload: Data

    init(domain: Domain = .outer, kind: Kind, direction: UInt8, nonce: Data,
         sequence: UInt64, request: Data, payload: Data) {
        self.domain = domain; self.kind = kind; self.direction = direction
        self.nonce = nonce; self.sequence = sequence; self.request = request; self.payload = payload
    }

    func signed(using key: P256.Signing.PrivateKey) throws -> Data {
        guard nonce.count == 32, request.count == 16, payload.count <= 2304,
              direction <= 1 else { throw PairedOwnerError.invalid }
        var bytes = Data(domain.rawValue.utf8)
        bytes.append(contentsOf: [kind.rawValue, direction])
        bytes.append(nonce)
        for shift in stride(from: 56, through: 0, by: -8) {
            bytes.append(UInt8(truncatingIfNeeded: sequence >> shift))
        }
        bytes.append(request)
        bytes.append(UInt8(payload.count >> 8)); bytes.append(UInt8(payload.count & 255))
        bytes.append(payload)
        bytes.append(try key.signature(for: bytes).rawRepresentation)
        return bytes
    }

    static func verified(_ bytes: Data, using key: P256.Signing.PublicKey) throws -> Self {
        let b = [UInt8](bytes)
        guard b.count >= 128, let domain = Domain(rawValue: String(decoding: b[0..<4], as: UTF8.self)), b[5] <= 1,
              let kind = Kind(rawValue: b[4]) else { throw PairedOwnerError.invalid }
        let length = Int(b[62]) * 256 + Int(b[63])
        guard length <= 2304, b.count == 128 + length else { throw PairedOwnerError.invalid }
        let signature = try P256.Signing.ECDSASignature(rawRepresentation: Data(b.suffix(64)))
        guard key.isValidSignature(signature, for: Data(b.dropLast(64))) else {
            throw PairedOwnerError.invalid
        }
        let seq = b[38..<46].reduce(UInt64(0)) { ($0 << 8) | UInt64($1) }
        return Self(domain: domain, kind: kind, direction: b[5], nonce: Data(b[6..<38]),
                    sequence: seq, request: Data(b[46..<62]), payload: Data(b[64..<(64 + length)]))
    }

    static func sessionPayload(watch: Data, owner: Data, encapsulated: Data,
                               feature: Data, accessory: Data) throws -> Data {
        guard watch.count == 65, owner.count == 65, encapsulated.count == 65,
              watch.first == 4, owner.first == 4, encapsulated.first == 4,
              !feature.isEmpty, feature.count <= 64, !accessory.isEmpty, accessory.count <= 64 else {
            throw PairedOwnerError.invalid
        }
        return watch + owner + encapsulated + Data([UInt8(feature.count)]) + feature
            + Data([UInt8(accessory.count)]) + accessory
    }
}

// One actor owns every outbound sequence for an authenticated session.
protocol PairedReplyDelivering: Actor {
    func deliver(_ wire: Data, sendResponse: @Sendable (Data) async throws -> Void,
                 sendACK: @Sendable (Data) async throws -> Void) async throws
    func retry(sendResponse: @Sendable (Data) async throws -> Void,
               sendACK: @Sendable (Data) async throws -> Void) async throws
    func invalidate() async
}

actor PairedReplyDelivery: PairedReplyDelivering {
    private let watch: P256.Signing.PublicKey
    private let owner: P256.Signing.PrivateKey
    private let nonce: Data
    private var receiveSequence: UInt64 = 0
    private var sendSequence: UInt64 = 0
    private struct Pending {
        let envelope: PairedOwnerEnvelope
        enum ResponseState { case notStarted, succeeded, uncertain }
        var responseState = ResponseState.notStarted
        var ack: Data?
    }
    private var pending: Pending?
    private var completed: Set<Data> = []
    private var valid = true
    private var busy = false

    init(watch: P256.Signing.PublicKey, owner: P256.Signing.PrivateKey, nonce: Data) throws {
        guard nonce.count == 32 else { throw PairedOwnerError.invalid }
        self.watch = watch; self.owner = owner; self.nonce = nonce
    }

    // All phone messages must use this allocator. An unacknowledged reply reserves
    // the stream until its identical signed ACK has been successfully retried.
    func signData(request: Data, payload: Data) throws -> Data {
        guard valid else { throw PairedOwnerError.unavailable }
        guard !busy, pending == nil else { throw PairedOwnerError.pending }
        return try allocate(kind: .data, request: request, payload: payload)
    }

    private func allocate(kind: PairedOwnerEnvelope.Kind, request: Data, payload: Data) throws -> Data {
        guard sendSequence < UInt64.max else { throw PairedOwnerError.unavailable }
        let wire = try PairedOwnerEnvelope(kind: kind, direction: 0, nonce: nonce,
            sequence: sendSequence + 1, request: request, payload: payload).signed(using: owner)
        sendSequence += 1
        return wire
    }

    func deliver(_ wire: Data, sendResponse: @Sendable (Data) async throws -> Void,
                 sendACK: @Sendable (Data) async throws -> Void) async throws {
        try Task.checkCancellation()
        guard valid else { throw PairedOwnerError.unavailable }
        guard !busy, pending == nil else { throw PairedOwnerError.pending }
        let envelope = try PairedOwnerEnvelope.verified(wire, using: watch)
        guard envelope.domain == .outer, envelope.kind == .reply, envelope.direction == 1, envelope.nonce == nonce,
              receiveSequence < UInt64.max, envelope.sequence == receiveSequence + 1,
              !completed.contains(envelope.request), completed.count < 64 else {
            throw PairedOwnerError.stale
        }
        pending = Pending(envelope: envelope)
        try await retry(sendResponse: sendResponse, sendACK: sendACK)
    }

    // A successful Apple response is never sent twice when only the ACK failed.
    // Transport disconnect leaves pending state intact; explicit invalidation
    // cancels the session and requires fresh authentication.
    func retry(sendResponse: @Sendable (Data) async throws -> Void,
               sendACK: @Sendable (Data) async throws -> Void) async throws {
        try Task.checkCancellation()
        guard valid else { throw PairedOwnerError.unavailable }
        guard !busy, var delivery = pending else { throw PairedOwnerError.pending }
        guard delivery.responseState != .uncertain else { throw PairedOwnerError.ambiguousResponse }
        busy = true
        defer { busy = false }
        if delivery.responseState == .notStarted {
            do {
                try await sendResponse(delivery.envelope.payload)
            } catch {
                // An Apple error/cancellation does not prove the effect never happened.
                // Never automatically invoke it again after an ambiguous completion.
                if valid {
                    delivery.responseState = error is PairedResponseFailure ? .notStarted : .uncertain
                    pending = delivery
                }
                throw error
            }
            guard valid else { throw PairedOwnerError.unavailable }
            delivery.responseState = .succeeded
            pending = delivery
        }
        try Task.checkCancellation()
        if delivery.ack == nil {
            delivery.ack = try allocate(kind: .ack, request: delivery.envelope.request, payload: Data())
            pending = delivery
        }
        try await sendACK(delivery.ack!)
        guard valid else { throw PairedOwnerError.unavailable }
        receiveSequence = delivery.envelope.sequence
        completed.insert(delivery.envelope.request)
        pending = nil
    }

    func invalidate() { valid = false; pending = nil }
}

// Best-effort historical diagnostics only. No diagnostic state is consulted by
// authentication, publication, readiness, counters or delivery decisions.
enum PairedDiagnosticStatus {
    enum Event: String, CaseIterable {
        case probeSecurityInitialized, probeSecurityAcceptEntered, probeSecurityPublicKeySubmitted
        case probeSecurityMaterialCallbackEntered, probeSecurityDefaultsWriteReturned
        case probeProviderInitialized, probeProviderActivated, probePrivateFixtureCreated
        case probePrivateFixtureReadPassed, probePrivatePriorFixtureReadPassed, probePrivateFixtureUnavailable
        case probeSharedReadOnlyLockPassed, probeSharedReadOnlyLockUnavailable
        case bleWaiting, blePoweredOn, bleConnected, bleServices, bleCharacteristics
        case bleReadFailure, bleLinkLost, bleFailure
        case consentConfirmed, challengeRead, bootstrapStarted, permissionRequested
        case permissionAllowed, permissionDenied, permissionFailed, approvalExpired
        case waitingSecurityCapture, waitingBootstrapReceipt, waitingApplePermission
        case waitingProviderCapture, waitingProviderSession, waitingAccessoryAuthorization
        case waitingSecurityMaterial, waitingProofExchange, waitingSessionWrite
        case bootstrapReceipt, proofRequested, proofPublished, sessionAcknowledged, readinessCommitted, mainFailure, mainCancelled
        case providerActivated, providerCaptured, providerBound, providerInvalidated, providerFailure
        case securityWaitingConsent
        case securityRequested, securityPublicKeySent, securityMaterialReceived, securityCaptured
        case bootstrapStoreBusy, securityBootstrapLoadFailed, securityBootstrapBindFailed
        case securityIdentityReadStarted, securityIdentityReadSucceeded
        case securityDirectoryReadStarted, securityDirectoryReadSucceeded
        case securityConsentReadStarted, securityConsentReadSucceeded
        case securityMessageRejected, securityIdentifierMissing, securityBootstrapMissing
        case securityCaptureRejected, securityStaleAttempt, securityKeyShapeRejected
        case securityCaptureValidationRejected, securityCaptureWriteFailed, securityCaptureBusyExhausted
        case securityReceiptAccepted, securityInvalidated, securityFailure
        case securityCallbackBusy, securityCallbackInvalidated, securityRelayLinkStarted, securityRelayStageStarted
        case securityLinkUnavailable, securityLinkTimedOut, securityLinkWriteFailed
        case extensionAccessoryActivated, extensionAccessoryInvalidated, extensionBLELinkDropped
        case extensionBluetoothUnknown, extensionBluetoothResetting, extensionBluetoothUnsupported
        case extensionBluetoothUnauthorized, extensionBluetoothPoweredOff, extensionBluetoothPoweredOn
        case extensionBLEConnected, extensionBLEServices, extensionBLECharacteristics
        case extensionWriteSizeRejected, extensionBLEWriteFailed
        case notificationDispatch, notificationDispatchAccepted, notificationDispatchFailure
        case notificationAdded, notificationUpdated, notificationSilent, notificationHistorical
        case notificationIneligible, notificationFocusSuppressed
        case notificationAlerted, notificationNotAlerted, notificationReceiptTimedOut
        case transportRequested, transportAccepted, transportMessageReceived
        case transportWriteAcknowledged, transportResponseDispatched, transportInvalidated, transportFailure
        case transportReverseFrameReceived, transportReversePacketReceived, transportReverseFrameRejected
        case transportReverseSessionRejected, transportProviderHandoffFailed, transportProviderHandoffAccepted
        case transportReverseAcknowledgementFailed, transportReverseFailure
        case providerResponseReceived, providerResponseSessionRejected, providerResponseDecoded
        case providerResponseMappingRejected, providerResponseSubmitted, providerResponseAccepted, providerResponseFailed
    }
    private static let suite = NotificationBridgeConfiguration.appGroup
    private static let prefix = "paired.diagnostic.v1."
    private static func allowedError(domain: String, code: Int) -> Bool {
        switch domain {
        case "CBATTErrorDomain": return (0...17).contains(code)
        case "CBErrorDomain": return (0...20).contains(code)
        case "PebbleD.SecureConnection": return code == 1
        case "PebbleD.SecureRead": return code == 1 || code == 2
        case NSOSStatusErrorDomain: return [-50, -25299, -25300, -25308, -34018].contains(code)
        case NSCocoaErrorDomain: return [4, 256, 257, 260, 513].contains(code)
        default: return false
        }
    }
    private static let stageLogger = Logger(subsystem: NotificationBridgeConfiguration.namespace, category: "paired-bootstrap-diagnostic")
    // Only enum-controlled stages and allowlisted domain/code pairs reach the
    // public log. Never include descriptions, userInfo or arbitrary domains.
    static func formatStageLog(_ event: Event, error: (any Error)? = nil) -> String {
        guard let error else { return event.rawValue }
        let value = error as NSError
        guard allowedError(domain: value.domain, code: value.code) else {
            return event.rawValue + " error=unavailable"
        }
        return event.rawValue + " error=" + value.domain + " code=" + String(value.code)
    }
    static func record(_ event: Event, error: (any Error)? = nil, bluetoothState: Int? = nil) {
        // Emit before either the restricted provider exit or shared-defaults
        // access, so shared diagnostics failure cannot hide callback progress.
        stageLogger.notice("\(formatStageLog(event, error: error), privacy: .public)")
        // Provider and Security cannot publish into their read-only App Group.
        // Independent fixed OSLog/Darwin stages carry no keys or authority.
        if Bundle.main.bundleIdentifier?.hasSuffix(".notificationProvider") == true
            || Bundle.main.bundleIdentifier?.hasSuffix(".notificationSecurity") == true {
            return
        }
        guard let defaults = UserDefaults(suiteName: suite) else { return }
        let key = prefix + event.rawValue
        let previous = defaults.dictionary(forKey: key)?["count"] as? Int ?? 0
        var observation: [String: Any] = ["count": min(max(previous, 0), 999998) + 1,
                                         "timestamp": Date().timeIntervalSince1970]
        if let error {
            let e = error as NSError
            // Arbitrary domains/descriptions/userInfo can contain private data.
            // Store a code only when both domain and numeric value are known.
            if allowedError(domain: e.domain, code: e.code) { observation["errorDomain"] = e.domain; observation["errorCode"] = e.code }
        }
        if let bluetoothState, (0...5).contains(bluetoothState) { observation["bluetoothState"] = bluetoothState }
        // Independent fixed event keys bound storage. Counts can lose updates
        // between processes and therefore are observations, never receipts.
        defaults.set(observation, forKey: key)
        if event == .securityRequested { PairedCapabilityProbe.emit(.securityDefaultsWriteReturned) }
    }
    static func formatSecurityObservation(since: Double, now: Double, events: [Event: Double]) -> String {
        guard since.isFinite, now.isFinite, since >= 0, now >= since else {
            return "no current security event recorded"
        }
        let recent = events.filter { event, timestamp in
            event.rawValue.hasPrefix("security") && event != .securityFailure &&
                timestamp.isFinite && timestamp >= since && timestamp <= now
        }.sorted { a, b in
            a.value == b.value ? a.key.rawValue < b.key.rawValue : a.value > b.value
        }.prefix(4).map { $0.key.rawValue }
        return recent.isEmpty ? "no current security event recorded" : recent.joined(separator: ", ")
    }
    static func currentSecurityObservation(since: Double) -> String {
        guard let defaults = UserDefaults(suiteName: suite) else { return "security observations unavailable" }
        var events: [Event: Double] = [:]
        for event in Event.allCases where event.rawValue.hasPrefix("security") {
            if let timestamp = defaults.dictionary(forKey: prefix + event.rawValue)?["timestamp"] as? Double {
                events[event] = timestamp
            }
        }
        return formatSecurityObservation(since: since, now: Date().timeIntervalSince1970, events: events)
    }
    static func summary() -> String {
        guard let defaults = UserDefaults(suiteName: suite) else { return "Diagnostic observations unavailable." }
        let observations = Event.allCases.compactMap { event -> (String, Double)? in
            guard let record = defaults.dictionary(forKey: prefix + event.rawValue),
                  let count = record["count"] as? Int, (1...999999).contains(count),
                  let timestamp = record["timestamp"] as? Double, timestamp.isFinite, timestamp >= 0 else { return nil }
            let age = max(0, Date().timeIntervalSince1970 - timestamp)
            var text = "\(event.rawValue): \(count), \(Int(min(age, 999999)))s ago"
            if let domain = record["errorDomain"] as? String, let code = record["errorCode"] as? Int {
                // Revalidate shared diagnostics before rendering any string.
                if allowedError(domain: domain, code: code) { text += " (\(domain), \(code))" }
            }
            if let value = record["bluetoothState"] as? Int, (0...5).contains(value) {
                text += " [" + ["unknown", "resetting", "unsupported", "unauthorized", "poweredOff", "poweredOn"][value] + "]"
            }
            return (text, timestamp)
        }.sorted { $0.1 > $1.1 }.map { $0.0 }
        return "Diagnostic observations; delivery is not verified.\n" +
            (observations.isEmpty ? "No recorded pipeline events." : observations.joined(separator: "\n")) +
            "\nProvider/security/transport events are shown when recorded. No recorded event is not proof that Apple never invoked the extension."
    }
}

// Observes the genuine Apple API result independently of extension captures.
// Pending never grants permission; error/denial is surfaced before mailbox waits.
enum PairedForwardingPermissionState {
    case pending, allowed, denied, failed(any Error)
    var allowsPublication: Bool { if case .allowed = self { return true }; return false }
    func requireNoFailure() throws {
        switch self {
        case .pending, .allowed: return
        case .denied: throw PairedOwnerError.unavailable
        case .failed(let error): throw error
        }
    }
}

import Foundation
import Darwin
import OSLog
import notify

// Diagnostic observations ONLY. None are authentication, capture, readiness,
// effect, delivery or retry receipts. Names contain no accessory/session data.
public enum PairedCapabilityProbe {
    public enum Event: String {
        case securityInitialized, securityAcceptEntered, securityPublicKeySubmitted
        case securityMaterialCallbackEntered, securityDefaultsWriteReturned
        case providerInitialized, providerActivated
        case privateFixtureCreated, privateFixtureReadPassed, privatePriorFixtureReadPassed
        case privateFixtureUnavailable, sharedReadOnlyLockPassed, sharedReadOnlyLockUnavailable
    }
    private static let log = Logger(subsystem: NotificationBridgeConfiguration.namespace, category: "paired-capability-probe")
    private static let fixture = Data("PebbleD provider private persistence qualification v1\n".utf8)
    public static func emit(_ event: Event) {
        log.notice("diagnostic stage: \(event.rawValue, privacy: .public); secure exchange not established")
        // Existing public Darwin diagnostics permit a bounded remote observer.
        // This carries a fixed stage name only; no state/identities/authority.
        _ = notify_post(NotificationBridgeConfiguration.notificationName("probe.v1.") + event.rawValue)
    }
    static func readonlyLock(directory: URL) -> Bool {
        let parent = open(directory.path, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC)
        guard parent >= 0 else { return false }
        defer { close(parent) }
        var directoryInfo = stat()
        guard fstat(parent, &directoryInfo) == 0, directoryInfo.st_uid == geteuid(),
              (directoryInfo.st_mode & 0o777) == 0o700 else { return false }
        let fd = openat(parent, "publisher.lock", O_RDONLY | O_NOFOLLOW | O_CLOEXEC)
        guard fd >= 0 else { return false }
        defer { close(fd) }
        var info = stat()
        guard fstat(fd, &info) == 0, (info.st_mode & S_IFMT) == S_IFREG,
              info.st_uid == geteuid(), (info.st_mode & 0o777) == 0o600,
              flock(fd, LOCK_SH | LOCK_NB) == 0 else { return false }
        return true // close releases the lease; no create/write/repair.
    }
    static func privateFixture(directory: URL) -> (created: Bool, priorRead: Bool, read: Bool) {
        let parent = open(directory.path, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC)
        guard parent >= 0 else { return (false, false, false) }
        defer { close(parent) }
        var directoryInfo = stat()
        guard fstat(parent, &directoryInfo) == 0, directoryInfo.st_uid == geteuid(),
              (directoryInfo.st_mode & 0o777) == 0o700 else { return (false, false, false) }
        func readFixture() -> Bool {
            let fd = openat(parent, "qualification.bin", O_RDONLY | O_NOFOLLOW | O_CLOEXEC)
            guard fd >= 0 else { return false }
            defer { close(fd) }
            var info = stat()
            guard fstat(fd, &info) == 0, (info.st_mode & S_IFMT) == S_IFREG,
                  info.st_uid == geteuid(), (info.st_mode & 0o777) == 0o600,
                  info.st_size == fixture.count else { return false }
            var bytes = [UInt8](repeating: 0, count: fixture.count)
            let n = bytes.withUnsafeMutableBytes { pread(fd, $0.baseAddress, $0.count, 0) }
            return n == fixture.count && Data(bytes) == fixture
        }
        let existing = openat(parent, "qualification.bin", O_RDONLY | O_NOFOLLOW | O_CLOEXEC)
        if existing >= 0 { close(existing); let valid = readFixture(); return (false, valid, valid) }
        guard errno == ENOENT else { return (false, false, false) }
        // Fixed harmless bytes in the extension's OWN container. Existing or
        // corrupt fixtures fail closed; never replace arbitrary files/keys.
        let fd = openat(parent, "qualification.bin", O_RDWR | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0o600)
        guard fd >= 0 else { return (false, false, false) }
        guard flock(fd, LOCK_EX | LOCK_NB) == 0 else { close(fd); return (true, false, false) }
        let n = fixture.withUnsafeBytes { pwrite(fd, $0.baseAddress, $0.count, 0) }
        let synced = n == fixture.count && ftruncate(fd, off_t(fixture.count)) == 0 && fsync(fd) == 0
        close(fd)
        guard synced, fsync(parent) == 0 else { return (true, false, false) }
        return (true, false, readFixture())
    }
    static func prepareOwnDirectory(own: URL, group: URL?, home: URL = URL(fileURLWithPath: NSHomeDirectory(), isDirectory: true)) throws -> URL {
        let home = home.standardizedFileURL
        let root = own.standardizedFileURL
        guard root.path.hasPrefix(home.path + "/"),
              group.map({ root.path == $0.standardizedFileURL.path ||
                  root.path.hasPrefix($0.standardizedFileURL.path + "/") }) != true else {
            throw CocoaError(.fileWriteNoPermission)
        }
        let components = String(root.path.dropFirst(home.path.count + 1)).split(separator: "/").map(String.init)
            + ["pebbled-capability-qualification"]
        var parent = open(home.path, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC)
        guard parent >= 0 else { throw CocoaError(.fileWriteNoPermission) }
        defer { close(parent) }
        for component in components {
            var info = stat()
            guard fstat(parent, &info) == 0, info.st_uid == geteuid(),
                  (info.st_mode & 0o022) == 0 else { throw CocoaError(.fileWriteNoPermission) }
            if mkdirat(parent, component, 0o700) != 0 && errno != EEXIST { throw CocoaError(.fileWriteNoPermission) }
            let child = openat(parent, component, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC)
            guard child >= 0 else { throw CocoaError(.fileWriteNoPermission) }
            close(parent); parent = child
        }
        var info = stat()
        guard fstat(parent, &info) == 0, info.st_uid == geteuid(), (info.st_mode & 0o777) == 0o700 else {
            throw CocoaError(.fileWriteNoPermission)
        }
        // Harmless constant fixture only. No path-based protection/repair of
        // existing objects. Secret-bearing persistence is a separate gate.
        return root.appendingPathComponent("pebbled-capability-qualification", isDirectory: true)
    }
    public static func providerQualification(enabled: Bool) {
        guard enabled else { return }
        let manager = FileManager.default
        guard let own = manager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
            emit(.privateFixtureUnavailable); emit(.sharedReadOnlyLockUnavailable); return
        }
        let group = manager.containerURL(forSecurityApplicationGroupIdentifier: NotificationBridgeConfiguration.appGroup)
        do {
            let directory = try prepareOwnDirectory(own: own, group: group)
            let result = privateFixture(directory: directory)
            if result.created { emit(.privateFixtureCreated) }
            if result.priorRead { emit(.privatePriorFixtureReadPassed) }
            emit(result.read ? .privateFixtureReadPassed : .privateFixtureUnavailable)
        } catch { emit(.privateFixtureUnavailable) }
        emit(group.map { readonlyLock(directory: $0.appendingPathComponent("paired-notifications", isDirectory: true)) } == true
             ? .sharedReadOnlyLockPassed : .sharedReadOnlyLockUnavailable)
    }
    public static func mainObservation(protectedDataAvailable: Bool) -> String {
        let available = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier:
            NotificationBridgeConfiguration.appGroup) != nil
        return "Main-only shared-container lookup: " + (available ? "available" : "unavailable") +
            "; protected data: " + (protectedDataAvailable ? "available" : "unavailable") +
            ". This does not establish extension access or secure exchange."
    }
}
