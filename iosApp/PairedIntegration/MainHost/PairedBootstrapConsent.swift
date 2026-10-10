import Foundation
import CryptoKit

// This consent permits obtaining Apple's actual callback identifiers only.
// It is never accepted by PairedSessionFactory as notification authorization.
struct PairedBootstrapConsent: Codable, Sendable {
    let generation: UUID
    let peripheral: UUID
    let challenge: Data
    let watchHash: Data
    let ownerHash: Data
    let issuedMillis: UInt64
    let expiresMillis: UInt64
    let bootMillis: UInt64
    var signature: Data

    private func transcript() throws -> Data {
        guard challenge.count == 32, watchHash.count == 32, ownerHash.count == 32,
              issuedMillis < expiresMillis, expiresMillis - issuedMillis <= 30000 else {
            throw PairedOwnerError.invalid
        }
        var bytes = Data("PebbleD/bootstrap-consent/v1\0".utf8)
        bytes.append(contentsOf: generation.uuidString.lowercased().utf8)
        bytes.append(contentsOf: peripheral.uuidString.lowercased().utf8)
        bytes.append(challenge); bytes.append(watchHash); bytes.append(ownerHash)
        for value in [issuedMillis, expiresMillis, bootMillis] {
            var big = value.bigEndian
            withUnsafeBytes(of: &big) { bytes.append(contentsOf: $0) }
        }
        return bytes
    }
    static func confirmed(peripheral: UUID, challenge: Data, displayedFingerprint: Data,
                          watch: P256.Signing.PublicKey, owner: P256.Signing.PrivateKey,
                          now: UInt64, boot: UInt64) throws -> Self {
        let hash = Data(SHA256.hash(data: owner.publicKey.x963Representation))
        guard displayedFingerprint == hash, now <= UInt64.max - 30000 else {
            throw PairedOwnerError.invalid
        }
        var consent = Self(generation: UUID(), peripheral: peripheral, challenge: challenge,
            watchHash: Data(SHA256.hash(data: watch.x963Representation)), ownerHash: hash,
            issuedMillis: now, expiresMillis: now + 30000, bootMillis: boot, signature: Data())
        consent.signature = try owner.signature(for: consent.transcript()).rawRepresentation
        return consent
    }
    func validate(watch: P256.Signing.PublicKey, owner: P256.Signing.PublicKey,
                  now: UInt64, boot: UInt64) throws {
        guard issuedMillis <= now, now < expiresMillis, bootMillis == boot,
              watchHash == Data(SHA256.hash(data: watch.x963Representation)),
              ownerHash == Data(SHA256.hash(data: owner.x963Representation)),
              owner.isValidSignature(try P256.Signing.ECDSASignature(rawRepresentation: signature),
                                     for: try transcript()) else { throw PairedOwnerError.stale }
    }
}

// Values copied directly from Apple's provider/security callbacks. These
// signatures bind process-shared metadata to explicit consent; they do not
// substitute for the watch's PFS1 proof or authorize a reply/notification.
struct PairedBootstrapCapture: Codable, Sendable {
    enum Source: String, Codable, Sendable { case provider, security }
    let source: Source
    let generation: UUID
    let sessionID: UUID?
    let peripheral: UUID?
    let hpkeIdentifier: String?
    let encapsulatedKey: Data?
    var signature: Data

