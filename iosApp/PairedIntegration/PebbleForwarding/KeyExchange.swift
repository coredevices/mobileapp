#if os(iOS)
@unsafe @preconcurrency public import AccessoryTransportExtension
import CryptoKit
import Foundation
import PebbleProtocol

/// The watch owns the P-256 private key. Security owns the Apple callback and
/// persists its exact activation before sending it over the selected ASK link.
public enum KeyExchange {
    public static func accept(_ request: AccessorySecuritySession.Request)
        -> AccessorySecuritySession.Request.Decision {
        PairedDiagnosticStatus.record(.securityRequested)
        guard PairedNotificationGate.enabled else { return request.reject(error: .unsupported) }
        let handler = Handler()
        let decision = request.accept { handler }
        Task { @MainActor in
            do { try await handler.start(request.session) }
            catch {
                PairedDiagnosticStatus.record(.securityFailure, error: error)
                request.session.cancel(error: .unsupported)
            }
        }
        return decision
    }

    final class Handler: AccessorySecuritySession.EventHandler, Sendable {
        @MainActor private final class State {
            var link: WatchAccessoryLink?
            var record: DirectSecurityRecord?
            var invalidated = false
            var callbackInFlight = false
        }
        private let state = State()

        @MainActor func start(_ session: AccessorySecuritySession) async throws {
            guard !state.invalidated else { throw DirectSecurityError.invalid }
            let selected = try await WatchAccessoryLink.selectedAuthorizedPeripheral()
            guard !state.invalidated else { throw DirectSecurityError.invalid }
            let link = try WatchAccessoryLink.restored(for: nil, selectedPeripheral: selected)
            state.link = link
            let journal = try DirectSecurityJournal()
            if let previous = try journal.load() {
                if previous.selectedPeripheral != selected {
                    // ASK no longer authorizes the old peer. Its callback cannot
                    // be replayed to a newly selected watch.
                    try journal.clear(matching: previous)
                } else if let activation = previous.activation {
                    do {
                        try await DirectSecurityWire.deliverActivation(activation, record: previous, over: link)
                        try journal.clear(matching: previous)
                    } catch {
                        // A watch reboot loses the RAM-scoped activation. Only a
                        // signed identity from the same key and a new epoch may
                        // retire the old pending callback for this new session.
                        let identity = try await DirectSecurityWire.readIdentity(over: link)
                        guard identity.watchPublic == previous.watchPublic,
                              identity.epoch != previous.epoch else { throw error }
                        try journal.clear(matching: previous)
                    }
                } else {
                    do {
                        try await prepare(previous, over: link, session: session, journal: journal)
                        return
                    } catch DirectSecurityError.stale {
                        try journal.clear(matching: previous)
                    }
                }
            }
            let fresh = DirectSecurityRecord(selectedPeripheral: selected, exchangeID: UUID(),
                prepareReceiptID: UUID(), epoch: Data(), watchPublic: Data(), activation: nil)
            try await prepare(fresh, over: link, session: session, journal: journal)
        }

        @MainActor private func prepare(_ pending: DirectSecurityRecord, over link: WatchAccessoryLink,
                                        session: AccessorySecuritySession,
                                        journal: DirectSecurityJournal) async throws {
            let prepared = try await DirectSecurityWire.prepare(pending, over: link)
            guard !state.invalidated else { throw DirectSecurityError.invalid }
            if !pending.epoch.isEmpty {
                guard pending.epoch == prepared.epoch,
                      pending.watchPublic == prepared.watchPublic else { throw DirectSecurityError.stale }
            }
            try journal.save(prepared)
            try session.sendSecurityMessage(SecurityMessage(keyType: .publicKey, cipherSuite: .p256,
                version: .version1, key: prepared.watchPublic.dropFirst(), supportedTransports: [.bluetooth]))
            state.record = prepared
            PairedDiagnosticStatus.record(.securityPublicKeySent)
            PairedCapabilityProbe.emit(.securityPublicKeySubmitted)
        }

