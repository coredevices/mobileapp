import Foundation
import CoreBluetooth
import AccessorySetupKit

@MainActor
@available(iOS 26.5, *)
final class PairedMainBLEChannel: NSObject, @preconcurrency CBCentralManagerDelegate, @preconcurrency CBPeripheralDelegate {
    static let service = CBUUID(string: "51000000-328E-0FBB-C642-1AA6699BDADA")
    private static let pairingService = CBUUID(string: "0000FED9-0000-1000-8000-00805F9B34FB")
    static let enrollment = CBUUID(string: "51000005-328E-0FBB-C642-1AA6699BDADA")
    static let challenge = CBUUID(string: "51000003-328E-0FBB-C642-1AA6699BDADA")
    static let proof = CBUUID(string: "51000004-328E-0FBB-C642-1AA6699BDADA")
    static let relay = CBUUID(string: "51000006-328E-0FBB-C642-1AA6699BDADA")
    static let receive = CBUUID(string: "51000002-328E-0FBB-C642-1AA6699BDADA")
    static let deliveryStatus = CBUUID(string: "5100000C-328E-0FBB-C642-1AA6699BDADA")
    private enum ConnectionStage: String { case waitingForBluetooth = "Bluetooth readiness", retrievingPeer = "Authorized watch lookup", connecting = "Bluetooth connection", services = "Secure service discovery", characteristics = "Secure characteristic discovery" }
    private var connectionStage: ConnectionStage = .waitingForBluetooth
    private func connectionFailure(_ reason: String, underlying: (any Error)? = nil) -> NSError {
        var info: [String: Any] = [NSLocalizedDescriptionKey: "\(connectionStage.rawValue): \(reason)"]
        var diagnosticError: NSError?
        if let underlying {
            let native = underlying as NSError
            let domain = native.domain == CBATTErrorDomain || native.domain == CBErrorDomain
                ? native.domain : "CoreBluetooth"
            info[NSLocalizedDescriptionKey] = "\(connectionStage.rawValue): \(reason) (\(domain), code \(native.code))"
            if (native.domain == CBATTErrorDomain && (0...17).contains(native.code)) ||
               (native.domain == CBErrorDomain && (0...20).contains(native.code)) {
                diagnosticError = NSError(domain: native.domain, code: native.code)
            }
        }
        let error = NSError(domain: "PebbleD.SecureConnection", code: 1, userInfo: info)
        PairedDiagnosticStatus.record(.bleFailure, error: diagnosticError ?? error, bluetoothState: central.state.rawValue)
        return error
    }
    private func diagnosticCode(_ error: any Error) -> NSError? {
        let native = error as NSError
        let allowed: Bool
        switch native.domain {
        case CBATTErrorDomain: allowed = (0...17).contains(native.code)
        case CBErrorDomain: allowed = (0...20).contains(native.code)
        case "PebbleD.SecureConnection": allowed = native.code == 1
        case "PebbleD.SecureRead": allowed = native.code == 1 || native.code == 2
        default: allowed = false
        }
        return allowed ? NSError(domain: native.domain, code: native.code) : nil
    }
    private var central: CBCentralManager!
    private var peer: CBPeripheral?
    private var characteristics: [CBUUID: CBCharacteristic] = [:]
    private var connection: CheckedContinuation<Void, any Error>?
    private var read: (CBUUID, CheckedContinuation<Data, any Error>)?
    private var write: (CBUUID, CheckedContinuation<Void, any Error>)?
    private var deadline: Task<Void, Never>?
    private var operation: UUID?
    private var selected: UUID?
    private var accessory: ASAccessory?
    var onLinkDropped: (() -> Void)?
    func retainThroughSession(expiresMillis: UInt64) throws {
        let now = try PairedNativeSessionFactory.nowMillis()
        guard expiresMillis > now, expiresMillis - now <= 300000 else { throw PairedOwnerError.stale }
        // Retain this acknowledged physical connection only through its signed
        // lease. This never changes the proof deadline or renews authority.
        Task { @MainActor [self] in
            try? await Task.sleep(for: .milliseconds(Int64(expiresMillis - now)))
            cancel()
        }
    }
    func requireCurrent() throws {
        guard let peer, peer.state == .connected, peer.identifier == selected,
              accessory?.state == .authorized, accessory?.bluetoothIdentifier == selected else {
            throw PairedOwnerError.stale
        }
    }
    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: .main,
            options: [CBCentralManagerOptionDeviceAccessForMedia: true])
    }
    private func timeout(_ token: UUID) {
        deadline?.cancel()
        deadline = Task { [weak self] in
            try? await Task.sleep(for: .seconds(10))
            guard !Task.isCancelled, self?.operation == token else { return }
            guard let self else { return }
            self.cancel(error: self.connectionFailure("Timed out after 10 seconds"))
        }
    }
    private func begin() throws -> UUID {
        guard PairedNotificationGate.enabled, operation == nil else { throw PairedOwnerError.unavailable }
        let token = UUID(); operation = token; timeout(token); return token
    }
    private func finishOperation() { operation = nil; deadline?.cancel(); deadline = nil }
    func connect(_ accessory: ASAccessory) async throws {
        guard accessory.state == .authorized, let identifier = accessory.bluetoothIdentifier,
              peer == nil else { throw PairedOwnerError.unavailable }
        _ = try begin(); selected = identifier; self.accessory = accessory
        try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in
                connectionStage = .waitingForBluetooth; connection = continuation
                PairedDiagnosticStatus.record(.bleWaiting, bluetoothState: central.state.rawValue)
                connectWhenPowered()
            }
        }, onCancel: { Task { @MainActor [weak self] in self?.cancel() } })
    }
    private func hasAuthorizedSelection() -> Bool {
        guard let selected else { return false }
        return accessory?.state == .authorized && accessory?.bluetoothIdentifier == selected
    }
    private func connectWhenPowered() {
        guard connection != nil, peer == nil, let selected else { return }
        guard hasAuthorizedSelection() else {
            cancel(error: connectionFailure("Selected watch authorization changed")); return
        }
        // During startup, poweredOff/unknown/resetting may precede poweredOn.
        // Wait only within the original begin() deadline; state changes never
        // allocate another operation token or extend that ten-second bound.
        switch central.state {
        case .poweredOn: PairedDiagnosticStatus.record(.blePoweredOn, bluetoothState: central.state.rawValue)
        case .unauthorized, .unsupported:
            cancel(error: connectionFailure("Bluetooth state \(central.state.rawValue)")); return
        default: return
        }
        connectionStage = .retrievingPeer
        let known = central.retrievePeripherals(withIdentifiers: [selected])
            .first(where: { $0.identifier == selected })
        // ASK authorization is not proof that retrievePeripherals has populated
        // this central's cache. A connected peer is still accepted only by the
        // same exact authorized UUID; never select the first unrelated watch.
        let peripheral = known ?? central.retrieveConnectedPeripherals(withServices: [Self.pairingService, Self.service])
            .first(where: { $0.identifier == selected })
        guard let peripheral else {
            cancel(error: connectionFailure("Exact authorized watch was not available to CoreBluetooth")); return
        }
        guard hasAuthorizedSelection(), peripheral.identifier == selected else {
            cancel(error: connectionFailure("Selected watch authorization or identity changed")); return
        }
        peer = peripheral; peripheral.delegate = self; connectionStage = .connecting; central.connect(peripheral)
    }
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn { connectWhenPowered(); return }
        // Once a peer is assigned, state loss invalidates its connection and
        // pending reads/writes. Never resume that peer or replay an operation.
        if peer != nil || central.state == .unauthorized || central.state == .unsupported {
            cancel(error: connectionFailure("Bluetooth state \(central.state.rawValue)")); return
        }
        // No peer yet: let poweredOff/unknown/resetting settle within the
        // existing connection deadline. A single state4 report says nothing
        // about whether the user changed a hardware setting.
    }
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        guard peripheral === peer, peripheral.identifier == selected else { return }
        guard hasAuthorizedSelection() else {
            cancel(error: connectionFailure("Selected watch authorization changed")); return
        }
        PairedDiagnosticStatus.record(.bleConnected)
        connectionStage = .services
        PairedDiagnosticStatus.record(.bleServices)
        peripheral.discoverServices([Self.service])
    }
    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: (any Error)?) {
        if peripheral === peer { cancel(error: connectionFailure("Connection failed", underlying: error)) }
    }
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: (any Error)?) {
        if peripheral === peer { cancel(error: connectionFailure("Watch disconnected", underlying: error)) }
    }
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: (any Error)?) {
        guard peripheral === peer else { return }
        guard hasAuthorizedSelection(), peripheral.identifier == selected else {
            cancel(error: connectionFailure("Selected watch authorization or identity changed")); return
        }
        if let error { cancel(error: connectionFailure("Service discovery failed", underlying: error)); return }
        guard let service = peripheral.services?.first(where: { $0.uuid == Self.service }) else { cancel(error: connectionFailure("Watch secure service was not returned")); return }
        connectionStage = .characteristics
        PairedDiagnosticStatus.record(.bleCharacteristics)
        peripheral.discoverCharacteristics([Self.enrollment, Self.challenge, Self.proof, Self.receive,
                                            Self.relay, Self.deliveryStatus], for: service)
    }
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: (any Error)?) {
        guard peripheral === peer, service.uuid == Self.service else { return }
        guard hasAuthorizedSelection(), peripheral.identifier == selected else {
            cancel(error: connectionFailure("Selected watch authorization or identity changed")); return
        }
        guard error == nil else { cancel(error: connectionFailure("Characteristic discovery failed", underlying: error)); return }
        for characteristic in service.characteristics ?? [] { characteristics[characteristic.uuid] = characteristic }
        guard [Self.enrollment, Self.challenge, Self.proof, Self.receive, Self.relay].allSatisfy({ characteristics[$0] != nil }) else { cancel(error: connectionFailure("Required secure characteristic was not returned")); return }
        let completed = connection; connection = nil; finishOperation(); completed?.resume()
    }
    func readValue(_ uuid: CBUUID) async throws -> Data {
        try requireCurrent()
        guard let peer, peer.state == .connected, let characteristic = characteristics[uuid],
              characteristic.properties.contains(.read) else { throw PairedOwnerError.unavailable }
        _ = try begin()
        return try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in
                read = (uuid, continuation); peer.readValue(for: characteristic)
            }
        }, onCancel: { Task { @MainActor [weak self] in self?.cancel() } })
    }
    func writeValue(_ data: Data, to uuid: CBUUID) async throws {
        try requireCurrent()
        guard let peer, peer.state == .connected, let characteristic = characteristics[uuid],
              characteristic.properties.contains(.write), !data.isEmpty, data.count <= 512,
              data.count <= peer.maximumWriteValueLength(for: .withResponse) else { throw PairedOwnerError.invalid }
        _ = try begin()
        try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in
                write = (uuid, continuation); peer.writeValue(data, for: characteristic, type: .withResponse)
            }
        }, onCancel: { Task { @MainActor [weak self] in self?.cancel() } })
    }
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: (any Error)?) {
        guard peripheral === peer, let waiting = read, waiting.0 == characteristic.uuid else { return }
        read = nil; finishOperation()
        if let error {
            let native = error as NSError
            // Preserve ATT/CoreBluetooth classification, not arbitrary error
            // descriptions, userInfo, characteristic bytes or key material.
            let domain = native.domain == CBATTErrorDomain || native.domain == CBErrorDomain
                ? native.domain : "PebbleD.CoreBluetoothRead"
            let sanitized = NSError(domain: domain, code: native.code,
                userInfo: [NSLocalizedDescriptionKey: "Watch characteristic read failed (\(domain), code \(native.code))"])
            PairedDiagnosticStatus.record(.bleReadFailure, error: diagnosticCode(sanitized))
            waiting.1.resume(throwing: sanitized)
            return
        }
        guard let value = characteristic.value else {
            let failure = NSError(domain: "PebbleD.SecureRead", code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Watch characteristic read returned no value"])
            PairedDiagnosticStatus.record(.bleReadFailure, error: diagnosticCode(failure))
            waiting.1.resume(throwing: failure)
            return
        }
        guard value.count <= 512 else {
            let failure = NSError(domain: "PebbleD.SecureRead", code: 2,
                userInfo: [NSLocalizedDescriptionKey: "Watch characteristic read exceeded the 512-byte limit"])
            PairedDiagnosticStatus.record(.bleReadFailure, error: diagnosticCode(failure))
            waiting.1.resume(throwing: failure)
            return
        }
        waiting.1.resume(returning: value)
    }
    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: (any Error)?) {
        guard peripheral === peer, let waiting = write, waiting.0 == characteristic.uuid else { return }
        write = nil; finishOperation()
        if let error { waiting.1.resume(throwing: error) } else { waiting.1.resume() }
    }
    func exchange(_ request: Data) async throws -> Data {
        try await writeValue(request, to: Self.proof); return try await readValue(Self.proof)
    }
    func sendSession(_ request: Data) async throws {
        try requireCurrent()
        guard let peer else { throw PairedOwnerError.unavailable }
        let frames = try PairedOuterFrames.writes(request,
            maximumWriteLength: peer.maximumWriteValueLength(for: .withResponse))
        for frame in frames { try Task.checkCancellation(); try requireCurrent(); try await writeValue(frame, to: Self.receive) }
        try Task.checkCancellation(); try requireCurrent()
    }
    func cancel(error: any Error = PairedOwnerError.unavailable) {
        if peer != nil { PairedDiagnosticStatus.record(.bleLinkLost, error: diagnosticCode(error), bluetoothState: central.state.rawValue) }
        let dropped = onLinkDropped; onLinkDropped = nil
        dropped?()
        let connecting = connection; let reading = read; let writing = write
        connection = nil; read = nil; write = nil; finishOperation(); characteristics.removeAll(); selected = nil; accessory = nil
        let old = peer; peer = nil; old?.delegate = nil
        if let old { central.cancelPeripheralConnection(old) }
        connecting?.resume(throwing: error)
        reading?.1.resume(throwing: error)
        writing?.1.resume(throwing: error)
    }
}

