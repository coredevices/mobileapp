import Foundation
import CryptoKit

// Opaque Apple HPKE ciphertext is unchanged. This layer alone consumes the
// outer-session counters shared by security/transport extensions. Provider
// receipts are a separate authenticated inner protocol, not another outer
// counter allocation; that firmware/receipt broker is still activation-held.
actor PairedAuthenticatedTransport {
    private let watch: P256.Signing.PublicKey
    private let owner: P256.Signing.PrivateKey
    private let nonce: Data
    private let feature: Data
    private let isCurrent: @Sendable () -> Bool
    private let accessory: Data?
    private let requireReadiness: @Sendable () throws -> Void
    private let invalidateReadiness: @Sendable () throws -> Void
    private var valid = true
    private let ledger: PairedSessionLedger

    init(watch: P256.Signing.PublicKey, owner: P256.Signing.PrivateKey,
         nonce: Data, feature: Data, ledger: PairedSessionLedger, accessory: Data?, isCurrent: @escaping @Sendable () -> Bool,
         requireReadiness: @escaping @Sendable () throws -> Void,
         invalidateReadiness: @escaping @Sendable () throws -> Void) throws {
        guard nonce.count == 32, !feature.isEmpty, feature.count <= 64 else { throw PairedOwnerError.invalid }
        try ledger.requireBinding(watch: watch.x963Representation,
            owner: owner.publicKey.x963Representation, nonce: nonce, feature: feature)
        self.watch = watch; self.owner = owner; self.nonce = nonce
        self.feature = feature; self.ledger = ledger; self.accessory = accessory; self.isCurrent = isCurrent
        self.requireReadiness = requireReadiness; self.invalidateReadiness = invalidateReadiness
    }

    func pinnedWatchPublicKey() throws -> Data {
        guard valid, isCurrent() else { throw PairedOwnerError.stale }
        try ledger.checkActive()
        return watch.x963Representation.dropFirst()
    }

    func session(encapsulated: Data, accessory: Data) throws -> Data {
        guard valid, isCurrent() else { throw PairedOwnerError.stale }
        try ledger.checkActive()
        guard self.accessory == nil || self.accessory == accessory else { throw PairedOwnerError.invalid }
        let payload = try PairedOwnerEnvelope.sessionPayload(watch: watch.x963Representation,
            owner: owner.publicKey.x963Representation, encapsulated: encapsulated,
            feature: feature, accessory: accessory)
        return try PairedOwnerEnvelope(kind: .session, direction: 0, nonce: nonce,
            sequence: 0, request: Data(repeating: 0, count: 16), payload: payload).signed(using: owner)
    }

    func sealCiphertext(_ ciphertext: Data, feature: Data, request: Data) throws -> Data {
        guard valid, isCurrent() else { throw PairedOwnerError.stale }
        try requireReadiness()
        guard feature == self.feature, !ciphertext.isEmpty, ciphertext.count <= 2304,
              request.count == 16 else { throw PairedOwnerError.invalid }
        let sequence = try ledger.nextOutbound()
        return try PairedOwnerEnvelope(kind: .data, direction: 0, nonce: nonce,
            sequence: sequence, request: request, payload: ciphertext).signed(using: owner)
    }

    func openCiphertext(_ wire: Data, feature: Data) throws -> Data {
        guard valid, isCurrent() else { throw PairedOwnerError.stale }
        try requireReadiness()
        guard feature == self.feature else { throw PairedOwnerError.invalid }
        let envelope = try PairedOwnerEnvelope.verified(wire, using: watch)
        guard envelope.domain == .outer, envelope.kind == .data, envelope.direction == 1, envelope.nonce == nonce,
              !envelope.payload.isEmpty else { throw PairedOwnerError.invalid }
        try ledger.acceptInbound(envelope.sequence)
        return envelope.payload
    }

    func invalidate() throws {
        valid = false
        // Always attempt both; a failed marker deletion cannot keep this actor live.
        defer { try? invalidateReadiness() }
        try ledger.invalidate()
    }
}
