#if os(iOS)
public import AccessoryNotifications
public import AccessoryTransportExtension
import CryptoKit
import Foundation
import OSLog
import PebbleProtocol
import Synchronization

/// Apple owns notification encryption and the provider session. This handler
/// keeps ordering and authenticated command deduplication. SDK identities travel
/// with each watch action, following NotifBridge; see NOTIFBRIDGE-NOTICE.md.
public final class NotificationForwardingHandler: NotificationsForwarding.AccessoryNotificationsHandler {
    private struct Outgoing: Sendable {
        let message: AccessoryNotificationMessage
        let delivered: CheckedContinuation<Bool, Never>?
    }

    private struct State {
        var session: NotificationsForwarding.Session?
        var receiptBoundSessionID: UUID?
        var namespace: Data?
        var actions: [String: [String: Bool]] = [:]
        var notificationIDs: [String: AccessoryNotification.Identifier] = [:]
        var dismissalActions: [String: String] = [:]
        var retiredDismissalActions: [String: Set<String>] = [:]
        var receipts: [Data: CheckedContinuation<Bool, Never>] = [:]
        var replyPayloads: [Data: Data] = [:]
        var replyResults: [Data: Bool] = [:]
        var executingReplies: Set<Data> = []
        var queue: PairedFIFO<Outgoing> = []
        var replyQueue: PairedFIFO<Outgoing> = []
        var wake: AsyncStream<Void>.Continuation?
        var generation = 0
        var dismissalObserver: Int32?
    }

    private let state = Mutex(State())
    private let replySuggester: any ReplySuggesting
    private let receiptTimeout: Duration
    private static let identityNamespace = Data("PebbleD.SDKNotificationIdentity.v1".utf8)

    public convenience init() { self.init(replySuggester: StoredReplies()) }

    init(replySuggester: any ReplySuggesting, receiptTimeout: Duration = .seconds(60)) {
        self.replySuggester = replySuggester
        self.receiptTimeout = receiptTimeout
        PairedCapabilityProbe.emit(.providerInitialized)
    }

    deinit {
        let abandoned = state.withLock { (Array($0.receipts.values), $0.wake, $0.dismissalObserver) }
        if let token = abandoned.2 { NotificationDismissalSettings.stopObserving(token) }
        abandoned.1?.finish()
        for receipt in abandoned.0 { receipt.resume(returning: false) }
    }

    public func didActivate(for session: NotificationsForwarding.Session) {
        PairedCapabilityProbe.emit(.providerActivated)
        PairedDiagnosticStatus.record(.providerActivated)
        let (wakes, wake) = AsyncStream.makeStream(of: Void.self, bufferingPolicy: .bufferingNewest(1))
        let generation: Int? = state.withLock { state in
            guard state.session == nil, state.wake == nil else { return nil }
            state.session = session
            state.receiptBoundSessionID = nil
            state.namespace = Self.identityNamespace
            state.wake = wake
            state.generation += 1
            return state.generation
        }
        guard let generation else { return }
        if let token = NotificationDismissalSettings.observe({ [weak self] in
            // A slow policy transmission must never hold the notification/reply drain.
            Task { [weak self] in
                try? await self?.send(.dismissalPolicy(onBothDevices: NotificationDismissalSettings.onBothDevices),
                                      generation: generation)
            }
        }) {
            let retained = state.withLock { state in
                guard state.generation == generation, state.session === session else { return false }
                state.dismissalObserver = token
                return true
            }
            if !retained { NotificationDismissalSettings.stopObserving(token) }
        }
        Task { [weak self] in
            for await _ in wakes { await self?.drain(generation) }
        }
    }

    public func didInvalidate() {
        PairedDiagnosticStatus.record(.providerInvalidated)
        let (abandoned, wake, receipts, observer) = state.withLock { state in
            let taken = (state.queue, state.wake, Array(state.receipts.values), state.dismissalObserver)
            state.dismissalObserver = nil
            state.session = nil
            state.receiptBoundSessionID = nil
            state.namespace = nil
            state.actions = [:]
            state.notificationIDs = [:]
            state.dismissalActions = [:]
            state.retiredDismissalActions = [:]
            state.receipts = [:]
            state.replyPayloads = [:]
            state.replyResults = [:]
            state.executingReplies = []
            state.queue = []
            state.replyQueue = []
            state.wake = nil
            state.generation += 1
            return taken
        }
        if let observer { NotificationDismissalSettings.stopObserving(observer) }
        for receipt in receipts { receipt.resume(returning: false) }
        wake?.finish()
        for outgoing in abandoned { outgoing.delivered?.resume(returning: false) }
    }

