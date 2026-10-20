import Foundation
import CryptoKit

enum PairedNotificationToken {
    static func namespace(watch: Data, owner: Data) -> Data {
        Data(SHA256.hash(data: Data("PebbleD forwarded notification namespace v1".utf8) + watch + owner))
    }
    static func make(namespace: Data, source: String, notification: String) -> String {
        var input = Data("PNT1".utf8); input.append(namespace)
        for value in [source, notification] {
            let bytes = Data(value.utf8)
            let count = UInt64(bytes.count)
            for shift in stride(from: 56, through: 0, by: -8) { input.append(UInt8(truncatingIfNeeded: count >> shift)) }
            input.append(bytes)
        }
        return "pnt1:" + SHA256.hash(data: input).map { String(format: "%02x", $0) }.joined()
    }
}
