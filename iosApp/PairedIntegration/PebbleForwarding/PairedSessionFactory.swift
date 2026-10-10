import Foundation
import CryptoKit

struct PairedSessionProof: Codable, Sendable {
    let nonce: Data
    let sessionID: UUID
    let accessory: String
    let issuedMillis: UInt64
    let expiresMillis: UInt64
    let bootMillis: UInt64
    let signature: Data
    func transcript(watch: Data, owner: Data) throws -> Data {
        let identifier = Data(accessory.utf8)
        guard nonce.count == 32, watch.count == 65, owner.count == 65,
              !identifier.isEmpty, identifier.count <= 64 else { throw PairedOwnerError.invalid }
        var bytes = Data("PebbleD session factory proof v1".utf8)
        bytes.append(watch); bytes.append(owner); bytes.append(nonce)
        bytes.append(contentsOf: sessionID.uuidString.utf8)
        bytes.append(UInt8(identifier.count)); bytes.append(identifier)
        for value in [issuedMillis, expiresMillis, bootMillis] {
            for shift in stride(from: 56, through: 0, by: -8) { bytes.append(UInt8(truncatingIfNeeded: value >> shift)) }
        }
        return bytes
    }
}
// Phone-local binding, committed atomically with the unchanged watch proof.
// The existing owner signs this mapping; no new identity or watch-wire field.
struct PairedSessionPublication: Codable, Sendable {
    let schemaVersion: Int
    let proof: PairedSessionProof
    let selectedPeripheral: UUID
    let ownerSignature: Data

    private func transcript(watch: Data, owner: Data) throws -> Data {
        guard schemaVersion == 1, proof.signature.count == 64 else { throw PairedOwnerError.invalid }
        var bytes = Data("PebbleD local selected Bluetooth binding v1".utf8)
        bytes.append(try proof.transcript(watch: watch, owner: owner))
        bytes.append(proof.signature)
        bytes.append(contentsOf: selectedPeripheral.uuidString.lowercased().utf8)
        return bytes
    }
    static func signed(proof: PairedSessionProof, selectedPeripheral: UUID,
                       watch: P256.Signing.PublicKey, owner: P256.Signing.PrivateKey) throws -> Self {
        let unsigned = Self(schemaVersion: 1, proof: proof, selectedPeripheral: selectedPeripheral,
                            ownerSignature: Data())
        return Self(schemaVersion: 1, proof: proof, selectedPeripheral: selectedPeripheral,
            ownerSignature: try owner.signature(for: unsigned.transcript(watch: watch.x963Representation,
                owner: owner.publicKey.x963Representation)).rawRepresentation)
    }
    // Structural/signature checks without lease checks also allow explicit renewal
    // of an expired, otherwise authentic old publication. Loading still checks expiry.
    func validateBinding(watch: P256.Signing.PublicKey, owner: P256.Signing.PublicKey) throws {
        let signature = try P256.Signing.ECDSASignature(rawRepresentation: proof.signature)
        guard watch.isValidSignature(signature, for: try proof.transcript(watch: watch.x963Representation,
            owner: owner.x963Representation)) else { throw PairedOwnerError.invalid }
        let localSignature = try P256.Signing.ECDSASignature(rawRepresentation: ownerSignature)
        guard owner.isValidSignature(localSignature, for: try transcript(watch: watch.x963Representation,
            owner: owner.x963Representation)) else { throw PairedOwnerError.invalid }
    }
    static func decodeForLoad(_ bytes: Data, watch: P256.Signing.PublicKey,
                              owner: P256.Signing.PublicKey) throws -> Self {
        if let publication = try? JSONDecoder().decode(Self.self, from: bytes) {
            try publication.validateBinding(watch: watch, owner: owner)
            return publication
        }
        // Bare v1 watch proofs lack a selected Bluetooth UUID. Never guess one.
        if let legacy = try? JSONDecoder().decode(PairedSessionProof.self, from: bytes) {
            let signature = try P256.Signing.ECDSASignature(rawRepresentation: legacy.signature)
            guard watch.isValidSignature(signature, for: try legacy.transcript(watch: watch.x963Representation,
                owner: owner.x963Representation)) else { throw PairedOwnerError.invalid }
            throw PairedPublicationError.selectedWatchBindingRequired
        }
        throw PairedOwnerError.invalid
    }
}