struct PairedWatchDeliveryStatus {
    let flags: UInt8
    let lastStage: UInt8
    let lastResult: UInt8
    let fragmentRejected: UInt32
    let fragmentCompleted: UInt32
    let prepareAttempts: UInt32
    let prepareAccepted: UInt32
    let activationAttempts: UInt32
    let activationAccepted: UInt32
    let transportAttempts: UInt32
    let decryptRejected: UInt32
    let consumerRejected: UInt32
    let transportAccepted: UInt32
    let notificationDelivery: [UInt32]?
    let replyDelivery: [UInt32]?

    init(_ data: Data) throws {
        let bytes = [UInt8](data)
        guard bytes.count >= 5, bytes[0...3].elementsEqual(Array("NDS1".utf8)),
              (bytes[4] == 1 && bytes.count == 48) || (bytes[4] == 2 && bytes.count == 88) ||
              (bytes[4] == 3 && bytes.count == 124) else {
            throw NSError(domain: "PebbleD.DeliveryStatus", code: 1,
                          userInfo: [NSLocalizedDescriptionKey: "The watch returned an unsupported delivery trace."])
        }
        func value(_ offset: Int) -> UInt32 {
            (UInt32(bytes[offset]) << 24) | (UInt32(bytes[offset + 1]) << 16) |
            (UInt32(bytes[offset + 2]) << 8) | UInt32(bytes[offset + 3])
        }
        flags = bytes[5]; lastStage = bytes[6]; lastResult = bytes[7]
        fragmentRejected = value(8); fragmentCompleted = value(12)
        prepareAttempts = value(16); prepareAccepted = value(20)
        activationAttempts = value(24); activationAccepted = value(28)
        transportAttempts = value(32); decryptRejected = value(36)
        consumerRejected = value(40); transportAccepted = value(44)
        notificationDelivery = bytes[4] >= 2 ? stride(from: 48, to: 88, by: 4).map(value) : nil
        replyDelivery = bytes[4] == 3 ? stride(from: 88, to: 124, by: 4).map(value) : nil
    }