    private func transcript() throws -> Data {
        var bytes = Data("PebbleD/bootstrap-capture/v1\0".utf8)
        bytes.append(contentsOf: source.rawValue.utf8); bytes.append(0)
        bytes.append(contentsOf: generation.uuidString.lowercased().utf8)
        switch source {
        case .provider:
            guard let sessionID, let peripheral, hpkeIdentifier == nil, encapsulatedKey == nil else {
                throw PairedOwnerError.invalid
            }
            bytes.append(contentsOf: sessionID.uuidString.lowercased().utf8)
            bytes.append(contentsOf: peripheral.uuidString.lowercased().utf8)
        case .security:
            guard sessionID == nil, peripheral == nil, let hpkeIdentifier, let encapsulatedKey,
                  !hpkeIdentifier.isEmpty, hpkeIdentifier.utf8.count <= 64,
                  !hpkeIdentifier.utf8.contains(0), encapsulatedKey.count == 65 else {
                throw PairedOwnerError.invalid
            }
            _ = try P256.KeyAgreement.PublicKey(x963Representation: encapsulatedKey)
            bytes.append(UInt8(hpkeIdentifier.utf8.count))
            bytes.append(contentsOf: hpkeIdentifier.utf8); bytes.append(encapsulatedKey)
        }
        return bytes
    }
    static func provider(sessionID: UUID, peripheral: UUID, consent: PairedBootstrapConsent,
                         owner: P256.Signing.PrivateKey) throws -> Self {
        guard peripheral == consent.peripheral else { throw PairedOwnerError.stale }
        var capture = Self(source: .provider, generation: consent.generation, sessionID: sessionID,
            peripheral: peripheral, hpkeIdentifier: nil, encapsulatedKey: nil, signature: Data())
        capture.signature = try owner.signature(for: capture.transcript()).rawRepresentation
        return capture
    }
    static func security(identifier: String, encapsulated: Data, consent: PairedBootstrapConsent,
                         owner: P256.Signing.PrivateKey) throws -> Self {
        var capture = Self(source: .security, generation: consent.generation, sessionID: nil,
            peripheral: nil, hpkeIdentifier: identifier, encapsulatedKey: encapsulated, signature: Data())
        capture.signature = try owner.signature(for: capture.transcript()).rawRepresentation
        return capture
    }
    func validate(consent: PairedBootstrapConsent, owner: P256.Signing.PublicKey) throws {
        guard generation == consent.generation,
              source != .provider || peripheral == consent.peripheral,
              owner.isValidSignature(try P256.Signing.ECDSASignature(rawRepresentation: signature),
                                     for: try transcript()) else { throw PairedOwnerError.stale }
    }
}

// Trust root: the enrolled Main identity and OS-enforced membership of this
// application suite. These public keys are not secrets; the consent signature
// binds their hashes but does not attest which sibling process wrote the file.
struct PairedBootstrapPublicContext: Codable {
    let consent: PairedBootstrapConsent
    let watchPublic: Data
    let ownerPublic: Data

    func validate(now: UInt64, boot: UInt64) throws -> (P256.Signing.PublicKey, P256.Signing.PublicKey) {
        guard watchPublic.count == 65, ownerPublic.count == 65 else { throw PairedOwnerError.invalid }
        let watch = try P256.Signing.PublicKey(x963Representation: watchPublic)
        let owner = try P256.Signing.PublicKey(x963Representation: ownerPublic)
        try consent.validate(watch: watch, owner: owner, now: now, boot: boot)
        return (watch, owner)
    }
}

// An untrusted proposal, never notification authorization or proof of Apple
// origin. Main adopts it only under authoritative current consent. Security
// retains its actual callback and requires byte equality before completion.
struct PairedBootstrapSecurityProposal: Codable, Equatable {
    let generation: UUID
    let identifier: String
    let encapsulated: Data

    func validate(consent: PairedBootstrapConsent) throws {
        guard generation == consent.generation else { throw PairedOwnerError.stale }
        guard !identifier.isEmpty, identifier.utf8.count <= 64,
              !identifier.utf8.contains(0), encapsulated.count == 65 else { throw PairedOwnerError.invalid }
        _ = try P256.KeyAgreement.PublicKey(x963Representation: encapsulated)
    }
    func matches(_ capture: PairedBootstrapCapture) -> Bool {
        capture.source == .security && capture.generation == generation &&
        capture.sessionID == nil && capture.peripheral == nil &&
        capture.hpkeIdentifier == identifier && capture.encapsulatedKey == encapsulated
    }
}
