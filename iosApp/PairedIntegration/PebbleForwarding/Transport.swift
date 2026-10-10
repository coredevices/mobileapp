#if os(iOS)
@unsafe @preconcurrency public import AccessoryTransportExtension
import CryptoKit
import Foundation
import PebbleProtocol

/// Apple's encrypted transport bytes go directly to the watch. A signed watch
/// acceptance receipt, rather than an ATT acknowledgement, completes the call.
public enum Transport {
    public static func accept(_ request: AccessoryTransportSession.Request)
        -> AccessoryTransportSession.Request.Decision {
        PairedDiagnosticStatus.record(.transportRequested)
        guard PairedNotificationGate.enabled else { return request.reject(error: .unsupported) }
        request.session.transport = .bluetooth
        let handler = Handler()
        let decision = request.accept { handler }
        PairedDiagnosticStatus.record(.transportAccepted)
        Task { @MainActor in
            do { try await handler.start(request.session) }
            catch { PairedDiagnosticStatus.record(.transportFailure, error: error) }
        }
        return decision
    }

    final class Handler: AccessoryTransportSession.EventHandler, Sendable {
        private struct PendingMessage: Sendable {
            let payload: Data
            let sessionID: UUID
            let completion: @Sendable (AccessoryMessage.Result) -> Void
        }
        @MainActor private final class State {
            var link: WatchAccessoryLink?
            var callbackToken: UUID?
            var session: AccessoryTransportSession?
            var publicKey: Data?
            var epoch: Data?
            var selected: UUID?
            var starting = false
            var refreshing = false
            var sending = false
            var pending: PairedFIFO<PendingMessage> = []
            var invalidated = false
            var reverse = DirectReverseReassembler()
            var acknowledged: [Data: Data] = [:]
            var reverseInFlight: Set<Data> = []
            nonisolated init() {}
        }
        private let state = State()

        @MainActor func start(_ session: AccessoryTransportSession) async throws {
            guard !state.invalidated else { throw DirectTransportError.unavailable }
            if state.link != nil { return }
            guard !state.starting else { return }
            state.starting = true
            defer { state.starting = false }
            let selected = try await WatchAccessoryLink.selectedAuthorizedPeripheral()
            guard !state.invalidated else { throw DirectTransportError.unavailable }
            let link = try WatchAccessoryLink.restored(for: session.transportStateRestoreIdentifier,
                                                       selectedPeripheral: selected)
            state.selected = selected
            state.session = session
            state.callbackToken = link.attachCallbacks(notification: { [weak self] frame in
                self?.receiveReverse(Data(frame))
            }, dropped: { [weak self] in
                self?.state.epoch = nil
                self?.state.reverse.reset()
            })
            state.link = link
            try await link.waitForDirectService()
            try await refreshIdentity(over: link)
        }

        @MainActor private func ready(until deadline: TimeInterval) async throws
            -> (WatchAccessoryLink, Data, Data) {
            var lastError: any Error = DirectTransportError.unavailable
            while ProcessInfo.processInfo.systemUptime < deadline {
                guard !state.invalidated else { throw DirectTransportError.unavailable }
                if let link = state.link {
                    if state.epoch == nil {
                        if state.starting || state.refreshing {
                            try await Task.sleep(for: .milliseconds(250))
                            continue
                        }
                        do { try await refreshIdentity(over: link) }
                        catch { lastError = error }
                    }
                    if let epoch = state.epoch, let publicKey = state.publicKey {
                        return (link, epoch, publicKey)
                    }
                }
                try await Task.sleep(for: .milliseconds(250))
            }
            throw lastError
        }

