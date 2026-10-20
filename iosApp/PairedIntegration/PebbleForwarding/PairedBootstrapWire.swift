import Foundation
import CryptoKit

// BPR1/BSA1 purpose is HPKE-material receipt only. Its UUID is our consent
// generation, explicitly NOT an Apple provider session or notification feature.
enum PairedBootstrapWire {
    private static func body(consent: PairedBootstrapConsent, capture: PairedBootstrapCapture,
                             watch: P256.Signing.PublicKey, owner: P256.Signing.PublicKey) throws -> Data {
        try capture.validate(consent: consent, owner: owner)
        guard capture.source == .security, let identifier = capture.hpkeIdentifier,
              let encapsulated = capture.encapsulatedKey, consent.challenge.count == 32 else {
            throw PairedOwnerError.invalid
        }
        return try materialBody(consent: consent, identifier: identifier, encapsulated: encapsulated, watch: watch, owner: owner)
    }
    private static func materialBody(consent: PairedBootstrapConsent, identifier: String, encapsulated: Data,
                                     watch: P256.Signing.PublicKey, owner: P256.Signing.PublicKey) throws -> Data {
        try PairedBootstrapSecurityProposal(generation: consent.generation, identifier: identifier,
            encapsulated: encapsulated).validate(consent: consent)
        guard consent.challenge.count == 32 else { throw PairedOwnerError.invalid }
        var data = Data("BPR1".utf8)
        data.append(watch.x963Representation); data.append(owner.x963Representation)
        data.append(consent.challenge)
        data.append(contentsOf: consent.generation.uuidString.utf8)
        for value in [consent.issuedMillis, consent.expiresMillis, consent.bootMillis] {
            var big = value.bigEndian; withUnsafeBytes(of: &big) { data.append(contentsOf: $0) }
        }
        data.append(UInt8(identifier.utf8.count)); data.append(contentsOf: identifier.utf8)
        data.append(encapsulated); return data
    }
    static func request(consent: PairedBootstrapConsent, capture: PairedBootstrapCapture,
                        watch: P256.Signing.PublicKey, owner: P256.Signing.PrivateKey) throws -> Data {
        var data = try body(consent: consent, capture: capture, watch: watch, owner: owner.publicKey)
        let transcript = Data("PebbleD HPKE bootstrap request v1".utf8) + data
        data.append(try owner.signature(for: transcript).rawRepresentation); return data
    }
    static func validateReceipt(_ receipt: Data, consent: PairedBootstrapConsent,
                                capture: PairedBootstrapCapture, watch: P256.Signing.PublicKey,
                                owner: P256.Signing.PublicKey) throws {
        let requestBody = try body(consent: consent, capture: capture, watch: watch, owner: owner)
        guard receipt.count == 100, receipt.prefix(4) == Data("BSA1".utf8),
              receipt.dropFirst(4).prefix(32) == Data(SHA256.hash(data: requestBody)),
              watch.isValidSignature(try P256.Signing.ECDSASignature(rawRepresentation: receipt.suffix(64)),
                                     for: receipt.prefix(36)) else { throw PairedOwnerError.invalid }
    }
    // Security has no owner private key or Main capture. Match the watch's
    // receipt against its exact retained callback and validated public consent.
    static func validateSecurityReceipt(_ receipt: Data, context: PairedBootstrapPublicContext,
                                        proposal: PairedBootstrapSecurityProposal, now: UInt64, boot: UInt64) throws {
        let (watch, owner) = try context.validate(now: now, boot: boot)
        try proposal.validate(consent: context.consent)
        let body = try materialBody(consent: context.consent, identifier: proposal.identifier,
            encapsulated: proposal.encapsulated, watch: watch, owner: owner)
        guard receipt.count == 100, receipt.prefix(4) == Data("BSA1".utf8),
              receipt.dropFirst(4).prefix(32) == Data(SHA256.hash(data: body)),
              watch.isValidSignature(try P256.Signing.ECDSASignature(rawRepresentation: receipt.suffix(64)),
                  for: receipt.prefix(36)) else { throw PairedOwnerError.invalid }
    }
}


enum PairedBootstrapRelayWire {
    static let pending = Data("BRN1".utf8)
    // Fixed 326-byte prefix; signature is the existing exact consent signature.
    private static func prefix(_ context: PairedBootstrapPublicContext) -> Data {
        let consent = context.consent
        var data = Data("BRP1".utf8)
        data.append(context.watchPublic); data.append(context.ownerPublic); data.append(consent.challenge)
        data.append(contentsOf: consent.generation.uuidString.utf8)
        data.append(contentsOf: consent.peripheral.uuidString.utf8)
        for value in [consent.issuedMillis, consent.expiresMillis, consent.bootMillis] {
            var big = value.bigEndian; withUnsafeBytes(of: &big) { data.append(contentsOf: $0) }
        }
        data.append(consent.signature)
        return data
    }
    static func request(_ proposal: PairedBootstrapSecurityProposal, context: PairedBootstrapPublicContext,
                        now: UInt64, boot: UInt64) throws -> Data {
        _ = try context.validate(now: now, boot: boot)
        try proposal.validate(consent: context.consent)
        var data = prefix(context)
        guard data.count == 326 else { throw PairedOwnerError.invalid }
        data.append(UInt8(proposal.identifier.utf8.count)); data.append(contentsOf: proposal.identifier.utf8)
        data.append(proposal.encapsulated)
        guard (393...456).contains(data.count) else { throw PairedOwnerError.invalid }
        return data
    }
    static func snapshot(_ bytes: Data, context: PairedBootstrapPublicContext,
                         now: UInt64, boot: UInt64) throws -> PairedBootstrapSecurityProposal? {
        let (watch, _) = try context.validate(now: now, boot: boot)
        if bytes == pending { return nil }
        let data = Data(bytes)
        guard (167...230).contains(data.count), data.prefix(4) == Data("BRS1".utf8),
              data[36] >= 1, data[36] <= 64, data.count == 166 + Int(data[36]),
              data.subdata(in: 4..<36) == Data(SHA256.hash(data: prefix(context))),
              let identifier = String(data: data.subdata(in: 37..<(37 + Int(data[36]))), encoding: .utf8),
              watch.isValidSignature(try P256.Signing.ECDSASignature(rawRepresentation: data.suffix(64)),
                for: Data("PebbleD HPKE relay snapshot v1".utf8) + data.dropLast(64)) else { throw PairedOwnerError.invalid }
        let proposal = PairedBootstrapSecurityProposal(generation: context.consent.generation, identifier: identifier,
            encapsulated: data.subdata(in: (37 + Int(data[36]))..<(102 + Int(data[36]))))
        try proposal.validate(consent: context.consent)
        return proposal
    }
}
