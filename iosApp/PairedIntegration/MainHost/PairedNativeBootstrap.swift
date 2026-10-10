#if os(iOS)
import Foundation
import CryptoKit

// Loaded only for a foreground user's signed, thirty-second bootstrap consent.
// No identity creation, notification transport or active-proof fallback.
struct PairedNativeBootstrap {
    let store: PairedBootstrapStore
    let consent: PairedBootstrapConsent
    let watch: P256.Signing.PublicKey
    let owner: P256.Signing.PrivateKey

    static func load(observe: (PairedDiagnosticStatus.Event) -> Void = { _ in }) throws -> Self {
        observe(.securityIdentityReadStarted)
        let (watch, owner) = try PairedNativeSessionFactory.readIdentity()
        observe(.securityIdentityReadSucceeded)
        observe(.securityDirectoryReadStarted)
        let store = PairedBootstrapStore(directory: try PairedNativeSessionFactory.privateDirectory(create: false))
        observe(.securityDirectoryReadSucceeded)
        observe(.securityConsentReadStarted)
        let consent = try store.consent(watch: watch, owner: owner.publicKey,
            now: PairedNativeSessionFactory.nowMillis(), boot: PairedNativeSessionFactory.bootMillis())
        observe(.securityConsentReadSucceeded)
        return Self(store: store, consent: consent, watch: watch, owner: owner)
    }
    func captureProvider(sessionID: UUID, peripheral: UUID) throws {
        let value = try PairedBootstrapCapture.provider(sessionID: sessionID, peripheral: peripheral,
            consent: consent, owner: owner)
        try capture(value)
    }
    func captureSecurity(identifier: String, encapsulated: Data) throws {
        try capture(PairedBootstrapCapture.security(identifier: identifier, encapsulated: encapsulated,
            consent: consent, owner: owner))
    }
    private func capture(_ value: PairedBootstrapCapture) throws {
        try store.capture(value, consent: consent, watch: watch, owner: owner.publicKey,
            now: PairedNativeSessionFactory.nowMillis(), boot: PairedNativeSessionFactory.bootMillis())
    }
    func captured(_ source: PairedBootstrapCapture.Source) throws -> PairedBootstrapCapture? {
        try store.captured(source, consent: consent, watch: watch, owner: owner.publicKey,
            now: PairedNativeSessionFactory.nowMillis(), boot: PairedNativeSessionFactory.bootMillis())
    }
    func requireCurrent() throws {
        let current = try store.consent(watch: watch, owner: owner.publicKey,
            now: PairedNativeSessionFactory.nowMillis(), boot: PairedNativeSessionFactory.bootMillis())
        guard current.generation == consent.generation else { throw PairedOwnerError.stale }
    }
}

// Security only needs public pins, consent and Apple's callback. Its sandbox
// cannot reach securityd on the observed device; Main owns all new signing.
struct PairedSecurityBootstrap {
    let store: PairedBootstrapStore
    let context: PairedBootstrapPublicContext
    let watch: P256.Signing.PublicKey
    var consent: PairedBootstrapConsent { context.consent }

    static func load(observe: (PairedDiagnosticStatus.Event) -> Void = { _ in }) throws -> Self {
        guard PairedNotificationGate.enabled,
              let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: PairedNativeSessionFactory.group) else {
            throw PairedOwnerError.unavailable
        }
        observe(.securityDirectoryReadStarted)
        // Do not instantiate PairedPublicationStore: that creates a write lock.
        let store = PairedBootstrapStore(directory: container.appendingPathComponent("paired-notifications", isDirectory: true))
        observe(.securityConsentReadStarted)
        let context = try store.publicContext(now: PairedNativeSessionFactory.nowMillis(), boot: PairedNativeSessionFactory.bootMillis())
        let (watch, _) = try context.validate(now: PairedNativeSessionFactory.nowMillis(), boot: PairedNativeSessionFactory.bootMillis())
        observe(.securityConsentReadSucceeded)
        return Self(store: store, context: context, watch: watch)
    }
    func requireCurrent() throws {
        let current = try store.publicContext(now: PairedNativeSessionFactory.nowMillis(), boot: PairedNativeSessionFactory.bootMillis())
        guard current.consent.generation == consent.generation,
              current.consent.signature == consent.signature,
              current.watchPublic == context.watchPublic, current.ownerPublic == context.ownerPublic else { throw PairedOwnerError.stale }
    }

}
#endif
