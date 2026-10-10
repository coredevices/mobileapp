import Foundation
import CryptoKit

actor PairedPersistedReplyDelivery: PairedReplyDelivering {
    private let watch: P256.Signing.PublicKey
    private let owner: P256.Signing.PrivateKey
    private let nonce: Data
    private let isCurrent: @Sendable () -> Bool
    private let requireReadiness: @Sendable () throws -> Void
    private let invalidateReadiness: @Sendable () throws -> Void
    private let ledger: PairedReceiptLedger
    private var pending: PairedOwnerEnvelope?
    private var valid = true
    private var busy = false
    init(watch: P256.Signing.PublicKey, owner: P256.Signing.PrivateKey, nonce: Data,
         feature: Data, ledger: PairedReceiptLedger, isCurrent: @escaping @Sendable () -> Bool,
         requireReadiness: @escaping @Sendable () throws -> Void,
         invalidateReadiness: @escaping @Sendable () throws -> Void) throws {
        try ledger.requireBinding(watch: watch.x963Representation,
            owner: owner.publicKey.x963Representation, nonce: nonce, feature: feature)
        self.watch = watch; self.owner = owner; self.nonce = nonce; self.ledger = ledger; self.isCurrent = isCurrent
        self.requireReadiness = requireReadiness; self.invalidateReadiness = invalidateReadiness
    }
    func deliver(_ wire: Data, sendResponse: @Sendable (Data) async throws -> Void,
                 sendACK: @Sendable (Data) async throws -> Void) async throws {
        try Task.checkCancellation()
        guard valid, isCurrent() else { throw PairedOwnerError.unavailable }
        let effectLease = try ledger.acquireEffectLease()
        defer { effectLease.release() }
        try requireReadiness()
        guard !busy else { throw PairedOwnerError.pending }
        let reply = try PairedOwnerEnvelope.verified(wire, using: watch)
        guard reply.domain == .receipt, reply.kind == .reply, reply.direction == 1,
              reply.nonce == nonce else { throw PairedOwnerError.invalid }
        let claim = try ledger.begin(reply)
        pending = reply
        try await run(claim.entry, claimed: claim.claimed, sendResponse: sendResponse, sendACK: sendACK)
    }
    func retry(sendResponse: @Sendable (Data) async throws -> Void,
               sendACK: @Sendable (Data) async throws -> Void) async throws {
        try Task.checkCancellation()
        guard valid, isCurrent() else { throw PairedOwnerError.unavailable }
        let effectLease = try ledger.acquireEffectLease()
        defer { effectLease.release() }
        try requireReadiness()
        guard !busy, let entry = try ledger.pending() else { throw PairedOwnerError.pending }
        try await run(entry, claimed: false, sendResponse: sendResponse, sendACK: sendACK)
    }
    private func run(_ entry: PairedReceiptLedger.Entry, claimed: Bool,
                     sendResponse: @Sendable (Data) async throws -> Void,
                     sendACK: @Sendable (Data) async throws -> Void) async throws {
        busy = true
        defer { busy = false }
        try requireReadiness()
        var callResponse = claimed
        if entry.phase == .invoking && !claimed { throw PairedOwnerError.ambiguousResponse }
        if entry.phase == .notSubmitted {
            guard let pending, pending.request == entry.request,
                  Data(SHA256.hash(data: pending.payload)) == entry.digest else { throw PairedOwnerError.pending }
            try ledger.resumeNotSubmitted(entry.request)
            callResponse = true
        }
        if callResponse {
            guard let pending, pending.request == entry.request else { throw PairedOwnerError.invalid }
            do { try Task.checkCancellation(); try requireReadiness(); try ledger.checkActive() }
            catch { try ledger.notSubmitted(entry.request); throw error }
            do { try await sendResponse(pending.payload) }
            catch {
                if error is PairedResponseFailure { try ledger.notSubmitted(entry.request) }
                // Any other error leaves durable invocation intent ambiguous.
                throw error
            }
            guard valid, isCurrent() else { throw PairedOwnerError.unavailable }
            try requireReadiness()
            try ledger.responseAccepted(entry.request)
        }
        try Task.checkCancellation()
        guard valid, isCurrent() else { throw PairedOwnerError.unavailable }
        try requireReadiness()
        let ack = try ledger.prepareACK(entry.request) { sequence in
            try PairedOwnerEnvelope(domain: .receipt, kind: .ack, direction: 0, nonce: nonce,
                sequence: sequence, request: entry.request, payload: Data()).signed(using: owner)
        }
        try requireReadiness()
        try await sendACK(ack)
        guard valid, isCurrent() else { throw PairedOwnerError.unavailable }
        try requireReadiness()
        try ledger.completed(entry.request)
        pending = nil
    }
    func invalidate() {
        valid = false; pending = nil
        try? invalidateReadiness(); try? ledger.invalidate()
    }
}
