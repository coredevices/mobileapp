
struct AccessoryNotification: Sendable {
    struct Body: Sendable { let string: String }
    struct Identifier: Codable, Sendable {
        let sourceIdentifier: String
        let notificationIdentifier: String
        init(sourceIdentifier: String, notificationIdentifier: String) {
            self.sourceIdentifier = sourceIdentifier; self.notificationIdentifier = notificationIdentifier
        }
        init(notificationIdentifier: String, sourceIdentifier: String) {
            self.sourceIdentifier = sourceIdentifier; self.notificationIdentifier = notificationIdentifier
        }
    }
    struct Action: Sendable {
        enum Kind: Sendable { case plain, textInput }
        let identifier: String
        let title: String?
        let type: Kind
    }
    let identifier: Identifier
    let title: String?
    let subtitle: String?
    let body: Body?
    let sourceName: String?
    let actions: [Action]
    var deliveryDate: Date = .now
}

struct AlertingContext: Sendable { let shouldAlert: Bool; var notificationCanAlert = true; var isSuppressedByFocus = false }
struct TransportMessage: Sendable { let sessionID: UUID; let data: Data }
struct NotificationResponse: Sendable {
    let sourceIdentifier: String
    let notificationIdentifier: String
    let actionIdentifier: String
    let userText: String?
}
struct AccessoryMessage: Sendable {
    struct Payload: Sendable { enum Transport: Sendable { case bluetooth }; let transport: Transport; let data: Data }
    enum Error: Swift.Error { case transportUnavailable }
    let payload: Payload
    init(_ build: () -> Payload) { payload = build() }
}
enum NotificationsForwarding {
    protocol AccessoryNotificationsHandler: AnyObject {
        func didActivate(for session: Session)
        func didInvalidate()
        func addNotification(_ notification: AccessoryNotification, alertingContext: AlertingContext) async throws -> Bool
        func updateNotification(_ notification: AccessoryNotification)
        func removeNotification(identifier: AccessoryNotification.Identifier)
        func removeAllNotifications()
        func messageHandler(_ message: TransportMessage)
    }
    class Session: @unchecked Sendable {
        let sessionID: UUID
        init(_ id: UUID = UUID()) { sessionID = id }
        func sendResponse(_ response: NotificationResponse) async throws {}
        func removeNotifications(identifiers: [AccessoryNotification.Identifier]) async throws {}
        func send(message: AccessoryMessage) async throws {}
    }
}
final class MockSession: NotificationsForwarding.Session, @unchecked Sendable {
    struct Snapshot {
        var autoReceipt = true
        var receiptSink: (@Sendable (TransportMessage) -> Void)?
        var responses: [NotificationResponse] = []
        var removals: [[AccessoryNotification.Identifier]] = []
        var policies: [Data] = []
        var failPolicy = false
        var pausePolicy = false
        var policyWaiter: CheckedContinuation<Void, Never>?
        var sent: [Data] = []
        var fail = false
        var pauseResponse = false
        var responseWaiter: CheckedContinuation<Void, Never>?
        var pauseSend = false
        var sendWaiter: CheckedContinuation<Void, Never>?
    }
    let state = Mutex(Snapshot())
    override func sendResponse(_ response: NotificationResponse) async throws {
        let (fail, pause) = state.withLock { state in
            state.responses.append(response)
            return (state.fail, state.pauseResponse)
        }
        if pause {
            await withCheckedContinuation { waiter in
                state.withLock { $0.responseWaiter = waiter }
            }
        }
        if fail { throw TestError.forced }
    }
    override func removeNotifications(identifiers: [AccessoryNotification.Identifier]) async throws {
        let (fail, pause) = state.withLock { state in
            state.removals.append(identifiers)
            return (state.fail, state.pauseResponse)
        }
        if pause {
            await withCheckedContinuation { waiter in state.withLock { $0.responseWaiter = waiter } }
        }
        if fail { throw TestError.forced }
    }
    override func send(message: AccessoryMessage) async throws {
        let isPolicy = message.payload.data.first == 5
        let (pause, pausePolicy, failPolicy) = state.withLock { state in
            if isPolicy { state.policies.append(message.payload.data) }
            else { state.sent.append(message.payload.data) }
            return (state.pauseSend, isPolicy && state.pausePolicy, isPolicy && state.failPolicy)
        }
        if failPolicy { throw TestError.forced }
        if pausePolicy {
            await withCheckedContinuation { waiter in state.withLock { $0.policyWaiter = waiter } }
        }
        if pause {
            await withCheckedContinuation { waiter in
                state.withLock { $0.sendWaiter = waiter }
            }
        }
        let receiver = state.withLock { ($0.autoReceipt, $0.receiptSink) }
        let frame = Array(message.payload.data)
        if receiver.0, let event = attribute(frame, 14) {
            receiver.1?(.init(sessionID: sessionID, data: Data(Array("NDA1".utf8) + event + (attribute(frame, 6) ?? [0]))))
        }
    }
        // Deterministic watch fixture: send completion alone is not an alert receipt.
    var snapshot: Snapshot { state.withLock { $0 } }
    func resumeResponse() {
        let waiter = state.withLock { state -> CheckedContinuation<Void, Never>? in
            let waiter = state.responseWaiter
            state.responseWaiter = nil
            state.pauseResponse = false
            return waiter
        }
        waiter?.resume()
    }
    func resumeSend() {
        let waiter = state.withLock { state -> CheckedContinuation<Void, Never>? in
            let waiter = state.sendWaiter
            state.sendWaiter = nil
            state.pauseSend = false
            return waiter
        }
        waiter?.resume()
    }
}
enum TestError: Swift.Error { case forced }
enum ProbeEvent { case providerInitialized, providerActivated }
enum PairedCapabilityProbe { static func emit(_ event: ProbeEvent) {} }
enum DiagnosticEvent {
    case providerActivated, providerInvalidated, providerResponseReceived, providerResponseSessionRejected
    case providerResponseDecoded, providerResponseMappingRejected, providerResponseSubmitted
    case providerResponseAccepted, providerResponseFailed, notificationDispatchFailure
    case notificationDispatch, notificationDispatchAccepted
    case notificationAdded, notificationUpdated, notificationSilent, notificationHistorical
    case notificationIneligible, notificationFocusSuppressed
    case notificationAlerted, notificationNotAlerted, notificationReceiptTimedOut
}
enum PairedDiagnosticStatus {
    static func record(_ event: DiagnosticEvent, error: Swift.Error? = nil) {}
}
struct MockLog { func error(_ message: String) {} }
let forwardingLog = MockLog()
enum PairedNotificationGate { static let enabled = true }
enum PairedOwnerError: Swift.Error { case stale }
protocol ReplySuggesting: Sendable { func replies(for notification: ForwardedNotification) async -> [String] }
struct StoredReplies: ReplySuggesting { func replies(for notification: ForwardedNotification) async -> [String] { [] } }
final class ReplySources: Sendable {
    private let entries = Mutex([String: AccessoryNotification.Identifier]())
    func notification(onTheWatchAs key: String) -> AccessoryNotification.Identifier? { entries.withLock { $0[key] } }
    func remember(_ identifier: AccessoryNotification.Identifier, token: String? = nil) {
        entries.withLock { $0[token ?? identifier.notificationIdentifier] = identifier }
    }
    func forget(_ identifier: AccessoryNotification.Identifier, token: String? = nil) {
        _ = entries.withLock { $0.removeValue(forKey: token ?? identifier.notificationIdentifier) }
    }
    func forgetAll() { entries.withLock { $0.removeAll() } }
}

enum NotificationDismissalSettings {
    struct State { var mode = false; var next: Int32 = 0; var observers: [Int32: @Sendable () -> Void] = [:] }
    static let state = Mutex(State())
    static var onBothDevices: Bool {
        get { state.withLock { $0.mode } }
        set {
            let callbacks = state.withLock { state in state.mode = newValue; return Array(state.observers.values) }
            for callback in callbacks { callback() }
        }
    }
    static func observe(_ callback: @escaping @Sendable () -> Void) -> Int32? {
        state.withLock { state in state.next += 1; state.observers[state.next] = callback; return state.next }
    }
    static func stopObserving(_ token: Int32) { _ = state.withLock { $0.observers.removeValue(forKey: token) } }
}

func activate(_ handler: NotificationForwardingHandler, _ session: MockSession) {
    session.state.withLock { $0.receiptSink = { [weak handler] message in handler?.messageHandler(message) } }
    handler.didActivate(for: session)
}
