#if os(iOS)
import PebbleProtocol
import Foundation
import CryptoKit
import Security
import Darwin
import OSLog

enum PairedNativeSessionFactory {
    static let group = NotificationBridgeConfiguration.appGroup
    static let keychainGroup = NotificationBridgeConfiguration.keychainGroup
    private struct Identity: Codable { let ownerPrivate: Data; let pinnedWatchPublic: Data }

    // Gate checked before container/Keychain access. No key generation,
    // registration, key export, fallback identity or silent state recreation.
    static func loadValidatedPublication(expectedSession: UUID? = nil) throws -> PairedValidatedPublicationContext {
        let directory = try privateDirectory(create: false)
        let (watch, owner) = try readIdentity()
        let bytes = try PairedPublicationStore(directory: directory).readPublicationBytes()
        let context = try PairedValidatedPublicationContext.validated(bytes: bytes, watch: watch, owner: owner,
            nowMillis: nowMillis(), bootMillis: bootMillis())
        if let expectedSession, context.sessionID != expectedSession { throw PairedOwnerError.stale }
        return context
    }
    static func load(expectedSession: UUID? = nil) throws -> PairedSessionComponents {
        let directory = try privateDirectory(create: false)
        let (watch, owner) = try readIdentity()
        let context = try loadValidatedPublication(expectedSession: expectedSession)
        return try PairedSessionFactory.make(proof: context.proof, selectedPeripheral: context.selectedPeripheral,
            watch: watch, owner: owner, directory: directory,
            nowMillis: nowMillis(), bootMillis: bootMillis(), create: false,
            publicationContext: context,
            requireReadiness: { try requireReadiness(context: context) },
            invalidateReadiness: { try invalidateReadiness(context: context) })
    }
    // Caller must first await the final successful SESSION GATT response, then
    // recheck actual provider/UUID authorization and cancellation. Never called
    // merely because a proof or SESSION byte string exists.
    static func acknowledgeSession(context: PairedValidatedPublicationContext,
                                   identifier: Data, encapsulated: Data) throws {
        try Task.checkCancellation()
        let directory = try privateDirectory(create: false)
        let (watch, owner) = try readIdentity()
        try checkActiveLedgers(context: context, directory: directory, watch: watch, owner: owner)
        let record = try PairedSessionReadiness.signed(context: context, identifier: identifier,
            encapsulated: encapsulated, owner: owner)
        let bytes = try JSONEncoder().encode(record)
        try PairedPublicationStore(directory: directory).commitReadiness(
            expectedPublicationBytes: context.publicationBytes, readinessBytes: bytes) {
                try Task.checkCancellation()
                try checkActiveLedgers(context: context, directory: directory, watch: watch, owner: owner, publisherLocked: true)
                try context.publication.validateBinding(watch: watch, owner: owner.publicKey)
                try PairedSessionFactory.validate(proof: context.proof, watch: watch, owner: owner,
                    nowMillis: nowMillis(), bootMillis: bootMillis())
                try record.validate(context: context, owner: owner.publicKey)
            }
    }
    static func requireReadiness(context: PairedValidatedPublicationContext) throws {
        let directory = try privateDirectory(create: false)
        let (watch, owner) = try readIdentity()
        try checkActiveLedgers(context: context, directory: directory, watch: watch, owner: owner)
        let bytes = try PairedPublicationStore(directory: directory).readReadiness(
            expectedPublicationBytes: context.publicationBytes)
        let record = try JSONDecoder().decode(PairedSessionReadiness.self, from: bytes)
        try record.validate(context: context, owner: owner.publicKey)
        try context.publication.validateBinding(watch: watch, owner: owner.publicKey)
        try PairedSessionFactory.validate(proof: context.proof, watch: watch, owner: owner,
            nowMillis: nowMillis(), bootMillis: bootMillis())
    }
    private static func checkActiveLedgers(context: PairedValidatedPublicationContext, directory: URL,
                                          watch: P256.Signing.PublicKey, owner: P256.Signing.PrivateKey,
                                          publisherLocked: Bool = false) throws {
        let feature = Data(context.sessionID.uuidString.utf8)
        // Either durable revocation blocks both protocols even if deletion of
        // the readiness marker itself was uncertain or temporarily unavailable.
        let outer = try PairedSessionLedger(directory: directory, watch: watch.x963Representation,
            owner: owner.publicKey.x963Representation, nonce: context.proof.nonce, feature: feature, create: false)
        try outer.checkActive()
        let receipt = try PairedReceiptLedger(directory: directory, watch: watch.x963Representation,
            owner: owner.publicKey.x963Representation, nonce: context.proof.nonce, feature: feature, create: false)
        if publisherLocked { try receipt.checkActiveWhilePublisherLocked() }
        else { try receipt.checkActive() }
    }
    static func invalidateReadiness(context: PairedValidatedPublicationContext) throws {
        let directory = try privateDirectory(create: false)
        try PairedPublicationStore(directory: directory).clearReadiness(
            expectedPublicationBytes: context.publicationBytes)
    }
    static func privateDirectory(create: Bool) throws -> URL {
        guard PairedNotificationGate.enabled,
              let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group) else {
            throw PairedOwnerError.unavailable
        }
        let directory = container.appendingPathComponent("paired-notifications", isDirectory: true)
        if create {
            if mkdir(directory.path, 0o700) == 0 {
                try FileManager.default.setAttributes([.protectionKey:
                    FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: directory.path)
            } else if errno != EEXIST { throw PairedOwnerError.unavailable }
        }
        _ = try PairedPublicationStore(directory: directory)
        return directory
    }
    static func readIdentity() throws -> (P256.Signing.PublicKey, P256.Signing.PrivateKey) {
        guard PairedNotificationGate.enabled else { throw PairedOwnerError.unavailable }
        var result: CFTypeRef?
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: NotificationBridgeConfiguration.notificationName("paired-owner"),
            kSecAttrAccount as String: "owner-v1", kSecAttrAccessGroup as String: keychainGroup,
            kSecAttrSynchronizable as String: false,
            kSecReturnData as String: true, kSecReturnAttributes as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne]
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        return try decodeIdentityLookup(status: status, attributes: result as? [String: Any]) { data in
            let identity = try JSONDecoder().decode(Identity.self, from: data)
            let watch = try P256.Signing.PublicKey(x963Representation: identity.pinnedWatchPublic)
            let owner = try P256.Signing.PrivateKey(rawRepresentation: identity.ownerPrivate)
            return (watch, owner)
        }
    }
    private static let identityLogger = Logger(subsystem: NotificationBridgeConfiguration.namespace, category: "paired-bootstrap-diagnostic")
    // OSStatus is a system Int32, so every value has a bounded numeric-only
    // representation. Never log descriptions, attributes or identity values.
    static func identityLookupStatusLog(_ status: OSStatus) -> String {
        return "identityLookupStatus code=" + String(status)
    }
    static func decodeIdentityLookup<T>(status: OSStatus, attributes: [String: Any]?,
                                         decode: (Data) throws -> T) throws -> T {
        guard status == errSecSuccess else {
            identityLogger.notice("\(identityLookupStatusLog(status), privacy: .public)")
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status), userInfo: nil)
        }
        guard let attributes,
              attributes[kSecAttrAccessible as String] as? String == kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly as String,
              let data = attributes[kSecValueData as String] as? Data, data.count <= 4096 else {
            identityLogger.notice("identityAttributesRejected")
            throw PairedOwnerError.unavailable
        }
        do { return try decode(data) }
        catch {
            identityLogger.notice("identityDecodeRejected")
            throw error
        }
    }
    static func bootMillis() throws -> UInt64 {
        var boot = timeval(); var size = MemoryLayout<timeval>.size
        guard sysctlbyname("kern.boottime", &boot, &size, nil, 0) == 0, boot.tv_sec >= 0 else {
            throw PairedOwnerError.unavailable
        }
        guard boot.tv_usec >= 0, boot.tv_usec < 1000000,
              UInt64(boot.tv_sec) <= UInt64.max / 1000 else { throw PairedOwnerError.unavailable }
        let value = (UInt64(boot.tv_sec) * 1000).addingReportingOverflow(UInt64(boot.tv_usec / 1000))
        guard !value.overflow else { throw PairedOwnerError.unavailable }
        return value.partialValue
    }
    static func nowMillis() throws -> UInt64 {
        let seconds = Date().timeIntervalSince1970
        guard seconds.isFinite, seconds >= 0, seconds < Double(UInt64.max / 1000) else { throw PairedOwnerError.stale }
        return UInt64(seconds * 1000)
    }
}
#endif