        @MainActor private func refreshIdentity(over link: WatchAccessoryLink) async throws {
            guard !state.refreshing else { throw DirectTransportError.unavailable }
            state.refreshing = true
            defer { state.refreshing = false }
            let value = try await link.readDirect(from: WatchAccessoryLink.prepareCharacteristic)
            guard value.count == 197, value.prefix(4) == Data("NPK1".utf8), value[68] == 4 else {
                throw DirectTransportError.invalid
            }
            let keyData = value.subdata(in: 68..<133)
            let signing = try P256.Signing.PublicKey(x963Representation: keyData)
            let signature = try P256.Signing.ECDSASignature(rawRepresentation: value.suffix(64))
            guard signing.isValidSignature(signature, for: value.prefix(133)),
                  state.publicKey == nil || state.publicKey == keyData else {
                throw DirectTransportError.invalid
            }
            state.publicKey = keyData
            let activation: Data
            do { activation = try await link.readDirect(from: WatchAccessoryLink.activateCharacteristic) }
            catch { throw DirectTransportError.notReady }
            guard activation.count == 164,
                  activation.prefix(4) == Data("NKR1".utf8),
                  activation.subdata(in: 4..<36) == value.subdata(in: 4..<36),
                  activation.subdata(in: 36..<52) == value.subdata(in: 36..<52) else {
                throw DirectTransportError.notReady
            }
            let activeSignature = try P256.Signing.ECDSASignature(rawRepresentation: activation.suffix(64))
            guard signing.isValidSignature(activeSignature, for: activation.prefix(100)) else {
                throw DirectTransportError.invalid
            }
            state.epoch = value.subdata(in: 4..<36)
        }

        func messageReceived(_ message: TransportMessage,
                             completion: @escaping @Sendable (AccessoryMessage.Result) -> Void) {
            PairedDiagnosticStatus.record(.transportMessageReceived)
            let payload = Data(message.data)
            let sessionID = message.sessionID
            guard (1...2213).contains(payload.count) else {
                completion(.failure(.transportUnavailable)); return
            }
            Task { @MainActor in
                // Session.send admits work before Apple's transport invokes us.
                // Serialize whole transfers here, rather than rejecting a burst
                // while its first encrypted message is still on the radio.
                guard !self.state.invalidated, self.state.pending.count < 128 else {
                    completion(.failure(.transportUnavailable)); return
                }
                self.state.pending.append(PendingMessage(payload: payload, sessionID: sessionID,
                                                         completion: completion))
                guard !self.state.sending else { return }
                self.state.sending = true
                await self.drainMessages()
            }
        }

        @MainActor private func drainMessages() async {
            defer { state.sending = false }
            while !state.invalidated, let message = state.pending.popFirst() {
                await sendMessage(message)
            }
        }

        @MainActor private func sendMessage(_ message: PendingMessage) async {
            let payload = message.payload
            let sessionID = message.sessionID
            let completion = message.completion
            // Queueing consumes no transfer deadline. Each admitted message gets
            // the existing bounded retry only when it reaches the radio.
            let deadline = ProcessInfo.processInfo.systemUptime + 25
            let receiptID = UUID()
            var lastError: any Error = DirectTransportError.unavailable
            while !state.invalidated, ProcessInfo.processInfo.systemUptime < deadline {
                do {
                    let (link, epoch, publicKey) = try await ready(until: deadline)
                    let packet = try DirectTransportWire.packet(epoch: epoch, receiptID: receiptID,
                                                                 sessionID: sessionID, data: payload)
                    let receiptIDBytes = DirectTransportWire.uuid(receiptID)
                    try await link.writeFramedDirect(packet, transferID: receiptIDBytes,
                                                    to: WatchAccessoryLink.directTransportCharacteristic)
                    let receipt = try await link.readDirect(from: WatchAccessoryLink.directTransportCharacteristic)
                    try DirectTransportWire.verify(receipt: receipt, epoch: epoch,
                        receiptID: receiptIDBytes, packet: packet, publicKey: publicKey)
                    guard !state.invalidated else { throw DirectTransportError.unavailable }
                    PairedDiagnosticStatus.record(.transportWriteAcknowledged)
                    completion(.success)
                    return
                } catch {
                    lastError = error
                    state.epoch = nil
                    if !state.invalidated { try? await Task.sleep(for: .milliseconds(400)) }
                }
            }
            PairedDiagnosticStatus.record(.transportFailure, error: lastError)
            completion(.failure(.transportUnavailable))
        }