        func messageReceived(_ message: SecurityMessage,
                             completion: @escaping @Sendable (AccessoryMessage.Result) -> Void) {
            PairedCapabilityProbe.emit(.securityMaterialCallbackEntered)
            guard message.keyType == .encapsulatedKey,
                  case .p256 = message.cipherSuite, case .version1 = message.version,
                  let identifier = message.identifier else {
                completion(.failure(.transportUnavailable)); return
            }
            let material = Data(message.key)
            Task { @MainActor in
                guard !self.state.invalidated, !self.state.callbackInFlight,
                      let record = self.state.record, let link = self.state.link else {
                    completion(.failure(.transportUnavailable)); return
                }
                self.state.callbackInFlight = true
                defer { self.state.callbackInFlight = false }
                do {
                    let activation = try DirectSecurityWire.activation(identifier: identifier,
                        encapsulated: material, record: record, receiptID: record.prepareReceiptID)
                    let updated = record.withActivation(activation)
                    let journal = try DirectSecurityJournal()
                    try journal.save(updated) // durable before the first BLE write
                    self.state.record = updated
                    try await DirectSecurityWire.deliverActivation(activation, record: updated, over: link)
                    guard !self.state.invalidated else { throw DirectSecurityError.invalid }
                    try journal.clear(matching: updated)
                    self.state.record = nil
                    PairedDiagnosticStatus.record(.securityReceiptAccepted)
                    completion(.success)
                } catch {
                    PairedDiagnosticStatus.record(.securityFailure, error: error)
                    completion(.failure(.transportUnavailable))
                }
            }
        }

        func sessionInvalidated(error: AccessorySecuritySession.Error?) {
            PairedDiagnosticStatus.record(.securityInvalidated, error: error)
            Task { @MainActor in
                self.state.invalidated = true
                self.state.link?.retireDirect()
                self.state.link = nil
                self.state.record = nil
            }
        }
    }
}

private enum DirectSecurityError: Error { case invalid, stale, missing, oversized }

/// All fields are public callback material. The Security extension's own
/// Application Support directory is writable even when the App Group is not.
private struct DirectSecurityRecord: Codable {
    let selectedPeripheral: UUID
    let exchangeID: UUID
    let prepareReceiptID: UUID
    let epoch: Data
    let watchPublic: Data
    let activation: Data?

    func withActivation(_ data: Data) -> Self {
        Self(selectedPeripheral: selectedPeripheral, exchangeID: exchangeID,
             prepareReceiptID: prepareReceiptID, epoch: epoch,
             watchPublic: watchPublic, activation: data)
    }
    func validate() throws {
        guard epoch.count == 32, watchPublic.count == 65, watchPublic.first == 4,
              (activation == nil || (activation!.count >= 137 && activation!.count <= 200)) else {
            throw DirectSecurityError.invalid
        }
        _ = try P256.Signing.PublicKey(x963Representation: watchPublic)
    }
}

private struct DirectSecurityJournal {
    private let url: URL
    init() throws {
        guard let home = FileManager.default.urls(for: .applicationSupportDirectory,
                                                   in: .userDomainMask).first else {
            throw DirectSecurityError.missing
        }
        let directory = home.appendingPathComponent("WatchSecurityExchange", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        url = directory.appendingPathComponent("pending-v1.json")
    }
    func load() throws -> DirectSecurityRecord? {
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        let attributes = try FileManager.default.attributesOfItem(atPath: url.path)
        guard let size = attributes[.size] as? NSNumber, size.intValue <= 16_384 else {
            throw DirectSecurityError.oversized
        }
        let record = try JSONDecoder().decode(DirectSecurityRecord.self, from: Data(contentsOf: url))
        try record.validate()
        return record
    }
    func save(_ record: DirectSecurityRecord) throws {
        try record.validate()
        let bytes = try JSONEncoder().encode(record)
        guard bytes.count <= 16_384 else { throw DirectSecurityError.oversized }
        try bytes.write(to: url, options: .atomic)
        try FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
                                              ofItemAtPath: url.path)
    }
    func clear(matching expected: DirectSecurityRecord) throws {
        guard let current = try load(), current.exchangeID == expected.exchangeID,
              current.prepareReceiptID == expected.prepareReceiptID,
              current.epoch == expected.epoch, current.activation == expected.activation else {
            throw DirectSecurityError.stale
        }
        try FileManager.default.removeItem(at: url)
    }
}

private enum DirectSecurityWire {
    static func uuid(_ id: UUID) -> Data {
        let x = id.uuid
        return Data([x.0, x.1, x.2, x.3, x.4, x.5, x.6, x.7,
                     x.8, x.9, x.10, x.11, x.12, x.13, x.14, x.15])
    }

