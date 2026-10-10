import Foundation
import CryptoKit
import Darwin

enum PairedLedgerError: Error { case unavailable, busy, corrupt, binding, exhausted }

// Explicitly injected private container; this never resolves an App Group,
// creates a key, or replaces a session. Contains counters/public binding only.
// LOCK_NB bounds contention. Corrupt, missing or mismatched state fails closed.
struct PairedSessionLedger: Sendable {
    private struct Record: Codable {
        var version = 1
        let binding: Data
        var outbound: UInt64 = 0
        var inbound: UInt64 = 0
        var invalidated = false
    }
    private let path: String
    private let binding: Data
    private static let maximumBytes = 1024

    init(directory: URL, watch: Data, owner: Data, nonce: Data, feature: Data,
         create: Bool) throws {
        guard directory.isFileURL, watch.count == 65, owner.count == 65,
              nonce.count == 32, !feature.isEmpty, feature.count <= 64 else {
            throw PairedLedgerError.binding
        }
        binding = Self.sessionBinding(watch: watch, owner: owner, nonce: nonce, feature: feature)
        let name = binding.map { String(format: "%02x", $0) }.joined()
        path = directory.appendingPathComponent("pot-" + name + ".json").path
        if create {
            let fd = Darwin.open(path, O_RDWR | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0o600)
            if fd >= 0 {
                defer { Darwin.close(fd) }
                guard flock(fd, LOCK_EX | LOCK_NB) == 0 else { throw PairedLedgerError.busy }
                try write(Record(binding: binding), to: fd)
                let directoryFD = Darwin.open(directory.path, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC)
                guard directoryFD >= 0 else { throw PairedLedgerError.unavailable }
                defer { Darwin.close(directoryFD) }
                guard fsync(directoryFD) == 0 else { throw PairedLedgerError.unavailable }
            } else if errno != EEXIST { throw PairedLedgerError.unavailable }
        }
        _ = try transaction { _ in () }
    }

    private static func sessionBinding(watch: Data, owner: Data, nonce: Data, feature: Data) -> Data {
        var transcript = Data("PebbleD outer session ledger v1".utf8)
        transcript.append(watch); transcript.append(owner); transcript.append(nonce)
        transcript.append(UInt8(feature.count)); transcript.append(feature)
        return Data(SHA256.hash(data: transcript))
    }

    func requireBinding(watch: Data, owner: Data, nonce: Data, feature: Data) throws {
        guard binding == Self.sessionBinding(watch: watch, owner: owner, nonce: nonce, feature: feature) else {
            throw PairedLedgerError.binding
        }
    }

    func checkActive() throws { _ = try transaction { _ in () } }

    func nextOutbound() throws -> UInt64 {
        try transaction { record in
            guard record.outbound < UInt64.max else { throw PairedLedgerError.exhausted }
            record.outbound += 1
            return record.outbound
        }
    }

    func acceptInbound(_ sequence: UInt64) throws {
        try transaction { record in
            guard sequence > record.inbound else { throw PairedOwnerError.stale }
            record.inbound = sequence
        }
    }

    func invalidate() throws {
        try transaction { $0.invalidated = true }
    }

    private func transaction<T>(_ operation: (inout Record) throws -> T) throws -> T {
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
        guard count == bytes.count,
              var record = try? JSONDecoder().decode(Record.self, from: Data(bytes)),
              record.version == 1, record.binding == binding else { throw PairedLedgerError.corrupt }
        guard !record.invalidated else { throw PairedLedgerError.unavailable }
        let result = try operation(&record)
        try write(record, to: fd)
        return result
    }

    private func write(_ record: Record, to fd: Int32) throws {
        let bytes = try JSONEncoder().encode(record)
        guard bytes.count <= Self.maximumBytes else { throw PairedLedgerError.corrupt }
        let count = bytes.withUnsafeBytes { pwrite(fd, $0.baseAddress, $0.count, 0) }
        guard count == bytes.count, ftruncate(fd, off_t(bytes.count)) == 0,
              fsync(fd) == 0 else { throw PairedLedgerError.unavailable }
    }
}
