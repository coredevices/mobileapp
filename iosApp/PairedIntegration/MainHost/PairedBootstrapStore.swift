import Foundation
import CryptoKit
import Darwin

enum PairedBootstrapStoreError: Error { case busy, notCreated }

enum PairedBootstrapCallbackAdmission {
    static func requireCurrent(invalidated: Bool, current: UUID?, expected: UUID) throws {
        guard !invalidated, current == expected else { throw PairedOwnerError.stale }
    }
}

// Retry only a lock that was busy before any store operation ran. All identity,
// generation, signature and expiry checks are re-evaluated by the operation.
enum PairedBootstrapStoreAccess {
    @MainActor static func retryBusy<T>(_ operation: () throws -> T) async throws -> T {
        for attempt in 0..<100 {
            try Task.checkCancellation()
            do { return try operation() }
            catch PairedBootstrapStoreError.busy {
                #if os(iOS)
                PairedDiagnosticStatus.record(.bootstrapStoreBusy)
                #endif
                guard attempt < 99 else { throw PairedBootstrapStoreError.busy }
                try await Task.sleep(nanoseconds: 20_000_000)
            }
        }
        throw PairedBootstrapStoreError.busy
    }
}

// Only signed, bounded metadata is shared. No notification body or private key.
struct PairedBootstrapStore {
    let directory: URL
    private func directoryFD(waitForCreation: Bool = false) throws -> Int32 {
        let fd = open(directory.path, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC)
        guard fd >= 0 else {
            if waitForCreation && errno == ENOENT { throw PairedBootstrapStoreError.notCreated }
            throw PairedOwnerError.unavailable
        }
        var info = stat()
        guard fstat(fd, &info) == 0, info.st_uid == geteuid(), (info.st_mode & 0o777) == 0o700 else {
            close(fd); throw PairedOwnerError.unavailable
        }
        return fd
    }
    private func locked<T>(write: Bool = true, waitForCreation: Bool = false, _ body: (Int32) throws -> T) throws -> T {
        let parent = try directoryFD(waitForCreation: waitForCreation); defer { close(parent) }
        let flags = (write ? O_RDWR | O_CREAT : O_RDONLY) | O_NOFOLLOW | O_CLOEXEC
        let lock = openat(parent, "bootstrap.lock", flags, 0o600)
        guard lock >= 0 else {
            if waitForCreation && errno == ENOENT { throw PairedBootstrapStoreError.notCreated }
            throw PairedOwnerError.unavailable
        }
        defer { close(lock) }
        var info = stat()
        guard fstat(lock, &info) == 0, (info.st_mode & S_IFMT) == S_IFREG,
              info.st_uid == geteuid(), (info.st_mode & 0o777) == 0o600 else { throw PairedOwnerError.unavailable }
        guard flock(lock, (write ? LOCK_EX : LOCK_SH) | LOCK_NB) == 0 else {
            if errno == EWOULDBLOCK { throw PairedBootstrapStoreError.busy }
            throw PairedOwnerError.unavailable
        }
        return try body(parent)
    }
    private func read<T: Decodable>(_ name: String, parent: Int32, as: T.Type) throws -> T? {
        let fd = openat(parent, name, O_RDONLY | O_NOFOLLOW | O_CLOEXEC)
        if fd < 0 {
            guard errno == ENOENT else { throw PairedOwnerError.unavailable }; return nil
        }
        defer { close(fd) }
        var info = stat()
        guard fstat(fd, &info) == 0, (info.st_mode & S_IFMT) == S_IFREG,
              info.st_uid == geteuid(), (info.st_mode & 0o777) == 0o600,
              info.st_size > 0, info.st_size <= 4096 else { throw PairedOwnerError.invalid }
        var data = Data(count: Int(info.st_size))
        let count = data.withUnsafeMutableBytes { Darwin.read(fd, $0.baseAddress, $0.count) }
        guard count == data.count else { throw PairedOwnerError.unavailable }
        return try JSONDecoder().decode(T.self, from: data)
    }
    private func write<T: Encodable>(_ value: T, name: String, parent: Int32) throws {
        let data = try JSONEncoder().encode(value)
        guard data.count <= 4096 else { throw PairedOwnerError.invalid }
        let temporary = ".bootstrap-" + UUID().uuidString
        let fd = openat(parent, temporary, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0o600)
        guard fd >= 0 else { throw PairedOwnerError.unavailable }
        defer { close(fd); _ = unlinkat(parent, temporary, 0) }
        var offset = 0
        while offset < data.count {
            let count = data.withUnsafeBytes { Darwin.write(fd, $0.baseAddress!.advanced(by: offset), data.count - offset) }
            if count < 0 && errno == EINTR { continue }
            guard count > 0 else { throw PairedOwnerError.unavailable }; offset += count
        }
        guard fsync(fd) == 0, renameat(parent, temporary, parent, name) == 0 else {
            throw PairedOwnerError.unavailable
        }
        guard fsync(parent) == 0 else { throw PairedPublicationError.uncertain }
    }
    private func captureName(_ source: PairedBootstrapCapture.Source, _ consent: PairedBootstrapConsent) -> String {
        "bootstrap-" + source.rawValue + "-" + consent.generation.uuidString + ".json"
    }
    func start(_ consent: PairedBootstrapConsent, watch: P256.Signing.PublicKey,
               owner: P256.Signing.PublicKey, now: UInt64, boot: UInt64) throws {
        try consent.validate(watch: watch, owner: owner, now: now, boot: boot)
        try locked { parent in
            if let old = try read("bootstrap-consent.json", parent: parent, as: PairedBootstrapConsent.self) {
                // A corrupt consent cannot be silently repaired; a valid expired
                // consent may be replaced after a fresh physical confirmation.
                try old.validate(watch: watch, owner: owner, now: old.issuedMillis, boot: old.bootMillis)
                guard now >= old.expiresMillis else { throw PairedOwnerError.unavailable }
                guard unlinkat(parent, proposalName(old), 0) == 0 || errno == ENOENT else {
                    throw PairedOwnerError.unavailable
                }
                for source in [PairedBootstrapCapture.Source.provider, .security] {
                    guard unlinkat(parent, captureName(source, old), 0) == 0 || errno == ENOENT else {
                        throw PairedOwnerError.unavailable
                    }
                }
                guard unlinkat(parent, "bootstrap-ack-" + old.generation.uuidString + ".json", 0) == 0 || errno == ENOENT else {
                    throw PairedOwnerError.unavailable
                }
            }
            let context = PairedBootstrapPublicContext(consent: consent,
                watchPublic: watch.x963Representation, ownerPublic: owner.x963Representation)
            try write(context, name: "bootstrap-public-context.json", parent: parent)
            try write(consent, name: "bootstrap-consent.json", parent: parent)
        }
    }
    func consent(watch: P256.Signing.PublicKey, owner: P256.Signing.PublicKey,
                 now: UInt64, boot: UInt64) throws -> PairedBootstrapConsent {
        try locked { parent in
            guard let value = try read("bootstrap-consent.json", parent: parent, as: PairedBootstrapConsent.self) else {
                throw PairedOwnerError.unavailable
            }
            try value.validate(watch: watch, owner: owner, now: now, boot: boot); return value
        }
    }
    func capture(_ value: PairedBootstrapCapture, consent: PairedBootstrapConsent,
                 watch: P256.Signing.PublicKey, owner: P256.Signing.PublicKey,
                 now: UInt64, boot: UInt64) throws {
        try locked { parent in
            guard let current = try read("bootstrap-consent.json", parent: parent, as: PairedBootstrapConsent.self),
                  current.generation == consent.generation else { throw PairedOwnerError.stale }
            try current.validate(watch: watch, owner: owner, now: now, boot: boot)
            try value.validate(consent: current, owner: owner)
            let name = captureName(value.source, current)
            if let _ = try read(name, parent: parent, as: PairedBootstrapCapture.self) {
                throw PairedOwnerError.stale
            }
            try write(value, name: name, parent: parent)
        }
    }
    func captured(_ source: PairedBootstrapCapture.Source, consent: PairedBootstrapConsent,
                  watch: P256.Signing.PublicKey, owner: P256.Signing.PublicKey,
                  now: UInt64, boot: UInt64) throws -> PairedBootstrapCapture? {
        try locked { parent in
            guard let current = try read("bootstrap-consent.json", parent: parent, as: PairedBootstrapConsent.self),
                  current.generation == consent.generation else { throw PairedOwnerError.stale }
            try current.validate(watch: watch, owner: owner, now: now, boot: boot)
            guard let value = try read(captureName(source, current), parent: parent, as: PairedBootstrapCapture.self) else {
                return nil
            }
            try value.validate(consent: current, owner: owner); return value
        }
    }
    func cancel(_ consent: PairedBootstrapConsent, watch: P256.Signing.PublicKey,
                owner: P256.Signing.PublicKey) throws {
        try locked { parent in
            guard let current = try read("bootstrap-consent.json", parent: parent, as: PairedBootstrapConsent.self),
                  current.generation == consent.generation else { throw PairedOwnerError.stale }
            try current.validate(watch: watch, owner: owner, now: current.issuedMillis, boot: current.bootMillis)
            for source in [PairedBootstrapCapture.Source.provider, .security] {
                guard unlinkat(parent, captureName(source, current), 0) == 0 || errno == ENOENT else {
                    throw PairedOwnerError.unavailable
                }
            }
            guard unlinkat(parent, "bootstrap-ack-" + current.generation.uuidString + ".json", 0) == 0 || errno == ENOENT else {
                throw PairedOwnerError.unavailable
            }
            for name in ["bootstrap-public-context.json", proposalName(current)] {
                guard unlinkat(parent, name, 0) == 0 || errno == ENOENT else { throw PairedOwnerError.unavailable }
            }
            guard unlinkat(parent, "bootstrap-consent.json", 0) == 0 else { throw PairedOwnerError.unavailable }
            guard fsync(parent) == 0 else { throw PairedPublicationError.uncertain }
        }
    }
    func watchReceipt(_ consent: PairedBootstrapConsent, capture: PairedBootstrapCapture,
                      watch: P256.Signing.PublicKey, owner: P256.Signing.PublicKey,
                      now: UInt64, boot: UInt64) throws -> Data? {
        try locked { parent in
            guard let current = try read("bootstrap-consent.json", parent: parent, as: PairedBootstrapConsent.self),
                  current.generation == consent.generation else { throw PairedOwnerError.stale }
            try current.validate(watch: watch, owner: owner, now: now, boot: boot)
            guard let data = try read("bootstrap-ack-" + current.generation.uuidString + ".json", parent: parent, as: Data.self) else {
                return nil
            }
            try PairedBootstrapWire.validateReceipt(data, consent: current, capture: capture, watch: watch, owner: owner)
            return data
        }
    }
    func recordWatchReceipt(_ data: Data, consent: PairedBootstrapConsent, capture: PairedBootstrapCapture,
                            watch: P256.Signing.PublicKey, owner: P256.Signing.PublicKey,
                            now: UInt64, boot: UInt64) throws {
        try locked { parent in
            guard let current = try read("bootstrap-consent.json", parent: parent, as: PairedBootstrapConsent.self),
                  current.generation == consent.generation else { throw PairedOwnerError.stale }
            try current.validate(watch: watch, owner: owner, now: now, boot: boot)
            try PairedBootstrapWire.validateReceipt(data, consent: current, capture: capture, watch: watch, owner: owner)
            let name = "bootstrap-ack-" + current.generation.uuidString + ".json"
            if let previous = try read(name, parent: parent, as: Data.self) {
                guard previous == data else { throw PairedOwnerError.stale }; return
            }
            try write(data, name: name, parent: parent)
        }
    }