    var summary: String {
        let identityReady = flags & 1 != 0
        let bridgeReady = flags & 2 != 0
        let activationReady = flags & 8 != 0
        let transportReady = flags & 16 != 0
        let conclusion: String
        if !identityReady || !bridgeReady {
            conclusion = "The watch secure identity or direct bridge did not initialize."
        } else if prepareAttempts == 0 {
            conclusion = "Apple's Security extension has not reached the watch."
        } else if activationAccepted == 0 {
            conclusion = "Security reached the watch, but Apple's encapsulated key was not accepted."
        } else if transportAttempts == 0 {
            conclusion = "The secure key exchange completed, but no encrypted transport payload reached the watch."
        } else if transportAccepted == 0 && decryptRejected > 0 {
            conclusion = "Encrypted transport reached the watch, but HPKE decryption failed."
        } else if transportAccepted == 0 && consumerRejected > 0 {
            conclusion = "The watch decrypted transport data, but its notification pipeline rejected it."
        } else if transportAccepted > 0 {
            if let delivery = notificationDelivery {
                if delivery[0] == 0 {
                    conclusion = "Encrypted transport is working, but only non-display commands reached the watch. No notification to display was received."
                } else if delivery[4] > 0 || delivery[5] > 0 {
                    conclusion = "The watch stored notification content. If nothing appeared, investigate watch alert policy or popup handling."
                } else if delivery[6] > 0 {
                    conclusion = "The watch received notification content but muted it through its source-app notification setting."
                } else if delivery[7] > 0 {
                    conclusion = "The watch received notification content but failed to create or store its notification item."
                } else {
                    conclusion = "Notification content was queued, but system-task processing has not completed. Read the trace again."
                }
            } else {
                conclusion = "The watch accepted encrypted transport data. It may be a notification, removal, clear, reply result, or settings command; matching firmware distinguishes them."
            }
        } else {
            conclusion = "The watch received transport data but rejected it before acceptance."
        }
        let processing: String
        if let delivery = notificationDelivery {
            let types = [1: "present/update", 2: "remove", 3: "clear"]
            let outcomes = [1: "new stored", 2: "update stored", 3: "muted", 4: "failed", 5: "removed", 6: "cleared", 7: "older update skipped"]
            processing = "\npresent/remove/clear queued: \(delivery[0])/\(delivery[1])/\(delivery[2])\n" +
                "system processed: \(delivery[3]); new/update stored: \(delivery[4])/\(delivery[5])\n" +
                "muted/processing failures: \(delivery[6])/\(delivery[7])\n" +
                "last message/outcome: \(types[Int(delivery[8])] ?? "none")/\(outcomes[Int(delivery[9])] ?? "none")"
        } else { processing = "" }
        let replyStatus: String
        if let reply = replyDelivery {
            let failures: [UInt32: String] = [0: "none", 1: "invalid reply input", 2: "reply queue unavailable",
                3: "notification session no longer active", 4: "reply encryption failed",
                5: "no subscribed encrypted phone link", 6: "Bluetooth notification enqueue failed",
                7: "reply handoff or Apple action result timed out", 8: "Apple rejected the reply action"]
            let result: String
            if reply[0] == 0 { result = "No watch reply was submitted." }
            else if reply[8] != 0 {
                result = "Last reply boundary: " + (failures[reply[8]] ?? "unknown failure") + "."
            } else if flags & 64 != 0 {
                result = "A reply is pending: waiting for phone handoff or the Apple action result."
            } else if reply[6] > 0 {
                result = "Apple accepted a watch reply action. Verify that the reply arrives in Messages."
            } else { result = "A watch reply was submitted; completion has not been recorded." }
            replyStatus = "Watch replies: " + result + "\n" +
                "attempted/queued/direct/sealed: \(reply[0])/\(reply[1])/\(reply[2])/\(reply[3])\n" +
                "Bluetooth queued/phone handoff/Apple accepted/failed: \(reply[4])/\(reply[5])/\(reply[6])/\(reply[7])\n\n"
        } else { replyStatus = "" }
        return replyStatus + "\(conclusion)\n\n" +
            "identity/bridge/activation/transport ready: \(identityReady)/\(bridgeReady)/\(activationReady)/\(transportReady)\n" +
            "prepare: \(prepareAccepted)/\(prepareAttempts) accepted\n" +
            "activation: \(activationAccepted)/\(activationAttempts) accepted\n" +
            "transport: \(transportAccepted)/\(transportAttempts) accepted\n" +
            "decrypt/consumer/fragment rejects: \(decryptRejected)/\(consumerRejected)/\(fragmentRejected)\n" +
            "completed frames: \(fragmentCompleted); last stage/result: \(lastStage)/\(lastResult)" + processing
    }
}