    @MainActor static func prepare(_ pending: DirectSecurityRecord,
                                   over link: WatchAccessoryLink) async throws -> DirectSecurityRecord {
        var request = Data("NPQ1".utf8)
        request.append(uuid(pending.exchangeID)); request.append(uuid(pending.prepareReceiptID))
        try await link.writeDirect([request], to: WatchAccessoryLink.prepareCharacteristic)
        let reply = try await link.readDirect(from: WatchAccessoryLink.prepareCharacteristic)
        guard reply.count == 197, reply.prefix(4) == Data("NPK1".utf8),
              reply.subdata(in: 36..<52) == uuid(pending.exchangeID),
              reply.subdata(in: 52..<68) == uuid(pending.prepareReceiptID),
              reply[68] == 4 else { throw DirectSecurityError.invalid }
        let identity = try verifiedIdentity(reply)
        return DirectSecurityRecord(selectedPeripheral: pending.selectedPeripheral,
            exchangeID: pending.exchangeID, prepareReceiptID: pending.prepareReceiptID,
            epoch: identity.epoch, watchPublic: identity.watchPublic, activation: nil)
    }

    @MainActor static func readIdentity(over link: WatchAccessoryLink) async throws
        -> (epoch: Data, watchPublic: Data) {
        try await link.waitForDirectService()
        let reply = try await link.readDirect(from: WatchAccessoryLink.prepareCharacteristic)
        return try verifiedIdentity(reply)
    }

    private static func verifiedIdentity(_ reply: Data) throws
        -> (epoch: Data, watchPublic: Data) {
        guard reply.count == 197, reply.prefix(4) == Data("NPK1".utf8), reply[68] == 4 else {
            throw DirectSecurityError.invalid
        }
        let publicKey = reply.subdata(in: 68..<133)
        let signing = try P256.Signing.PublicKey(x963Representation: publicKey)
        let signature = try P256.Signing.ECDSASignature(rawRepresentation: reply.suffix(64))
        guard signing.isValidSignature(signature, for: reply.prefix(133)) else {
            throw DirectSecurityError.invalid
        }
        return (reply.subdata(in: 4..<36), publicKey)
    }

    static func activation(identifier: String, encapsulated: Data,
                           record: DirectSecurityRecord, receiptID: UUID) throws -> Data {
        let name = Data(identifier.utf8)
        guard (1...64).contains(name.count), !name.contains(0), encapsulated.count == 65,
              encapsulated.first == 4 else { throw DirectSecurityError.invalid }
        _ = try P256.KeyAgreement.PublicKey(x963Representation: encapsulated)
        var packet = Data("NKA1".utf8)
        packet.append(1); packet.append(1)
        packet.append(record.epoch); packet.append(uuid(record.exchangeID)); packet.append(uuid(receiptID))
        packet.append(UInt8(name.count)); packet.append(name); packet.append(encapsulated)
        return packet
    }

    @MainActor static func deliverActivation(_ packet: Data, record: DirectSecurityRecord,
                                              over link: WatchAccessoryLink) async throws {
        try record.validate()
        guard packet.count >= 137, packet.count <= 200,
              packet.prefix(4) == Data("NKA1".utf8), packet[4] == 1, packet[5] == 1,
              packet.subdata(in: 6..<38) == record.epoch,
              packet.subdata(in: 38..<54) == uuid(record.exchangeID) else {
            throw DirectSecurityError.invalid
        }
        let receiptID = packet.subdata(in: 54..<70)
        for attempt in 0..<3 {
            do {
                try await link.writeFramedDirect(packet, transferID: receiptID,
                                                to: WatchAccessoryLink.activateCharacteristic)
                let receipt = try await link.readDirect(from: WatchAccessoryLink.activateCharacteristic)
                try verifyReceipt(receipt, magic: "NKR1", record: record,
                                  receiptID: receiptID, packet: packet)
                return
            } catch {
                if attempt == 2 { throw error }
                try await Task.sleep(for: .milliseconds(300))
            }
        }
    }

    static func verifyReceipt(_ receipt: Data, magic: String,
                              record: DirectSecurityRecord, receiptID: Data,
                              packet: Data) throws {
        guard receipt.count == 164, receipt.prefix(4) == Data(magic.utf8),
              receipt.subdata(in: 4..<36) == record.epoch,
              receipt.subdata(in: 36..<52) == uuid(record.exchangeID),
              receipt.subdata(in: 52..<68) == receiptID,
              receipt.subdata(in: 68..<100) == Data(SHA256.hash(data: packet)) else {
            throw DirectSecurityError.invalid
        }
        let key = try P256.Signing.PublicKey(x963Representation: record.watchPublic)
        let signature = try P256.Signing.ECDSASignature(rawRepresentation: receipt.suffix(64))
        guard key.isValidSignature(signature, for: receipt.prefix(100)) else {
            throw DirectSecurityError.invalid
        }
    }
}
#endif