    private func proposalName(_ consent: PairedBootstrapConsent) -> String {
        "bootstrap-proposal-" + consent.generation.uuidString + ".json"
    }
    // Pre-bind only. Waiting never supplies authority or changes an existing
    // session's generation. Validate signatures and pins before treating an old
    // consent as expired; malformed/denied/inconsistent reads remain terminal.
    func contextForFirstBinding(now: UInt64, boot: UInt64) throws -> PairedBootstrapPublicContext? {
        do {
            return try locked(write: false, waitForCreation: true) { parent in
                guard let context = try read("bootstrap-public-context.json", parent: parent, as: PairedBootstrapPublicContext.self) else { return nil }
                guard context.consent.issuedMillis <= now else { throw PairedOwnerError.stale }
                let (watch, owner) = try context.validate(now: context.consent.issuedMillis, boot: boot)
                guard let current = try read("bootstrap-consent.json", parent: parent, as: PairedBootstrapConsent.self) else { return nil }
                guard current.generation == context.consent.generation,
                      current.signature == context.consent.signature else { throw PairedOwnerError.stale }
                try current.validate(watch: watch, owner: owner, now: current.issuedMillis, boot: boot)
                guard now < current.expiresMillis else { return nil }
                _ = try context.validate(now: now, boot: boot)
                return context
            }
        } catch PairedBootstrapStoreError.notCreated { return nil }
          catch PairedBootstrapStoreError.busy { return nil }
    }