    public func addNotification(_ notification: AccessoryNotification,
                                alertingContext: AlertingContext) async throws -> Bool {
        PairedDiagnosticStatus.record(.notificationAdded)
        if !alertingContext.notificationCanAlert { PairedDiagnosticStatus.record(.notificationIneligible) }
        if alertingContext.isSuppressedByFocus { PairedDiagnosticStatus.record(.notificationFocusSuppressed) }
        observeDeliveryDate(notification, shouldAlert: alertingContext.shouldAlert)
        guard let remembered = remember(notification) else { return false }
        // Preserve the requested quiet-alert behavior only for notifications that
        // remain eligible and are not actually suppressed by the current Focus.
        let canAlert = alertingContext.notificationCanAlert && !alertingContext.isSuppressedByFocus
        var forwarded = ForwardedNotification(notification, shouldAlert: canAlert)
        forwarded.identifier = remembered.token
        forwarded.dismissalActionIdentifier = remembered.dismissalID
        let event = withUnsafeBytes(of: UUID().uuid) { Data($0) }
        forwarded.deliveryReceiptID = Array(event)
        let generation = remembered.generation
        return await withCheckedContinuation { receipt in
            let registered = state.withLock { state in
                guard state.generation == generation, state.session != nil, state.receipts.count < 128 else { return false }
                state.receipts[event] = receipt
                return true
            }
            guard registered else { receipt.resume(returning: false); return }
            enqueue(Outgoing(message: .present(forwarded), delivered: nil), generation: generation)
            Task { [weak self, receiptTimeout] in
                try? await Task.sleep(for: receiptTimeout)
                if self?.resolveReceipt(event, alerted: false, generation: generation) == true {
                    PairedDiagnosticStatus.record(.notificationReceiptTimedOut)
                }
            }
        }
    }

    public func updateNotification(_ notification: AccessoryNotification) {
        PairedDiagnosticStatus.record(.notificationUpdated)
        observeDeliveryDate(notification, shouldAlert: false)
        guard let remembered = remember(notification) else { return }
        var forwarded = ForwardedNotification(notification, shouldAlert: false)
        forwarded.identifier = remembered.token
        forwarded.dismissalActionIdentifier = remembered.dismissalID
        enqueue(Outgoing(message: .present(forwarded), delivered: nil), generation: remembered.generation)
    }

    public func removeNotification(identifier: AccessoryNotification.Identifier) {
        let captured: (String, Int)? = state.withLock { state in
            guard let namespace = state.namespace, state.session != nil else { return nil }
            let token = PairedNotificationToken.make(namespace: namespace,
                source: identifier.sourceIdentifier, notification: identifier.notificationIdentifier)
            state.actions.removeValue(forKey: token)
            state.notificationIDs.removeValue(forKey: token)
            state.dismissalActions.removeValue(forKey: token)
            state.retiredDismissalActions.removeValue(forKey: token)
            return (token, state.generation)
        }
        guard let (token, generation) = captured else { return }
        enqueue(Outgoing(message: .remove(sourceIdentifier: identifier.sourceIdentifier,
                                           notificationIdentifier: token), delivered: nil), generation: generation)
    }

    public func removeAllNotifications() {
        let generation: Int? = state.withLock { state in
            guard state.session != nil else { return nil }
            state.actions = [:]; state.notificationIDs = [:]
            state.dismissalActions = [:]; state.retiredDismissalActions = [:]
            return state.generation
        }
        guard let generation else { return }
        enqueue(Outgoing(message: .removeAll, delivered: nil), generation: generation)
    }