// Immutable validated snapshot. The digest covers the exact protected publication
// bytes, not a re-encoding of equivalent JSON. This is phone-local only.
struct PairedValidatedPublicationContext: Sendable {
    let publicationBytes: Data
    let publication: PairedSessionPublication
    var publicationDigest: Data { Data(SHA256.hash(data: publicationBytes)) }
    var proof: PairedSessionProof { publication.proof }
    var sessionID: UUID { proof.sessionID }
    var selectedPeripheral: UUID { publication.selectedPeripheral }
    private init(bytes: Data, publication: PairedSessionPublication) {
        self.publicationBytes = bytes; self.publication = publication
    }
    static func validated(bytes: Data, watch: P256.Signing.PublicKey,
                          owner: P256.Signing.PrivateKey, nowMillis: UInt64,
                          bootMillis: UInt64) throws -> Self {
        let publication = try PairedSessionPublication.decodeForLoad(bytes, watch: watch, owner: owner.publicKey)
        try PairedSessionFactory.validate(proof: publication.proof, watch: watch, owner: owner,
            nowMillis: nowMillis, bootMillis: bootMillis)
        return Self(bytes: bytes, publication: publication)
    }
}

// Owner-signed private acknowledgement of actual SESSION acceptance. Never an
// authorization substitute for a live connection, nor valid after a GAP drop.
struct PairedSessionReadiness: Codable, Sendable {
    let schemaVersion: Int
    let publicationDigest: Data
    let nonce: Data
    let sessionID: UUID
    let selectedPeripheral: UUID
    let appleMaterialDigest: Data
    let ownerSignature: Data

    static func materialDigest(identifier: Data, encapsulated: Data) throws -> Data {
        guard !identifier.isEmpty, identifier.count <= 64, !identifier.contains(0),
              encapsulated.count == 65 else { throw PairedOwnerError.invalid }
        var bytes = Data("PebbleD acknowledged Apple SESSION material v1".utf8)
        bytes.append(UInt8(identifier.count)); bytes.append(identifier)
        bytes.append(UInt8(encapsulated.count)); bytes.append(encapsulated)
        return Data(SHA256.hash(data: bytes))
    }
    private func transcript() throws -> Data {
        guard schemaVersion == 1, publicationDigest.count == 32, nonce.count == 32,
              appleMaterialDigest.count == 32 else { throw PairedOwnerError.invalid }
        var bytes = Data("PebbleD acknowledged SESSION readiness v1".utf8)
        bytes.append(publicationDigest); bytes.append(nonce)
        bytes.append(contentsOf: sessionID.uuidString.lowercased().utf8)
        bytes.append(contentsOf: selectedPeripheral.uuidString.lowercased().utf8)
        bytes.append(appleMaterialDigest)
        return bytes
    }
    static func signed(context: PairedValidatedPublicationContext, identifier: Data,
                       encapsulated: Data, owner: P256.Signing.PrivateKey) throws -> Self {
        guard identifier == Data(context.proof.accessory.utf8) else { throw PairedOwnerError.invalid }
        let unsigned = Self(schemaVersion: 1, publicationDigest: context.publicationDigest,
            nonce: context.proof.nonce, sessionID: context.sessionID,
            selectedPeripheral: context.selectedPeripheral,
            appleMaterialDigest: try materialDigest(identifier: identifier, encapsulated: encapsulated),
            ownerSignature: Data())
        return Self(schemaVersion: unsigned.schemaVersion, publicationDigest: unsigned.publicationDigest,
            nonce: unsigned.nonce, sessionID: unsigned.sessionID, selectedPeripheral: unsigned.selectedPeripheral,
            appleMaterialDigest: unsigned.appleMaterialDigest,
            ownerSignature: try owner.signature(for: unsigned.transcript()).rawRepresentation)
    }
    func validate(context: PairedValidatedPublicationContext, owner: P256.Signing.PublicKey) throws {
        guard publicationDigest == context.publicationDigest, nonce == context.proof.nonce,
              sessionID == context.sessionID, selectedPeripheral == context.selectedPeripheral else {
            throw PairedOwnerError.stale
        }
        let signature = try P256.Signing.ECDSASignature(rawRepresentation: ownerSignature)
        guard owner.isValidSignature(signature, for: try transcript()) else { throw PairedOwnerError.invalid }
    }
}

