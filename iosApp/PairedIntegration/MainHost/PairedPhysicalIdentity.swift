import PebbleProtocol
import Foundation
import CryptoKit
import Security

enum PairedIdentityLookupError: Error, LocalizedError {
    case unavailable(OSStatus)
    case invalidStoredIdentity
    var errorDescription: String? {
        switch self {
        case .unavailable(let status):
            return "The saved identity could not be accessed (Keychain status \(status)). Existing keys were preserved; unlock the phone and retry."
        case .invalidStoredIdentity:
            return "The saved identity has invalid data or protection attributes. Existing keys were preserved; no replacement identity was created."
        }
    }
}

// Only the explicit main-app ceremony may create an owner identity. Extensions
// retain their existing read-only factory; staged state cannot authorize data.
enum PairedPhysicalIdentity {
    struct Staged: Codable {
        let ownerPrivate: Data
        let pinnedWatchPublic: Data
        var owner: P256.Signing.PrivateKey { get throws { try P256.Signing.PrivateKey(rawRepresentation: ownerPrivate) } }
        var watch: P256.Signing.PublicKey { get throws { try P256.Signing.PublicKey(x963Representation: pinnedWatchPublic) } }
    }
    private static func query(_ account: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: NotificationBridgeConfiguration.notificationName("paired-owner"),
         kSecAttrAccount as String: account,
         kSecAttrAccessGroup as String: PairedNativeSessionFactory.keychainGroup,
         kSecAttrSynchronizable as String: false]
    }
    private static func read(_ account: String) throws -> Staged? {
        guard PairedNotificationGate.enabled else { throw PairedOwnerError.unavailable }
        var q = query(account); q[kSecReturnData as String] = true; q[kSecReturnAttributes as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?; let status = SecItemCopyMatching(q as CFDictionary, &result)
        return try decodeKeychainRead(status: status, attributes: result as? [String: Any])
    }
    // Only errSecItemNotFound denotes absence. Protected/entitlement failures,
    // malformed records and invalid keys must never become permission to stage.
    static func decodeKeychainRead(status: OSStatus, attributes: [String: Any]?) throws -> Staged? {
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess else { throw PairedIdentityLookupError.unavailable(status) }
        guard let attributes,
              attributes[kSecAttrAccessible as String] as? String == kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly as String,
              let data = attributes[kSecValueData as String] as? Data, !data.isEmpty, data.count <= 4096 else {
            throw PairedIdentityLookupError.invalidStoredIdentity
        }
        do {
            let item = try JSONDecoder().decode(Staged.self, from: data)
            _ = try item.owner; _ = try item.watch; return item
        } catch {
            // No decoder/key material is included in user diagnostics or logs.
            throw PairedIdentityLookupError.invalidStoredIdentity
        }
    }
    static func existingActiveForExplicitResume() throws -> Staged? {
        try read("owner-v1")
    }

    private static func add(_ item: Staged, account: String) throws {
        var q = query(account)
        q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        q[kSecValueData as String] = try JSONEncoder().encode(item)
        guard SecItemAdd(q as CFDictionary, nil) == errSecSuccess else { throw PairedOwnerError.unavailable }
        guard let saved = try read(account), saved.ownerPrivate == item.ownerPrivate,
              saved.pinnedWatchPublic == item.pinnedWatchPublic else { throw PairedOwnerError.invalid }
    }
    static func existingForExplicitResume() throws -> Staged {
        if let active = try read("owner-v1") { return active }
        guard let pending = try read("pending-owner-v1") else { throw PairedOwnerError.unavailable }
        return pending
    }
    // Called only after an explicit Create/Resume UI action, never on launch or a callback.
    static func stage(afterExplicitConsent watch: Data) throws -> Staged {
        guard PairedNotificationGate.enabled else { throw PairedOwnerError.unavailable }
        _ = try P256.Signing.PublicKey(x963Representation: watch)
        if let active = try read("owner-v1") {
            guard active.pinnedWatchPublic == watch else { throw PairedOwnerError.invalid }
            return active
        }
        if let pending = try read("pending-owner-v1") {
            guard pending.pinnedWatchPublic == watch else { throw PairedOwnerError.invalid }
            return pending
        }
        let item = Staged(ownerPrivate: P256.Signing.PrivateKey().rawRepresentation, pinnedWatchPublic: watch)
        try add(item, account: "pending-owner-v1"); return item
    }
    static func code(watch: Data, owner: Data) throws -> Data {
        _ = try P256.Signing.PublicKey(x963Representation: watch)
        _ = try P256.Signing.PublicKey(x963Representation: owner)
        return Data(SHA256.hash(data: Data("PebbleD physical code v1\0".utf8) + watch + owner))
    }
    static func proposal(_ item: Staged, challenge: Data) throws -> Data {
        guard challenge.count == 32 else { throw PairedOwnerError.invalid }
        let owner = try item.owner
        let body = Data("PEP1".utf8) + challenge + owner.publicKey.x963Representation
        let transcript = Data("PebbleD physical proposal v1".utf8) + item.pinnedWatchPublic + body
        return body + (try owner.signature(for: transcript).rawRepresentation)
    }
    static func verifyReceipt(_ item: Staged, _ receipt: Data) throws {
        guard receipt.count == 100, receipt.prefix(4) == Data("PEA1".utf8) else {
            throw PairedOwnerError.invalid
        }
        let owner = try item.owner; let watch = try item.watch
        let expected = try code(watch: item.pinnedWatchPublic, owner: owner.publicKey.x963Representation)
        guard receipt.subdata(in: 4..<36) == expected,
              watch.isValidSignature(try P256.Signing.ECDSASignature(rawRepresentation: receipt.suffix(64)),
                                     for: receipt.prefix(36)) else { throw PairedOwnerError.invalid }
    }
    static func activate(_ item: Staged, signedWatchAcknowledgement receipt: Data) throws {
        guard PairedNotificationGate.enabled else { throw PairedOwnerError.unavailable }
        try verifyReceipt(item, receipt)
        if let active = try read("owner-v1") {
            guard active.ownerPrivate == item.ownerPrivate, active.pinnedWatchPublic == item.pinnedWatchPublic else {
                throw PairedOwnerError.invalid
            }
        } else {
            guard let pending = try read("pending-owner-v1"), pending.ownerPrivate == item.ownerPrivate,
                  pending.pinnedWatchPublic == item.pinnedWatchPublic else { throw PairedOwnerError.invalid }
            try add(item, account: "owner-v1")
        }
        // The durable active item exists before staging cleanup. A cleanup error
        // cannot regenerate, overwrite, or deactivate the accepted identity.
        if let pending = try read("pending-owner-v1") {
            guard pending.ownerPrivate == item.ownerPrivate, pending.pinnedWatchPublic == item.pinnedWatchPublic else {
                throw PairedOwnerError.invalid
            }
            let status = SecItemDelete(query("pending-owner-v1") as CFDictionary)
            guard status == errSecSuccess || status == errSecItemNotFound else {
                throw PairedIdentityLookupError.unavailable(status)
            }
        }
    }
}
