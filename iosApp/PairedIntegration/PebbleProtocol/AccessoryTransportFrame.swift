package import Foundation

/// The watch's AccessoryNotifications transport: the GATT service
/// `accessory_transport_service.c` hosts, and the frames written to it and
/// notified from it. Apple fixes neither, so both ends are this app's and the
/// firmware's to agree on; everything inside a DATA or RESPONSE frame is sealed
/// and opened by iOS and the watch, never by the app.
package enum AccessoryTransportFrame {
    package static let serviceUUID = "50000000-328E-0FBB-C642-1AA6699BDADA"
    /// Watch to phone: PUBKEY and RESPONSE by notification, and PUBKEY again by
    /// a read.
    package static let notifyCharacteristicUUID = "50000001-328E-0FBB-C642-1AA6699BDADA"
    /// Phone to watch, by write with response: SESSION and DATA.
    package static let writeCharacteristicUUID = "50000002-328E-0FBB-C642-1AA6699BDADA"

    static let publicKeyType: UInt8 = 0x01
    static let sessionType: UInt8 = 0x02
    static let dataType: UInt8 = 0x03
    static let responseType: UInt8 = 0x82

    static let moreFlag: UInt8 = 0x01
    static let firstFlag: UInt8 = 0x02

    /// A P-256 point without its `0x04` prefix, as the watch notifies it and as
    /// `SecurityMessage` carries it.
    package static let publicKeyByteCount = 64
    /// An uncompressed P-256 point: HPKE's `enc` for this suite.
    package static let encapsulatedKeyByteCount = 65
    /// `ATS_MAX_UUID_LEN`, beyond which the watch refuses the session.
    static let maximumAccessoryIdentifierByteCount = 64
    /// `ATS_MAX_RX_WRITE`: the ATT attribute value limit, which the watch refuses
    /// a write beyond.
    static let maximumWriteByteCount = 512
    /// `ACCESSORY_TRANSPORT_MAX_FEATURE_ID_LEN`.
    static let maximumFeatureIDByteCount = 64
    /// `ACCESSORY_TRANSPORT_MAX_PAYLOAD`, sealed: nonce, plaintext, tag.
    static let maximumSealedByteCount = 12 + 2_048 + 16

    /// The watch's public key, from what it notifies after a subscription or
    /// answers a read with; nil for any other frame.
    package static func publicKey(from frame: [UInt8]) -> [UInt8]? {
        guard frame.count == 1 + publicKeyByteCount, frame.first == publicKeyType else { return nil }
        return Array(frame.dropFirst())
    }

    /// `enc | u8 uuid_len | uuid`. The identifier is the one iOS put in the HPKE
    /// info, and the watch puts these bytes in its own as they are: a UUID parsed
    /// and printed again comes out in whatever case the printer chose, and the
    /// two ends then derive different keys. Too long is refused rather than cut,
    /// for the same reason.
    package static func session(
        encapsulatedKey: [UInt8],
        accessoryIdentifier: String
    ) throws -> [UInt8] {
        guard encapsulatedKey.count == encapsulatedKeyByteCount else {
            throw AccessoryTransportFrameError.malformedKey
        }
        let identifier = Array(accessoryIdentifier.utf8)
        guard !identifier.isEmpty, identifier.count <= maximumAccessoryIdentifierByteCount else {
            throw AccessoryTransportFrameError.malformedIdentifier
        }
        return [sessionType] + encapsulatedKey + [UInt8(identifier.count)] + identifier
    }

    /// `u8 feature_id_len | feature_id | sealed`, cut into writes of at most
    /// `maximumWriteLength` bytes as `0x03 | flags | chunk`. The watch starts over
    /// on FIRST, so a message a restarted extension abandoned half way is not
    /// spliced onto the next one.
    package static func dataWrites(
        featureID: UUID,
        sealed: [UInt8],
        maximumWriteLength: Int
    ) throws -> [[UInt8]] {
        guard sealed.count <= maximumSealedByteCount else {
            throw AccessoryTransportFrameError.messageTooLarge
        }
        let chunkByteCount = min(maximumWriteLength, maximumWriteByteCount) - 2
        guard chunkByteCount > 0 else { throw AccessoryTransportFrameError.messageTooLarge }
        let featureIDBytes = Array(featureID.uuidString.utf8)
        let logical = [UInt8(featureIDBytes.count)] + featureIDBytes + sealed
        return stride(from: 0, to: logical.count, by: chunkByteCount).map { offset in
            let end = min(offset + chunkByteCount, logical.count)
            var flags: UInt8 = 0
            if offset == 0 { flags |= firstFlag }
            if end < logical.count { flags |= moreFlag }
            return [dataType, flags] + logical[offset..<end]
        }
    }
}

package enum AccessoryTransportFrameError: Error, Equatable, Sendable {
    case malformedKey
    case malformedIdentifier
    case messageTooLarge
}

/// A reply the watch sealed, reassembled from its RESPONSE notifications.
package struct AccessoryTransportResponse: Equatable, Sendable {
    package var featureID: UUID
    package var sealed: [UInt8]
}

/// Puts `0x82 | flags | chunk` notifications back together. One per link: the
/// watch has a single reply in flight to a phone at a time.
package struct AccessoryTransportResponseReassembler: Sendable {
    /// Nil until a FIRST fragment starts a reply. A tail heard without its
    /// start — the start went out on a link that has since dropped — would
    /// otherwise be read as a reply of its own.
    private var pending: [UInt8]?

    package init() {}

    /// Forgets a reply half heard, for a link that has dropped.
    package mutating func reset() {
        pending = nil
    }

    /// The reply the fragment completes; nil while more is to come, and for
    /// anything that is not a RESPONSE fragment or does not make a reply.
    package mutating func receive(_ frame: [UInt8]) -> AccessoryTransportResponse? {
        guard frame.count >= 2, frame[0] == AccessoryTransportFrame.responseType else { return nil }
        let flags = frame[1]
        if flags & AccessoryTransportFrame.firstFlag != 0 {
            pending = []
        }
        guard var logical = pending.take() else { return nil }
        logical += frame.dropFirst(2)
        let maximum = 1 + AccessoryTransportFrame.maximumFeatureIDByteCount
            + AccessoryTransportFrame.maximumSealedByteCount
        guard logical.count <= maximum else { return nil }
        guard flags & AccessoryTransportFrame.moreFlag == 0 else {
            pending = logical
            return nil
        }
        guard let length = logical.first.map(Int.init), length > 0, logical.count > length,
              let featureID = UUID(uuidString: String(decoding: logical[1...length], as: UTF8.self)) else {
            return nil
        }
        return AccessoryTransportResponse(featureID: featureID, sealed: Array(logical.dropFirst(1 + length)))
    }
}