    public func messageHandler(_ message: TransportMessage) {
        if #available(iOS 27.0, *) {
            handleMessage(message, expectedSessionID: { $0.sessionID })
        } else {
            handleMessage(message, expectedSessionID: { _ in nil })
        }
    }

    // A 26.5 feature session does not expose its ID. Bind the first response
    // only after the watch returns a receipt for this activation's fresh event.
    // The closure is evaluated under the state lock so a session replacement
    // cannot pair an old SDK ID with a new session object.
    func handleMessage(_ message: TransportMessage,
                       expectedSessionID: (NotificationsForwarding.Session) -> UUID?) {
        PairedDiagnosticStatus.record(.providerResponseReceived)
        let bytes = [UInt8](message.data)
        let receipt = bytes.starts(with: Array("NAR1".utf8)) ? Array(bytes.dropFirst(20)) : bytes
        let receiptCandidate = receipt.starts(with: Array("NDA1".utf8))
        let validReceipt = receiptCandidate && receipt.count == 21 && receipt[20] <= 1
        if receiptCandidate && !validReceipt {
            PairedDiagnosticStatus.record(.providerResponseSessionRejected)
            return
        }
        guard PairedNotificationGate.enabled,
              let (session, generation, resolvedReceipt) = state.withLock({ state -> (NotificationsForwarding.Session, Int, CheckedContinuation<Bool, Never>?)? in
                  guard let session = state.session else { return nil }
                  let event = validReceipt ? Data(receipt[4..<20]) : nil
                  if let directID = expectedSessionID(session) {
                      guard directID == message.sessionID else { return nil }
                  } else if let boundID = state.receiptBoundSessionID {
                      guard boundID == message.sessionID else { return nil }
                  } else {
                      guard let event, state.receipts[event] != nil else { return nil }
                      state.receiptBoundSessionID = message.sessionID
                  }
                  // Claim the receipt in the same critical section as the first
                  // 26.5 session binding. Timeout cannot remove it in between.
                  let resolved = event.flatMap { state.receipts.removeValue(forKey: $0) }
                  return (session, state.generation, resolved)
              }) else {
            PairedDiagnosticStatus.record(.providerResponseSessionRejected)
            return
        }
        if validReceipt {
            if let resolvedReceipt {
                resolvedReceipt.resume(returning: receipt[20] == 1)
                PairedDiagnosticStatus.record(receipt[20] == 1 ? .notificationAlerted : .notificationNotAlerted)
            }
            return
        }
        Task { [weak self] in
            guard let self else { return }
            var requestID: Data?
            var reserved = false
            var accepted = false
            do {
                let reply = try AccessoryNotificationCodec.decodeReply([UInt8](message.data))
                PairedDiagnosticStatus.record(.providerResponseDecoded)
                requestID = reply.requestID.map { Data($0) }
                if let requestID {
                    let admission: (Bool, Bool?) = self.state.withLock { state in
                        guard state.generation == generation && state.session === session else { return (false, nil) }
                        let digest = Data(SHA256.hash(data: message.data))
                        if let priorDigest = state.replyPayloads[requestID], priorDigest != digest { return (false, false) }
                        if let prior = state.replyResults[requestID] { return (false, prior) }
                        guard !state.executingReplies.contains(requestID),
                              state.executingReplies.count + state.replyResults.count < 256 else { return (false, nil) }
                        state.replyPayloads[requestID] = digest
                        state.executingReplies.insert(requestID)
                        return (true, nil)
                    }
                    if let prior = admission.1 {
                        self.enqueueReplyResult(requestID, accepted: prior, generation: generation)
                        return
                    }
                    guard admission.0 else { return }
                    reserved = true
                }
                let resolved: (AccessoryNotification.Identifier, Bool)? = self.state.withLock { state in
                    guard state.generation == generation, state.session === session else { return nil }
                    if let source = reply.sourceIdentifier {
                        // The authenticated watch carries the original opaque SDK identity.
                        // Apple validates the requested action; no volatile lookup gates it.
                        if reply.clearsNotification, !NotificationDismissalSettings.onBothDevices { return nil }
                        return (.init(notificationIdentifier: reply.notificationIdentifier, sourceIdentifier: source), reply.clearsNotification)
                    }
                    guard let notification = state.notificationIDs[reply.notificationIdentifier] else { return nil }
                    // An update must never turn an old watch Dismiss into an app-defined action.
                    guard state.retiredDismissalActions[reply.notificationIdentifier]?.contains(reply.actionIdentifier) != true else { return nil }
                    let dismissal = state.dismissalActions[reply.notificationIdentifier] == reply.actionIdentifier
                    if dismissal {
                        guard requestID != nil, reply.text == nil, NotificationDismissalSettings.onBothDevices else { return nil }
                    } else {
                        guard state.actions[reply.notificationIdentifier]?[reply.actionIdentifier].map({ reply.text == nil || $0 }) == true else { return nil }
                    }
                    return (notification, dismissal)
                }
                guard let (notification, dismissal) = resolved else {
                    PairedDiagnosticStatus.record(.providerResponseMappingRejected)
                    throw PairedOwnerError.stale
                }
                PairedDiagnosticStatus.record(.providerResponseSubmitted)
                if dismissal {
                    try await session.removeNotifications(identifiers: [notification])
                } else {
                    try await session.sendResponse(NotificationResponse(
                        sourceIdentifier: notification.sourceIdentifier,
                        notificationIdentifier: notification.notificationIdentifier,
                        actionIdentifier: reply.actionIdentifier, userText: reply.text))
                }
                accepted = true
                PairedDiagnosticStatus.record(.providerResponseAccepted)
            } catch {
                PairedDiagnosticStatus.record(.providerResponseFailed, error: error)
                forwardingLog.error("a watch reply was not accepted by iOS")
            }
            if let requestID, reserved {
                let current = self.state.withLock { state in
                    guard state.generation == generation && state.session === session else { return false }
                    state.executingReplies.remove(requestID)
                    // Cache both outcomes: an API error does not prove the action had no effect.
                    // A duplicate request must never execute the Messages action again.
                    state.replyResults[requestID] = accepted
                    return true
                }
                if current { self.enqueueReplyResult(requestID, accepted: accepted, generation: generation) }
            }
        }
    }

    @discardableResult
    private func resolveReceipt(_ event: Data, alerted: Bool, generation: Int) -> Bool {
        let receipt = state.withLock { state -> CheckedContinuation<Bool, Never>? in
            guard state.generation == generation else { return nil }
            return state.receipts.removeValue(forKey: event)
        }
        receipt?.resume(returning: alerted)
        return receipt != nil
    }

    private func enqueueReplyResult(_ requestID: Data, accepted: Bool, generation: Int) {
        guard requestID.count == 16 else { return }
        let outgoing = Outgoing(message: .replyResult(requestID: Array(requestID), accepted: accepted), delivered: nil)
        state.withLock { state in
            guard state.generation == generation, let wake = state.wake,
                  state.replyQueue.count < 16 else { return }
            state.replyQueue.append(outgoing)
            wake.yield()
        }
    }

    private func observeDeliveryDate(_ notification: AccessoryNotification, shouldAlert: Bool) {
        if !shouldAlert { PairedDiagnosticStatus.record(.notificationSilent) }
        let age = Date().timeIntervalSince(notification.deliveryDate)
        // Historical observation only; no content or identifier and no age-based delivery gate.
        if age.isFinite && age > 300 { PairedDiagnosticStatus.record(.notificationHistorical) }
    }

    private func remember(_ notification: AccessoryNotification) -> (token: String, dismissalID: String, generation: Int)? {
        state.withLock { state in
            guard let namespace = state.namespace, state.session != nil else { return nil }
            let token = PairedNotificationToken.make(namespace: namespace,
                source: notification.identifier.sourceIdentifier, notification: notification.identifier.notificationIdentifier)
            let ids = Set(notification.actions.map(\.identifier))
            var dismissalID = "pebbled-dismiss"
            var suffix = 0
            while ids.contains(dismissalID) || state.retiredDismissalActions[token]?.contains(dismissalID) == true { suffix += 1; dismissalID = "pebbled-dismiss-" + String(suffix) }
            state.notificationIDs[token] = notification.identifier
            if let old = state.dismissalActions[token], old != dismissalID {
                state.retiredDismissalActions[token, default: []].insert(old)
            }
            state.dismissalActions[token] = dismissalID
            state.actions[token] = Dictionary(notification.actions.map { action in
                let collectsText = if case .textInput = action.type { true } else { false }
                return (action.identifier, collectsText)
            }, uniquingKeysWith: { _, _ in false })
            return (token, dismissalID, state.generation)
        }
    }

    private func enqueue(_ outgoing: Outgoing, generation: Int? = nil) {
        let queued = state.withLock { state in
            guard generation == nil || generation == state.generation, let wake = state.wake, state.queue.count < 128 else { return false }
            state.queue.append(outgoing)
            wake.yield()
            return true
        }
        if !queued {
            outgoing.delivered?.resume(returning: false)
            if case .present(let notification) = outgoing.message, let event = notification.deliveryReceiptID {
                let receipt = state.withLock { $0.receipts.removeValue(forKey: Data(event)) }
                receipt?.resume(returning: false)
            }
        }
    }

    private func drain(_ generation: Int) async {
        while let outgoing = state.withLock({ state in
            guard state.generation == generation else { return nil as Outgoing? }
            return state.replyQueue.popFirst() ?? state.queue.popFirst()
        }) {
            let delivered: Bool
            do {
                try await send(await withReplies(outgoing.message), generation: generation)
                delivered = true
            } catch {
                PairedDiagnosticStatus.record(.notificationDispatchFailure, error: error)
                delivered = false
            }
            outgoing.delivered?.resume(returning: delivered)
            if !delivered, case .present(let notification) = outgoing.message,
               let event = notification.deliveryReceiptID {
                resolveReceipt(Data(event), alerted: false, generation: generation)
            }
        }
    }

    private func withReplies(_ message: AccessoryNotificationMessage) async -> AccessoryNotificationMessage {
        guard case .present(var notification) = message,
              notification.actions.contains(where: \.collectsText) else { return message }
        notification.replies = await replySuggester.replies(for: notification)
        return .present(notification)
    }

    private func send(_ message: AccessoryNotificationMessage, generation: Int) async throws {
        guard PairedNotificationGate.enabled,
              let session = state.withLock({ $0.generation == generation ? $0.session : nil }) else {
            throw AccessoryMessage.Error.transportUnavailable
        }
        guard state.withLock({ $0.generation == generation && $0.session === session }) else {
            throw AccessoryMessage.Error.transportUnavailable
        }
        let currentMessage: AccessoryNotificationMessage
        switch message {
        case .present(var notification):
            notification.dismissOnBothDevices = NotificationDismissalSettings.onBothDevices
            currentMessage = .present(notification)
        case .dismissalPolicy:
            currentMessage = .dismissalPolicy(onBothDevices: NotificationDismissalSettings.onBothDevices)
        default: currentMessage = message
        }
        PairedDiagnosticStatus.record(.notificationDispatch)
        try await session.send(message: AccessoryMessage {
            AccessoryMessage.Payload(transport: .bluetooth,
                                     data: Data(AccessoryNotificationCodec.encode(currentMessage)))
        })
        PairedDiagnosticStatus.record(.notificationDispatchAccepted)
    }
}

extension ForwardedNotification {
    init(_ notification: AccessoryNotification, shouldAlert: Bool) {
        let seconds = notification.deliveryDate.timeIntervalSince1970
        let timestamp = seconds.isFinite && seconds > 0 && seconds <= Double(UInt32.max)
            ? UInt32(seconds) : nil
        self.init(identifier: notification.identifier.notificationIdentifier,
                  title: notification.title, subtitle: notification.subtitle,
                  body: notification.body?.string, sourceName: notification.sourceName,
                  sourceIdentifier: notification.identifier.sourceIdentifier,
                  shouldAlert: shouldAlert,
                  actions: notification.actions.map { action in
                      let collectsText = if case .textInput = action.type { true } else { false }
                      return Action(identifier: action.identifier, title: action.title ?? "",
                                    collectsText: collectsText)
                  }, deliveryTimestamp: timestamp,
                  originalNotificationIdentifier: notification.identifier.notificationIdentifier,
                  dismissOnBothDevices: NotificationDismissalSettings.onBothDevices)
    }
}
#endif
