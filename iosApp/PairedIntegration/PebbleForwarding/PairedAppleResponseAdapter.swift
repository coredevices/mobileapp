#if os(iOS)
import AccessoryNotifications
import AccessoryTransportExtension
import Foundation

// The provider owns a single immutable Apple session and authenticated delivery
// actor. No keys are created here. The containing app's approved enrollment and
// cross-extension session broker must supply the actor before activation.
actor PairedAppleResponseAdapter {
    private let session: any NotificationsForwarding.AccessoryNotificationsSession
    private let delivery: any PairedReplyDelivering
    private let isCurrent: @Sendable () -> Bool
    private let resolve: @Sendable (Data) throws -> NotificationResponse
    private var valid = true

    init(session: any NotificationsForwarding.AccessoryNotificationsSession,
         delivery: any PairedReplyDelivering,
         isCurrent: @escaping @Sendable () -> Bool,
         resolve: @escaping @Sendable (Data) throws -> NotificationResponse) {
        self.session = session
        self.delivery = delivery
        self.isCurrent = isCurrent
        self.resolve = resolve
    }

    // POT1 is verified by delivery before the resolver sees any reply bytes.
    // The resolver must check the current notification/action mapping. Never
    // accept a donor's unsigned reply or a transport's claimed verification.
    func deliver(_ wire: Data) async throws {
        guard valid, isCurrent() else { throw PairedOwnerError.unavailable }
        try await delivery.deliver(wire, sendResponse: { [self] bytes in
            try await respond(bytes)
        }, sendACK: { [self] ack in
            try await acknowledge(ack)
        })
    }

    func retry() async throws {
        guard valid, isCurrent() else { throw PairedOwnerError.unavailable }
        try await delivery.retry(sendResponse: { [self] bytes in
            try await respond(bytes)
        }, sendACK: { [self] ack in
            try await acknowledge(ack)
        })
    }

    private func respond(_ bytes: Data) async throws {
        try Task.checkCancellation()
        guard valid, isCurrent() else { throw PairedOwnerError.unavailable }
        let response: NotificationResponse
        do { response = try resolve(bytes) }
        catch { throw PairedResponseFailure.notSubmitted }
        // An error from Apple is deliberately not converted to notSubmitted.
        try await session.sendResponse(response)
    }

    private func acknowledge(_ ack: Data) async throws {
        try Task.checkCancellation()
        guard valid, isCurrent() else { throw PairedOwnerError.unavailable }
        try await session.send(message: AccessoryMessage {
            AccessoryMessage.Payload(transport: .bluetooth, data: ack)
        })
    }

    func invalidate() async {
        valid = false
        await delivery.invalidate()
    }
}
#endif
