import Foundation
import CryptoKit
import Darwin

// No notification/action text is stored. The digest detects conflicting
// reuses of a request; invocation intent is durable before the Apple effect.
struct PairedReceiptLedger: Sendable {
    enum Phase: String, Codable { case invoking, notSubmitted, responseAccepted, ackReady, completed }
    struct Entry: Codable, Sendable {
        let request: Data
        let sequence: UInt64
        let digest: Data
        var phase: Phase
        var ack: Data?
    }
    private struct Record: Codable {
        var version = 1
        let binding: Data
        var incoming: UInt64 = 0
        var outgoing: UInt64 = 0
        var invalidated = false
        var entries: [Entry] = []
    }
    private let directory: URL
    private let watchKey: Data
    private let ownerKey: Data
    private let nonce: Data
    private let feature: Data
    private let path: String
    private let binding: Data
    private static let maximumBytes = 65536

    init(directory: URL, watch: Data, owner: Data, nonce: Data, feature: Data, create: Bool) throws {
        guard directory.isFileURL, watch.count == 65, owner.count == 65,
              nonce.count == 32, !feature.isEmpty, feature.count <= 64 else { throw PairedLedgerError.binding }
        self.directory = directory; self.watchKey = watch; self.ownerKey = owner; self.nonce = nonce; self.feature = feature
        binding = Self.makeBinding(watch: watch, owner: owner, nonce: nonce, feature: feature)
        path = directory.appendingPathComponent("por-" + binding.map { String(format: "%02x", $0) }.joined() + ".json").path
        if create {
            let fd = Darwin.open(path, O_RDWR | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0o600)
            if fd >= 0 {
                defer { Darwin.close(fd) }
                guard flock(fd, LOCK_EX | LOCK_NB) == 0 else { throw PairedLedgerError.busy }
                try write(Record(binding: binding), fd)
                let parent = Darwin.open(directory.path, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC)
                guard parent >= 0 else { throw PairedLedgerError.unavailable }
                defer { Darwin.close(parent) }
                guard fsync(parent) == 0 else { throw PairedLedgerError.unavailable }
            } else if errno != EEXIST { throw PairedLedgerError.unavailable }
        }
        _ = try transaction(initializing: true) { _ in () }
    }
    private static func makeBinding(watch: Data, owner: Data, nonce: Data, feature: Data) -> Data {
        var bytes = Data("PebbleD provider receipt ledger v1".utf8)
        bytes.append(watch); bytes.append(owner); bytes.append(nonce)
        bytes.append(UInt8(feature.count)); bytes.append(feature)
        return Data(SHA256.hash(data: bytes))
    }
    func requireBinding(watch: Data, owner: Data, nonce: Data, feature: Data) throws {
        guard binding == Self.makeBinding(watch: watch, owner: owner, nonce: nonce, feature: feature) else {
            throw PairedLedgerError.binding
        }
    }
    func begin(_ reply: PairedOwnerEnvelope) throws -> (entry: Entry, claimed: Bool) {
        let digest = Data(SHA256.hash(data: reply.payload))
        return try transaction { record in
            if let entry = record.entries.first(where: { $0.request == reply.request }) {
                guard entry.sequence == reply.sequence, entry.digest == digest else { throw PairedOwnerError.stale }
                return (entry, false)
            }
            try Self.rejectHistoricalRequest(reply.request, directory: directory, excluding: URL(fileURLWithPath: path).lastPathComponent)
            guard reply.sequence > record.incoming, record.entries.count < 64,
                  !record.entries.contains(where: { $0.phase != .completed }) else { throw PairedOwnerError.pending }
            let entry = Entry(request: reply.request, sequence: reply.sequence, digest: digest, phase: .invoking)
            record.incoming = reply.sequence; record.entries.append(entry)
            return (entry, true)
        }
    }
    func resumeNotSubmitted(_ request: Data) throws {
        try update(request) { entry in
            guard entry.phase == .notSubmitted else { throw PairedOwnerError.ambiguousResponse }
            entry.phase = .invoking
        }
    }
    func responseAccepted(_ request: Data) throws {
        try update(request) { entry in
            guard entry.phase == .invoking else { throw PairedOwnerError.stale }
            entry.phase = .responseAccepted
        }
    }
    func notSubmitted(_ request: Data) throws {
        try update(request) { entry in
            guard entry.phase == .invoking else { throw PairedOwnerError.stale }
            entry.phase = .notSubmitted
        }
    }
    func pending() throws -> Entry? {
        try transaction { $0.entries.first { $0.phase != .completed } }
    }
    func prepareACK(_ request: Data, sign: (UInt64) throws -> Data) throws -> Data {
        try transaction { record in
            guard let index = record.entries.firstIndex(where: { $0.request == request }) else { throw PairedOwnerError.stale }
            if let ack = record.entries[index].ack { return ack }
            guard record.entries[index].phase == .responseAccepted, record.outgoing < UInt64.max else {
                throw PairedOwnerError.ambiguousResponse
            }
            let ack = try sign(record.outgoing + 1)
            guard ack.count == 128 else { throw PairedOwnerError.invalid }
            record.outgoing += 1; record.entries[index].ack = ack; record.entries[index].phase = .ackReady
            return ack
        }
    }
    func completed(_ request: Data) throws {
        try update(request) { entry in
            guard entry.phase == .ackReady || entry.phase == .completed else { throw PairedOwnerError.stale }
            entry.phase = .completed
        }
    }
    func invalidate() throws {
        let publicationLock = try PairedPublicationLock.acquire(directory: directory, exclusive: false)
        defer { publicationLock.release() }
        let marker = Darwin.open(path + ".revoked", O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0o600)
        if marker < 0 {
            guard errno == EEXIST else { throw PairedLedgerError.unavailable }
            return
        }
        defer { Darwin.close(marker) }
        guard fsync(marker) == 0 else { throw PairedLedgerError.unavailable }
        let parent = Darwin.open(URL(fileURLWithPath: path).deletingLastPathComponent().path,
                                 O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC)
        guard parent >= 0 else { throw PairedLedgerError.unavailable }
        defer { Darwin.close(parent) }
        guard fsync(parent) == 0 else { throw PairedLedgerError.unavailable }
    }
    func checkActive() throws { _ = try transaction { _ in () } }
    // Only inside PublicationStore.commitReadiness validation while its EX
    // publisher lock is held. It prevents a precheck-to-commit revocation race.
    // Never used to admit or perform an Apple effect.
    func checkActiveWhilePublisherLocked() throws { _ = try transaction(initializing: true) { _ in () } }
    private func requireNotRevoked() throws {
        var info = stat()
        guard lstat(path + ".revoked", &info) != 0, errno == ENOENT else { throw PairedLedgerError.unavailable }
    }
    private func update(_ request: Data, _ operation: (inout Entry) throws -> Void) throws {
        try transaction { record in
            guard let index = record.entries.firstIndex(where: { $0.request == request }) else { throw PairedOwnerError.stale }
            try operation(&record.entries[index])
        }
    }
    private func transaction<T>(initializing: Bool = false, _ operation: (inout Record) throws -> T) throws -> T {
        let publicationLock = initializing ? nil : try acquireEffectLease()
        defer { publicationLock?.release() }
        try requireNotRevoked()
        let fd = Darwin.open(path, O_RDWR | O_NOFOLLOW | O_CLOEXEC)
        guard fd >= 0 else { throw PairedLedgerError.unavailable }
        defer { Darwin.close(fd) }
        guard flock(fd, LOCK_EX | LOCK_NB) == 0 else { throw PairedLedgerError.busy }
        var info = stat()
        guard fstat(fd, &info) == 0, (info.st_mode & S_IFMT) == S_IFREG,
              info.st_uid == geteuid(), (info.st_mode & 0o777) == 0o600,
              info.st_size > 0, info.st_size <= Self.maximumBytes else { throw PairedLedgerError.corrupt }
        var bytes = [UInt8](repeating: 0, count: Int(info.st_size))
        let count = bytes.withUnsafeMutableBytes { pread(fd, $0.baseAddress, $0.count, 0) }
        guard count == bytes.count, var record = try? JSONDecoder().decode(Record.self, from: Data(bytes)),
              record.version == 1, record.binding == binding, record.entries.count <= 64,
              record.entries.allSatisfy({ $0.request.count == 16 && $0.digest.count == 32 && $0.sequence > 0 && ($0.ack == nil || $0.ack?.count == 128) }),
              Set(record.entries.map(\.request)).count == record.entries.count else { throw PairedLedgerError.corrupt }
        guard !record.invalidated else { throw PairedLedgerError.unavailable }
        let result = try operation(&record); try requireNotRevoked(); try write(record, fd); return result
    }
    // The token must span the actual awaited Apple response/ACK operation.
    // Completed duplicate ACKs also hold it, so rollover cannot race them.
    func acquireEffectLease() throws -> PairedPublicationLock {
        let lock = try PairedPublicationLock.acquire(directory: directory, exclusive: false)
        do {
            let bytes = try PairedPublicationLock.read(parent: lock.directoryFD, name: "active-session.json", maximum: 4096)!
            let publication = try JSONDecoder().decode(PairedSessionPublication.self, from: bytes)
            try publication.validateBinding(watch: P256.Signing.PublicKey(x963Representation: watchKey),
                owner: P256.Signing.PublicKey(x963Representation: ownerKey))
            guard publication.proof.nonce == nonce,
                  Data(publication.proof.sessionID.uuidString.utf8) == feature else { throw PairedOwnerError.stale }
            try requireNotRevoked()
            return lock
        } catch { lock.release(); throw error }
    }
    static func filename(watch: Data, owner: Data, nonce: Data, feature: Data) -> String {
        "por-" + makeBinding(watch: watch, owner: owner, nonce: nonce, feature: feature)
            .map { String(format: "%02x", $0) }.joined() + ".json"
    }
    // Called only while publisher.lock is held. Evidence reads ignore revocation
    // for inspection, never for invoking effects, and never rewrite old records.
    private static func history(directory: URL, excluding: String? = nil) throws -> [(String, Record)] {
        let names = try FileManager.default.contentsOfDirectory(atPath: directory.path).filter {
            $0 != excluding && $0.hasPrefix("por-") && $0.hasSuffix(".json")
        }.sorted()
        guard names.count <= 512 else { throw PairedLedgerError.exhausted }
        var result: [(String, Record)] = []
        for name in names {
            let hex = String(name.dropFirst(4).dropLast(5))
            guard hex.count == 64, hex.allSatisfy({ "0123456789abcdef".contains($0) }) else { throw PairedLedgerError.corrupt }
            let fd = Darwin.open(directory.appendingPathComponent(name).path, O_RDONLY | O_NOFOLLOW | O_CLOEXEC)
            guard fd >= 0 else { throw PairedLedgerError.unavailable }
            defer { Darwin.close(fd) }
            guard flock(fd, LOCK_SH | LOCK_NB) == 0 else { throw PairedLedgerError.busy }
            var info = stat()
            guard fstat(fd, &info) == 0, (info.st_mode & S_IFMT) == S_IFREG,
                  info.st_uid == geteuid(), (info.st_mode & 0o777) == 0o600,
                  info.st_size > 0, info.st_size <= maximumBytes else { throw PairedLedgerError.corrupt }
            var bytes = [UInt8](repeating: 0, count: Int(info.st_size))
            guard bytes.withUnsafeMutableBytes({ pread(fd, $0.baseAddress, $0.count, 0) }) == bytes.count,
                  let record = try? JSONDecoder().decode(Record.self, from: Data(bytes)),
                  record.version == 1, record.binding.count == 32,
                  record.binding.map({ String(format: "%02x", $0) }).joined() == hex,
                  record.entries.count <= 64,
                  record.entries.allSatisfy({ $0.request.count == 16 && $0.digest.count == 32 && $0.sequence > 0 &&
                    $0.sequence <= record.incoming && ($0.ack == nil || $0.ack?.count == 128) }),
                  Set(record.entries.map(\.request)).count == record.entries.count else { throw PairedLedgerError.corrupt }
            result.append((name, record))
        }
        return result
    }
    static func requireRolloverSafe(directory: URL, requiredLedger: String? = nil) throws {
        let records = try history(directory: directory)
        if let requiredLedger, !records.contains(where: { $0.0 == requiredLedger }) {
            throw PairedLedgerError.unavailable
        }
        guard records.allSatisfy({ $0.1.entries.allSatisfy({ $0.phase == .completed }) }) else {
            throw PairedPublicationError.replyReconciliationRequired
        }
    }
    private static func rejectHistoricalRequest(_ request: Data, directory: URL, excluding: String) throws {
        // Skip the current ledger: its existing entry is the single permitted
        // duplicate within a generation, with digest/sequence checked by begin.
        for (_, record) in try history(directory: directory, excluding: excluding) {
            guard !record.entries.contains(where: { $0.request == request }) else { throw PairedOwnerError.stale }
        }
    }
    static func sealPreviousGenerations(directory: URL, keeping: String) throws {
        try requireRolloverSafe(directory: directory)
        for (name, _) in try history(directory: directory) where name != keeping {
            let marker = directory.appendingPathComponent(name + ".revoked").path
            let fd = Darwin.open(marker, O_WRONLY | O_CREAT | O_NOFOLLOW | O_CLOEXEC, 0o600)
            guard fd >= 0 else { throw PairedLedgerError.unavailable }
            defer { Darwin.close(fd) }
            var info = stat()
            guard fstat(fd, &info) == 0, (info.st_mode & S_IFMT) == S_IFREG,
                  info.st_uid == geteuid(), (info.st_mode & 0o777) == 0o600,
                  info.st_size == 0, fsync(fd) == 0 else { throw PairedLedgerError.unavailable }
        }
        let fd = Darwin.open(directory.path, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC)
        guard fd >= 0 else { throw PairedLedgerError.unavailable }
        defer { Darwin.close(fd) }
        guard fsync(fd) == 0 else { throw PairedLedgerError.unavailable }
    }
    private func write(_ record: Record, _ fd: Int32) throws {
        let bytes = try JSONEncoder().encode(record)
        guard bytes.count <= Self.maximumBytes else { throw PairedLedgerError.corrupt }
        let count = bytes.withUnsafeBytes { pwrite(fd, $0.baseAddress, $0.count, 0) }
        guard count == bytes.count, ftruncate(fd, off_t(bytes.count)) == 0, fsync(fd) == 0 else { throw PairedLedgerError.unavailable }
    }
}
