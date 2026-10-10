import Foundation
import CryptoKit
import Darwin

// Shared leases span real effect awaits; exclusive leases serialize publication
// and readiness mutation. Nonblocking contention fails closed. Lock order is
// publisher.lock, then receipt file. Process death releases locks, not uncertainty.
final class PairedPublicationLock: @unchecked Sendable {
    let directoryFD: Int32
    private var lockFD: Int32
    private let mutex = NSLock()
    private init(directoryFD: Int32, lockFD: Int32) { self.directoryFD = directoryFD; self.lockFD = lockFD }
    static func acquire(directory: URL, exclusive: Bool) throws -> PairedPublicationLock {
        guard directory.isFileURL else { throw PairedPublicationError.unavailable }
        let parent = Darwin.open(directory.path, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC)
        guard parent >= 0 else { throw PairedPublicationError.unavailable }
        var info = stat()
        guard fstat(parent, &info) == 0, info.st_uid == geteuid(), (info.st_mode & 0o777) == 0o700 else {
            Darwin.close(parent); throw PairedPublicationError.unavailable
        }
        let fd = openat(parent, "publisher.lock", O_RDWR | O_CREAT | O_NOFOLLOW | O_CLOEXEC, 0o600)
        guard fd >= 0 else { Darwin.close(parent); throw PairedPublicationError.unavailable }
        guard fstat(fd, &info) == 0, (info.st_mode & S_IFMT) == S_IFREG,
              info.st_uid == geteuid(), (info.st_mode & 0o777) == 0o600,
              flock(fd, (exclusive ? LOCK_EX : LOCK_SH) | LOCK_NB) == 0 else {
            Darwin.close(fd); Darwin.close(parent); throw PairedPublicationError.unavailable
        }
        return PairedPublicationLock(directoryFD: parent, lockFD: fd)
    }
    func release() {
        mutex.lock(); defer { mutex.unlock() }
        guard lockFD >= 0 else { return }
        Darwin.close(lockFD); Darwin.close(directoryFD); lockFD = -1
    }
    deinit { release() }
    static func read(parent: Int32, name: String, maximum: Int, required: Bool = true) throws -> Data? {
        let fd = openat(parent, name, O_RDONLY | O_NOFOLLOW | O_CLOEXEC)
        if fd < 0 {
            if !required && errno == ENOENT { return nil }
            throw PairedPublicationError.unavailable
        }
        defer { Darwin.close(fd) }
        var info = stat()
        guard fstat(fd, &info) == 0, (info.st_mode & S_IFMT) == S_IFREG,
              info.st_uid == geteuid(), (info.st_mode & 0o777) == 0o600,
              info.st_size > 0, info.st_size <= maximum else { throw PairedPublicationError.unavailable }
        var bytes = [UInt8](repeating: 0, count: Int(info.st_size))
        var offset = 0
        while offset < bytes.count {
            let remaining = bytes.count - offset
            let count = bytes.withUnsafeMutableBytes { pread(fd, $0.baseAddress!.advanced(by: offset), remaining, off_t(offset)) }
            if count < 0 && errno == EINTR { continue }
            guard count > 0 else { throw PairedPublicationError.unavailable }
            offset += count
        }
        return Data(bytes)
    }
}