        @MainActor private func receiveReverse(_ frame: Data) {
            guard frame.prefix(4) == Data("NRF1".utf8), !state.invalidated else { return }
            PairedDiagnosticStatus.record(.transportReverseFrameReceived)
            do {
                guard let packet = try state.reverse.receive(frame, now: ProcessInfo.processInfo.systemUptime) else { return }
                let parsed = try DirectTransportWire.parseReverse(packet)
                PairedDiagnosticStatus.record(.transportReversePacketReceived)
                let digest = Data(SHA256.hash(data: packet))
                if let prior = state.acknowledged[parsed.replyID] {
                    guard prior == digest else { return }
                    Task { @MainActor in try? await self.acknowledgeReverse(parsed, digest: digest) }
                    return
                }
                guard !state.reverseInFlight.contains(parsed.replyID) else { return }
                state.reverseInFlight.insert(parsed.replyID)
                Task { @MainActor in
                    defer { self.state.reverseInFlight.remove(parsed.replyID) }
                    do {
                        let (link, epoch, _) = try await self.ready(until: ProcessInfo.processInfo.systemUptime + 20)
                        guard epoch == parsed.epoch, let session = self.state.session else {
                            PairedDiagnosticStatus.record(.transportReverseSessionRejected)
                            throw DirectTransportError.unavailable
                        }
                        do {
                            try session.sendMessageToDataProvider(TransportMessage(sessionID: parsed.sessionID,
                                                                                  data: parsed.data))
                        } catch {
                            PairedDiagnosticStatus.record(.transportProviderHandoffFailed, error: error)
                            throw error
                        }
                        PairedDiagnosticStatus.record(.transportProviderHandoffAccepted)
                        self.state.acknowledged[parsed.replyID] = digest
                        if self.state.acknowledged.count > 16,
                           let evicted = self.state.acknowledged.keys.first {
                            self.state.acknowledged.removeValue(forKey: evicted)
                        }
                        do {
                            try await self.acknowledgeReverse(parsed, digest: digest, over: link)
                        } catch {
                            PairedDiagnosticStatus.record(.transportReverseAcknowledgementFailed, error: error)
                            throw error
                        }
                        PairedDiagnosticStatus.record(.transportResponseDispatched)
                    } catch {
                        PairedDiagnosticStatus.record(.transportReverseFailure, error: error)
                        forwardingLog.error("a watch reply could not be sent to the provider")
                    }
                }
            } catch {
                PairedDiagnosticStatus.record(.transportReverseFrameRejected, error: error)
                state.reverse.reset()
            }
        }

        @MainActor private func acknowledgeReverse(_ reverse: DirectTransportWire.Reverse,
                                                    digest: Data, over provided: WatchAccessoryLink? = nil) async throws {
            let link: WatchAccessoryLink
            if let provided { link = provided }
            else { (link, _, _) = try await ready(until: ProcessInfo.processInfo.systemUptime + 20) }
            var ack = Data("NRA1".utf8)
            ack.append(reverse.epoch); ack.append(reverse.replyID); ack.append(digest)
            try await link.writeDirect([ack], to: WatchAccessoryLink.directTransportCharacteristic)
        }

        func sessionInvalidated(error: AccessoryTransportSession.Error?) {
            PairedDiagnosticStatus.record(.transportInvalidated, error: error)
            Task { @MainActor in
                self.state.invalidated = true
                while let message = self.state.pending.popFirst() {
                    message.completion(.failure(.transportUnavailable))
                }
                if let token = self.state.callbackToken { self.state.link?.releaseCallbacks(token) }
                self.state.link?.retireDirect()
                self.state.link = nil
                self.state.session = nil
                self.state.epoch = nil
                self.state.reverse.reset()
            }
        }
    }
}

private enum DirectTransportError: Error { case invalid, unavailable, notReady }