    // Never opens or creates a writable lock on the extension's read path.
    func publicContext(now: UInt64, boot: UInt64) throws -> PairedBootstrapPublicContext {
        try locked(write: false) { parent in
            guard let context = try read("bootstrap-public-context.json", parent: parent, as: PairedBootstrapPublicContext.self),
                  let current = try read("bootstrap-consent.json", parent: parent, as: PairedBootstrapConsent.self),
                  current.generation == context.consent.generation,
                  current.signature == context.consent.signature else { throw PairedOwnerError.stale }
            let (watch, owner) = try context.validate(now: now, boot: boot)
            try current.validate(watch: watch, owner: owner, now: now, boot: boot)
            return context
        }
    }
    private func requireCurrent(_ consent: PairedBootstrapConsent, parent: Int32,
                                watch: P256.Signing.PublicKey, owner: P256.Signing.PublicKey,
                                now: UInt64, boot: UInt64) throws -> PairedBootstrapConsent {
        guard let current = try read("bootstrap-consent.json", parent: parent, as: PairedBootstrapConsent.self),
              current.generation == consent.generation,
              current.signature == consent.signature else { throw PairedOwnerError.stale }
        try current.validate(watch: watch, owner: owner, now: now, boot: boot)
        return current
    }
    func proposeSecurity(_ proposal: PairedBootstrapSecurityProposal, context: PairedBootstrapPublicContext,
                         now: UInt64, boot: UInt64) throws {
        let (watch, owner) = try context.validate(now: now, boot: boot)
        try locked { parent in
            let current = try requireCurrent(context.consent, parent: parent, watch: watch, owner: owner, now: now, boot: boot)
            try proposal.validate(consent: current)
            let name = proposalName(current)
            if let previous = try read(name, parent: parent, as: PairedBootstrapSecurityProposal.self) {
                guard previous == proposal else { throw PairedOwnerError.stale }; return
            }
            try write(proposal, name: name, parent: parent)
        }
    }
    // Main passes its Keychain identity; no private key is placed in this store.
    func adoptSecurityProposal(consent: PairedBootstrapConsent, watch: P256.Signing.PublicKey,
                               owner: P256.Signing.PrivateKey, now: UInt64, boot: UInt64) throws {
        try locked { parent in
            let current = try requireCurrent(consent, parent: parent, watch: watch, owner: owner.publicKey, now: now, boot: boot)
            guard let proposal = try read(proposalName(current), parent: parent, as: PairedBootstrapSecurityProposal.self) else { return }
            try proposal.validate(consent: current)
            let name = captureName(.security, current)
            if let existing = try read(name, parent: parent, as: PairedBootstrapCapture.self) {
                try existing.validate(consent: current, owner: owner.publicKey)
                guard proposal.matches(existing) else { throw PairedOwnerError.stale }; return
            }
            let capture = try PairedBootstrapCapture.security(identifier: proposal.identifier,
                encapsulated: proposal.encapsulated, consent: current, owner: owner)
            try write(capture, name: name, parent: parent)
        }
    }
    func securityReceipt(proposal: PairedBootstrapSecurityProposal, context: PairedBootstrapPublicContext,
                         now: UInt64, boot: UInt64) throws -> Data? {
        let (watch, owner) = try context.validate(now: now, boot: boot)
        return try locked(write: false) { parent in
            let current = try requireCurrent(context.consent, parent: parent, watch: watch, owner: owner, now: now, boot: boot)
            try proposal.validate(consent: current)
            guard let capture = try read(captureName(.security, current), parent: parent, as: PairedBootstrapCapture.self) else { return nil }
            try capture.validate(consent: current, owner: owner)
            guard proposal.matches(capture) else { throw PairedOwnerError.stale }
            guard let receipt = try read("bootstrap-ack-" + current.generation.uuidString + ".json", parent: parent, as: Data.self) else { return nil }
            try PairedBootstrapWire.validateReceipt(receipt, consent: current, capture: capture, watch: watch, owner: owner)
            return receipt
        }
    }
}