struct PairedSessionComponents: Sendable {
    let transport: PairedAuthenticatedTransport
    let replies: PairedPersistedReplyDelivery
    let sessionID: UUID
    let notificationNamespace: Data
    let selectedPeripheral: UUID
    let publicationContext: PairedValidatedPublicationContext
}
enum PairedSessionFactory {
    static func validate(proof: PairedSessionProof, watch: P256.Signing.PublicKey,
                         owner: P256.Signing.PrivateKey, nowMillis: UInt64, bootMillis: UInt64) throws {
        guard proof.issuedMillis <= nowMillis, nowMillis < proof.expiresMillis,
              proof.expiresMillis > proof.issuedMillis,
              proof.expiresMillis - proof.issuedMillis <= 300000,
              proof.bootMillis == bootMillis else { throw PairedOwnerError.stale }
        let signature = try P256.Signing.ECDSASignature(rawRepresentation: proof.signature)
        guard watch.isValidSignature(signature, for: try proof.transcript(watch: watch.x963Representation,
            owner: owner.publicKey.x963Representation)) else { throw PairedOwnerError.invalid }
    }
    static func prepareLedgers(proof: PairedSessionProof, watch: P256.Signing.PublicKey,
                               owner: P256.Signing.PrivateKey, directory: URL,
                               nowMillis: UInt64, bootMillis: UInt64) throws {
        try validate(proof: proof, watch: watch, owner: owner, nowMillis: nowMillis, bootMillis: bootMillis)
        let feature = Data(proof.sessionID.uuidString.utf8)
        _ = try PairedSessionLedger(directory: directory, watch: watch.x963Representation,
            owner: owner.publicKey.x963Representation, nonce: proof.nonce, feature: feature, create: true)
        _ = try PairedReceiptLedger(directory: directory, watch: watch.x963Representation,
            owner: owner.publicKey.x963Representation, nonce: proof.nonce, feature: feature, create: true)
    }
    static func make(proof: PairedSessionProof, selectedPeripheral: UUID, watch: P256.Signing.PublicKey,
                     owner: P256.Signing.PrivateKey, directory: URL,
                     nowMillis: UInt64, bootMillis: UInt64, create: Bool,
                     publicationContext: PairedValidatedPublicationContext,
                     requireReadiness: @escaping @Sendable () throws -> Void,
                     invalidateReadiness: @escaping @Sendable () throws -> Void,
                     clock: @escaping @Sendable () -> UInt64 = {
                         let seconds = Date().timeIntervalSince1970
                         return seconds.isFinite && seconds >= 0 && seconds < Double(UInt64.max / 1000)
                             ? UInt64(seconds * 1000) : UInt64.max
                     }) throws -> PairedSessionComponents {
        try validate(proof: proof, watch: watch, owner: owner, nowMillis: nowMillis, bootMillis: bootMillis)
        guard proof.nonce == publicationContext.proof.nonce,
              proof.signature == publicationContext.proof.signature,
              proof.sessionID == publicationContext.sessionID,
              selectedPeripheral == publicationContext.selectedPeripheral else { throw PairedOwnerError.invalid }
        let lease: @Sendable () -> Bool = {
            let now = clock(); return proof.issuedMillis <= now && now < proof.expiresMillis
        }
        let feature = Data(proof.sessionID.uuidString.utf8)
        let outer = try PairedSessionLedger(directory: directory, watch: watch.x963Representation,
            owner: owner.publicKey.x963Representation, nonce: proof.nonce, feature: feature, create: create)
        let receipt = try PairedReceiptLedger(directory: directory, watch: watch.x963Representation,
            owner: owner.publicKey.x963Representation, nonce: proof.nonce, feature: feature, create: create)
        return PairedSessionComponents(transport: try PairedAuthenticatedTransport(watch: watch, owner: owner,
            nonce: proof.nonce, feature: feature, ledger: outer, accessory: Data(proof.accessory.utf8), isCurrent: lease,
            requireReadiness: requireReadiness, invalidateReadiness: invalidateReadiness),
            replies: try PairedPersistedReplyDelivery(watch: watch, owner: owner, nonce: proof.nonce,
                feature: feature, ledger: receipt, isCurrent: lease,
                requireReadiness: requireReadiness, invalidateReadiness: invalidateReadiness), sessionID: proof.sessionID,
            notificationNamespace: PairedNotificationToken.namespace(watch: watch.x963Representation, owner: owner.publicKey.x963Representation),
            selectedPeripheral: selectedPeripheral, publicationContext: publicationContext)
    }
}