// Exact raw publication comparison prevents stale readiness from qualifying or
// erasing a newer generation. Caller validates owner-signed readiness/context.
struct PairedPublicationStore {
    private let directory: URL
    init(directory: URL) throws {
        self.directory = directory
        let lock = try PairedPublicationLock.acquire(directory: directory, exclusive: false)
        lock.release()
    }
    func readPublicationBytes() throws -> Data {
        let lock = try PairedPublicationLock.acquire(directory: directory, exclusive: false); defer { lock.release() }
        return try PairedPublicationLock.read(parent: lock.directoryFD, name: "active-session.json", maximum: 4096)!
    }
    private func requireExpected(_ expected: Data, parent: Int32) throws {
        guard !expected.isEmpty, expected.count <= 4096,
              try PairedPublicationLock.read(parent: parent, name: "active-session.json", maximum: 4096) == expected else {
            throw PairedPublicationError.binding
        }
    }
    func readReadiness(expectedPublicationBytes: Data) throws -> Data {
        let lock = try PairedPublicationLock.acquire(directory: directory, exclusive: false); defer { lock.release() }
        try requireExpected(expectedPublicationBytes, parent: lock.directoryFD)
        return try PairedPublicationLock.read(parent: lock.directoryFD, name: "session-ready.json", maximum: 4096)!
    }
    func commitReadiness(expectedPublicationBytes: Data, readinessBytes: Data,
                         validate: () throws -> Void) throws {
        let lock = try PairedPublicationLock.acquire(directory: directory, exclusive: true); defer { lock.release() }
        try requireExpected(expectedPublicationBytes, parent: lock.directoryFD)
        try validate()
        try atomicWrite(readinessBytes, name: "session-ready.json", parent: lock.directoryFD,
                        directorySync: { Darwin.fsync($0) }, beforeRename: {
            try requireExpected(expectedPublicationBytes, parent: lock.directoryFD); try validate()
        })
    }
    func clearReadiness(expectedPublicationBytes: Data) throws {
        // Shared invalidation can revoke readiness while an effect lease is held;
        // exclusive publication/ready commits still cannot race this unlink.
        let lock = try PairedPublicationLock.acquire(directory: directory, exclusive: false); defer { lock.release() }
        try requireExpected(expectedPublicationBytes, parent: lock.directoryFD)
        try removeReadiness(parent: lock.directoryFD)
    }
    private func removeReadiness(parent: Int32) throws {
        if unlinkat(parent, "session-ready.json", 0) != 0 && errno != ENOENT { throw PairedPublicationError.unavailable }
        guard fsync(parent) == 0 else { throw PairedPublicationError.uncertain }
    }
    func publish(_ publication: PairedSessionPublication, watch: P256.Signing.PublicKey,
                 owner: P256.Signing.PrivateKey, prepareLedgers: () throws -> Void,
                 directorySync: (Int32) -> Int32 = { Darwin.fsync($0) }) throws {
        try publication.validateBinding(watch: watch, owner: owner.publicKey)
        let lock = try PairedPublicationLock.acquire(directory: directory, exclusive: true); defer { lock.release() }
        let parent = lock.directoryFD
        var requiredPreviousLedger: String?
        if let previous = try PairedPublicationLock.read(parent: parent, name: "active-session.json", maximum: 4096, required: false) {
            let oldProof: PairedSessionProof
            if let old = try? JSONDecoder().decode(PairedSessionPublication.self, from: previous) {
                try old.validateBinding(watch: watch, owner: owner.publicKey); oldProof = old.proof
            } else {
                let old = try JSONDecoder().decode(PairedSessionProof.self, from: previous)
                let signature = try P256.Signing.ECDSASignature(rawRepresentation: old.signature)
                guard watch.isValidSignature(signature, for: try old.transcript(watch: watch.x963Representation,
                    owner: owner.publicKey.x963Representation)) else { throw PairedPublicationError.unavailable }
                oldProof = old
            }
            guard oldProof.nonce != publication.proof.nonce else { throw PairedPublicationError.binding }
            requiredPreviousLedger = PairedReceiptLedger.filename(watch: watch.x963Representation,
                owner: owner.publicKey.x963Representation, nonce: oldProof.nonce,
                feature: Data(oldProof.sessionID.uuidString.utf8))
        }
        // All histories, including revoked generations, remain evidence. Pending
        // or ambiguous effects prohibit replacing the proof/counters entirely.
        try PairedReceiptLedger.requireRolloverSafe(directory: directory, requiredLedger: requiredPreviousLedger)
        try prepareLedgers() // initializer never reacquires publisher.lock
        let keep = PairedReceiptLedger.filename(watch: watch.x963Representation,
            owner: owner.publicKey.x963Representation, nonce: publication.proof.nonce,
            feature: Data(publication.proof.sessionID.uuidString.utf8))
        try PairedReceiptLedger.sealPreviousGenerations(directory: directory, keeping: keep)
        try removeReadiness(parent: parent)
        try atomicWrite(JSONEncoder().encode(publication), name: "active-session.json", parent: parent,
                        directorySync: directorySync, beforeRename: {})
    }
    private func atomicWrite(_ bytes: Data, name: String, parent: Int32,
                             directorySync: (Int32) -> Int32, beforeRename: () throws -> Void) throws {
        guard !bytes.isEmpty, bytes.count <= 4096 else { throw PairedPublicationError.binding }
        let temporary = "." + name + "-" + UUID().uuidString
        let fd = openat(parent, temporary, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0o600)
        guard fd >= 0 else { throw PairedPublicationError.unavailable }
        defer { Darwin.close(fd); _ = unlinkat(parent, temporary, 0) }
        var offset = 0
        while offset < bytes.count {
            let written = bytes.withUnsafeBytes { write(fd, $0.baseAddress!.advanced(by: offset), bytes.count - offset) }
            if written < 0 && errno == EINTR { continue }
            guard written > 0 else { throw PairedPublicationError.unavailable }
            offset += written
        }
        guard fsync(fd) == 0 else { throw PairedPublicationError.unavailable }
        try beforeRename()
        guard renameat(parent, temporary, parent, name) == 0 else { throw PairedPublicationError.unavailable }
        guard directorySync(parent) == 0 else { throw PairedPublicationError.uncertain }
    }
}
