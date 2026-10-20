import Foundation
import CryptoKit

enum PairedProofWire {
    static func request(nonce: Data, sessionID: UUID, accessory: String,
                        issuedMillis: UInt64, expiresMillis: UInt64, bootMillis: UInt64,
                        watch: P256.Signing.PublicKey, owner: P256.Signing.PrivateKey) throws -> Data {
        let proof = PairedSessionProof(nonce: nonce, sessionID: sessionID, accessory: accessory,
            issuedMillis: issuedMillis, expiresMillis: expiresMillis, bootMillis: bootMillis, signature: Data())
        guard !accessory.utf8.contains(0), expiresMillis > issuedMillis,
              expiresMillis - issuedMillis <= 300000 else { throw PairedOwnerError.invalid }
        let transcript = try proof.transcript(watch: watch.x963Representation, owner: owner.publicKey.x963Representation)
        let proofDomain = Data("PebbleD session factory proof v1".utf8)
        let body = Data(transcript.dropFirst(proofDomain.count + 130))
        var signed = Data("PebbleD session factory request v1".utf8)
        signed.append(watch.x963Representation); signed.append(owner.publicKey.x963Representation); signed.append(body)
        return Data("PPR1".utf8) + body + (try owner.signature(for: signed).rawRepresentation)
    }

    static func decode(_ wire: Data) throws -> PairedSessionProof {
        let b = [UInt8](wire)
        guard b.count >= 162, b.count <= 225, Data(b[0..<4]) == Data("PFS1".utf8) else {
            throw PairedOwnerError.invalid
        }
        let length = Int(b[72])
        guard length > 0, length <= 64, b.count == 161 + length,
              let uuidText = String(bytes: b[36..<72], encoding: .utf8),
              let sessionID = UUID(uuidString: uuidText), sessionID.uuidString == uuidText,
              let accessory = String(bytes: b[73..<(73 + length)], encoding: .utf8),
              !accessory.utf8.contains(0) else { throw PairedOwnerError.invalid }
        func number(_ offset: Int) -> UInt64 { b[offset..<(offset + 8)].reduce(0) { ($0 << 8) | UInt64($1) } }
        let times = 73 + length
        return PairedSessionProof(nonce: Data(b[4..<36]), sessionID: sessionID, accessory: accessory,
            issuedMillis: number(times), expiresMillis: number(times + 8), bootMillis: number(times + 16),
            signature: Data(b.suffix(64)))
    }
}
