import Foundation
import CryptoKit

enum PairedPublicationError: Error { case consent, expired, binding, unavailable, uncertain, selectedWatchBindingRequired, replyReconciliationRequired }

// Only the main UI calls confirm after an explicit matching-fingerprint action.
// A watch-signed proof of the physically enrolled owner is also mandatory.
// This core neither generates nor writes an identity/key.
@MainActor
final class PairedMainPublisher {
    struct Consent: Sendable {
        let fingerprint: Data
        let sessionID: UUID
        let expiresMillis: UInt64
    }
    private struct Pending {
        let selectedPeripheral: UUID
        let proof: PairedSessionProof
        let consentDeadline: UInt64
        var confirmed = false
    }
    private let watch: P256.Signing.PublicKey
    private let owner: P256.Signing.PrivateKey
    private let directory: URL
    private let store: PairedPublicationStore
    private let clock: @Sendable () -> UInt64
    private let boot: @Sendable () throws -> UInt64
    private var pending: Pending?

    init(watch: P256.Signing.PublicKey, owner: P256.Signing.PrivateKey,
         directory: URL, clock: @escaping @Sendable () -> UInt64,
         boot: @escaping @Sendable () throws -> UInt64) throws {
        self.watch = watch; self.owner = owner; self.directory = directory
        self.clock = clock; self.boot = boot
        store = try PairedPublicationStore(directory: directory)
    }

    // Exact values supplied by Apple's callbacks; never invent an Apple session
    // UUID or substitute a Bluetooth peripheral UUID for its HPKE identifier.
    func begin(challenge: Data, accessory: String, sessionID: UUID, selectedPeripheral: UUID) throws -> Consent {
        guard pending == nil, challenge.count == 32, !accessory.isEmpty,
              accessory.utf8.count <= 64, !accessory.utf8.contains(0) else {
            throw PairedPublicationError.binding
        }
        let now = clock()
        guard now <= UInt64.max - 300000 else { throw PairedPublicationError.expired }
        let proof = PairedSessionProof(nonce: challenge, sessionID: sessionID, accessory: accessory,
            issuedMillis: now, expiresMillis: now + 300000, bootMillis: try boot(), signature: Data())
        pending = Pending(selectedPeripheral: selectedPeripheral, proof: proof, consentDeadline: now + 30000)
        return Consent(fingerprint: Data(SHA256.hash(data: owner.publicKey.x963Representation)),
            sessionID: sessionID, expiresMillis: now + 30000)
    }

    func confirm(displayedFingerprint: Data) throws -> Data {
        guard var current = pending, !current.confirmed else { throw PairedPublicationError.consent }
        guard displayedFingerprint == Data(SHA256.hash(data: owner.publicKey.x963Representation)) else {
            throw PairedPublicationError.consent
        }
        let now = clock()
        guard current.proof.issuedMillis <= now, now < current.consentDeadline,
              try boot() == current.proof.bootMillis else {
            pending = nil; throw PairedPublicationError.expired
        }
        let proof = current.proof
        let request = try PairedProofWire.request(nonce: proof.nonce, sessionID: proof.sessionID,
            accessory: proof.accessory, issuedMillis: proof.issuedMillis,
            expiresMillis: proof.expiresMillis, bootMillis: proof.bootMillis, watch: watch, owner: owner)
        current.confirmed = true; pending = current
        return request
    }

    func publish(watchProof: Data) throws -> UUID {
        guard let current = pending, current.confirmed else { throw PairedPublicationError.consent }
        let proof = try PairedProofWire.decode(watchProof), expected = current.proof
        guard proof.nonce == expected.nonce, proof.sessionID == expected.sessionID,
              proof.accessory == expected.accessory, proof.issuedMillis == expected.issuedMillis,
              proof.expiresMillis == expected.expiresMillis, proof.bootMillis == expected.bootMillis else {
            throw PairedPublicationError.binding
        }
        let now = clock(), currentBoot = try boot()
        try PairedSessionFactory.validate(proof: proof, watch: watch, owner: owner,
            nowMillis: now, bootMillis: currentBoot)
        // Consent is consumed before any durable state. A failed/uncertain commit
        // must never automatically retry or replace counters/identity.
        pending = nil
        let publication = try PairedSessionPublication.signed(proof: proof,
            selectedPeripheral: current.selectedPeripheral, watch: watch, owner: owner)
        try store.publish(publication, watch: watch, owner: owner) {
            try PairedSessionFactory.prepareLedgers(proof: proof, watch: watch, owner: owner,
                directory: directory, nowMillis: clock(), bootMillis: try boot())
        }
        return proof.sessionID
    }

    func cancel() { pending = nil }
}

extension PairedPublicationError: LocalizedError {
    var errorDescription: String? {
        switch self {
        case .replyReconciliationRequired:
            return "A previous reply is still pending or its Apple result is uncertain. Secure session replacement is blocked; existing reply evidence and counters were preserved. Do not repeat the action to resolve uncertainty."
        case .selectedWatchBindingRequired:
            return "This saved session has no selected-watch binding. Open Secure notifications and explicitly resume or renew setup with the already authorized watch. Existing identities and data are preserved."
        default: return nil
        }
    }
}