private enum DirectTransportWire {
    struct Reverse {
        let epoch: Data
        let replyID: Data
        let sessionID: UUID
        let data: Data
    }
    static func uuid(_ id: UUID) -> Data {
        let x = id.uuid
        return Data([x.0, x.1, x.2, x.3, x.4, x.5, x.6, x.7,
                     x.8, x.9, x.10, x.11, x.12, x.13, x.14, x.15])
    }
    static func packet(epoch: Data, receiptID: UUID, sessionID: UUID, data: Data) throws -> Data {
        let id = Data(sessionID.uuidString.utf8)
        guard epoch.count == 32, id.count == 36, !data.isEmpty,
              data.count <= 2213 else { throw DirectTransportError.invalid }
        var packet = Data("NDT1".utf8)
        packet.append(epoch); packet.append(uuid(receiptID)); packet.append(UInt8(id.count)); packet.append(id)
        packet.append(UInt8(data.count >> 8)); packet.append(UInt8(data.count & 255)); packet.append(data)
        return packet
    }
    static func verify(receipt: Data, epoch: Data, receiptID: Data,
                       packet: Data, publicKey: Data) throws {
        guard receipt.count == 148, receipt.prefix(4) == Data("NTR1".utf8),
              receipt.subdata(in: 4..<36) == epoch,
              receipt.subdata(in: 36..<52) == receiptID,
              receipt.subdata(in: 52..<84) == Data(SHA256.hash(data: packet)) else {
            throw DirectTransportError.invalid
        }
        let key = try P256.Signing.PublicKey(x963Representation: publicKey)
        let signature = try P256.Signing.ECDSASignature(rawRepresentation: receipt.suffix(64))
        guard key.isValidSignature(signature, for: receipt.prefix(84)) else {
            throw DirectTransportError.invalid
        }
    }
    static func parseReverse(_ packet: Data) throws -> Reverse {
        guard packet.count >= 57, packet.count <= 3072,
              packet.prefix(4) == Data("NDR1".utf8),
              packet.count > 55 else { throw DirectTransportError.invalid }
        let size = Int(packet[52])
        guard (1...64).contains(size), packet.count >= 55 + size,
              let id = String(data: packet.subdata(in: 53..<(53 + size)), encoding: .utf8),
              let sessionID = UUID(uuidString: id) else { throw DirectTransportError.invalid }
        let dataSize = Int(packet[53 + size]) * 256 + Int(packet[54 + size])
        // AES-GCM combined data adds a 12-byte nonce and 16-byte tag to the reply.
        guard (29...2076).contains(dataSize),
              packet.count == 55 + size + dataSize else { throw DirectTransportError.invalid }
        return Reverse(epoch: packet.subdata(in: 4..<36), replyID: packet.subdata(in: 36..<52),
                       sessionID: sessionID, data: packet.suffix(dataSize))
    }
}

private struct DirectReverseReassembler {
    private var transferID: Data?
    private var total = 0
    private var nextOffset = 0
    private var deadline = 0.0
    private var bytes = Data()
    nonisolated init() {}
    mutating func reset() {
        transferID = nil; total = 0; nextOffset = 0; deadline = 0; bytes = Data()
    }
    mutating func receive(_ frame: Data, now: Double) throws -> Data? {
        guard frame.count > 24, frame.count <= 512,
              frame.prefix(4) == Data("NRF1".utf8) else { reset(); throw DirectTransportError.invalid }
        let length = Int(frame[4]) * 256 + Int(frame[5])
        let offset = Int(frame[6]) * 256 + Int(frame[7])
        let id = frame.subdata(in: 8..<24)
        guard (57...3072).contains(length) else { reset(); throw DirectTransportError.invalid }
        if offset == 0 {
            transferID = id; total = length; nextOffset = 0; bytes = Data(); deadline = now + 10
        }
        guard transferID == id, total == length, offset == nextOffset,
              now < deadline, frame.count - 24 <= total - bytes.count else {
            reset(); throw DirectTransportError.invalid
        }
        bytes.append(frame.dropFirst(24))
        nextOffset = bytes.count
        guard bytes.count == total else { return nil }
        let packet = bytes
        reset()
        guard packet.subdata(in: 36..<52) == id else { throw DirectTransportError.invalid }
        return packet
    }
}
#endif
