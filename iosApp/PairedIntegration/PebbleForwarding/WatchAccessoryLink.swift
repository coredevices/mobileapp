#if os(iOS)
internal import AccessorySetupKit
internal import CoreBluetooth
import Foundation
import OSLog
import PebbleProtocol

let forwardingLog = Logger(subsystem: NotificationBridgeConfiguration.namespace, category: "forwarding")

/// An extension's own link to the watch's AccessoryNotifications service.
///
/// iOS runs each extension in a process of its own and hands none of them the
/// Bluetooth link, so each connects by itself. A central made with
/// `CBCentralManagerOptionDeviceAccessForMedia` reaches the accessories the app
/// paired through AccessorySetupKit, which is all an extension is allowed.
///
/// The phone-local owner-signed publication pins the exact selected Bluetooth
/// UUID. Apple HPKE identifiers remain separate and are never treated as UUIDs.
@MainActor
final class WatchAccessoryLink: NSObject {
    static func selectedAuthorizedPeripheral() async throws -> UUID {
        try await WatchAccessorySelector().select()
    }
    // A warm resume reuses the same central; cold launch restores through its delegate.
    private static var restoredLinks: [String: WatchAccessoryLink] = [:]
    static func restored(for identifier: String?, selectedPeripheral: UUID) throws -> WatchAccessoryLink {
        guard let identifier else { return WatchAccessoryLink(restoreIdentifier: nil, selectedPeripheral: selectedPeripheral) }
        guard !identifier.isEmpty, identifier.utf8.count <= 128 else { throw LinkError.unavailable }
        if let link = restoredLinks[identifier] {
            guard link.selectedPeripheral == selectedPeripheral else { throw LinkError.unavailable }
            if !link.bootstrapRetired { return link }
            // A prior Transport session invalidated this link and its ASK
            // session. It cannot be restarted; keep the selected-watch check
            // above, then create a fresh central for this restore identifier.
            restoredLinks.removeValue(forKey: identifier)
        }
        guard restoredLinks.count < 4 else { throw LinkError.unavailable }
        let link = WatchAccessoryLink(restoreIdentifier: identifier, selectedPeripheral: selectedPeripheral)
        restoredLinks[identifier] = link
        return link
    }

    struct Connection {
        var peripheralIdentifier: UUID
        var maximumWriteLength: Int
    }

    enum LinkError: Error {
        case unavailable
        case writeFailed(String)
        case timedOut
    }

    /// Every notification the watch sends on the service, and the public key it
    /// answers a read with.
    private(set) var onNotification: ((_ frame: [UInt8]) -> Void)?
    /// Told when the link goes, so what was half heard on it is forgotten.
    private(set) var onLinkDropped: (() -> Void)?
    private var callbackOwner: UUID?
    func attachCallbacks(notification: @escaping ([UInt8]) -> Void,
                         dropped: @escaping () -> Void) -> UUID {
        let token = UUID()
        callbackOwner = token; onNotification = notification; onLinkDropped = dropped
        if let peripheral, let direct = bootstrapCharacteristics[Self.directTransportCharacteristic],
           direct.properties.contains(.notify) {
            peripheral.setNotifyValue(true, for: direct)
        }
        return token
    }
    func releaseCallbacks(_ token: UUID) {
        guard callbackOwner == token else { return }
        callbackOwner = nil; onNotification = nil; onLinkDropped = nil
        if let peripheral, let direct = bootstrapCharacteristics[Self.directTransportCharacteristic],
           direct.properties.contains(.notify) {
            peripheral.setNotifyValue(false, for: direct)
        }
    }

    /// What the watch last said its public key is. It keeps the key across
    /// reboots and notifies it only when a subscription starts, so a session
    /// that starts on a link another process already subscribed would otherwise
    /// never hear it; the read in `didDiscoverCharacteristicsFor` asks for it.
    /// Kept for the current link only: carried over, it was handed to iOS as
    /// the key of whichever watch the next link reached, or of the same watch
    /// after a reset gave it a new one.
    private(set) var publicKey: [UInt8]?

    private struct PendingWrite {
        let id = UUID()
        let characteristicUUID: CBUUID
        let makeFrames: (Connection) throws -> [[UInt8]]
        var frames: [[UInt8]]?
        let continuation: CheckedContinuation<Void, any Error>
        var deadline: Task<Void, Never>?
    }

    let selectedPeripheral: UUID
    private var central: CBCentralManager?
    private let accessories = ASAccessorySession()
    private var isAccessorySessionActive = false
    private var peripheral: CBPeripheral?
    /// Only the exact selected, currently authorized watch may be a candidate.
    private var candidates: [CBPeripheral] = []
    private var writeCharacteristic: CBCharacteristic?
    private var bootstrapCharacteristics: [CBUUID: CBCharacteristic] = [:]
    private var bootstrapRead: (UUID, CheckedContinuation<Data, any Error>)?
    private var directRead: (UUID, CBUUID, CheckedContinuation<Data, any Error>)?
    private var bootstrapRetired = false
    private var bootstrapReadDeadline: Task<Void, Never>?
    private var inFlightCharacteristic: CBUUID?
    private var inFlightAttribute: CBCharacteristic?
    private var writes: PairedFIFO<PendingWrite> = []
    /// The write whose frame is on the air. Kept past a deadline that gave up on
    /// it, so the acknowledgement that still arrives is not taken for the next.
    private var inFlight: UUID?
    /// Connects that failed since the link last worked. Past a handful the
    /// link stops trying by itself and waits for the next write to ask again.
    // A first central state is not a GAP disconnect. Once connected, every loss
    // remains terminal for the bound cryptographic exchange.
    private var hasConnected = false
    private var failures = 0
    private var retry: Task<Void, Never>?

    private static let maximumFailures = 5
    private static let service = CBUUID(string: PairedServiceConstants.service)
    private static let notifyCharacteristic = CBUUID(string: PairedServiceConstants.notify)
    private static let writeCharacteristicUUID = CBUUID(string: PairedServiceConstants.write)
    private static let relayCharacteristic = CBUUID(string: "51000006-328E-0FBB-C642-1AA6699BDADA")
    private static let receiptCharacteristic = CBUUID(string: "51000007-328E-0FBB-C642-1AA6699BDADA")
    static let prepareCharacteristic = CBUUID(string: "51000009-328E-0FBB-C642-1AA6699BDADA")
    static let activateCharacteristic = CBUUID(string: "5100000A-328E-0FBB-C642-1AA6699BDADA")
    static let directTransportCharacteristic = CBUUID(string: "5100000B-328E-0FBB-C642-1AA6699BDADA")
    private static let pairingService = CBUUID(string: WatchAdvertisement.pairingServiceUUID)

    private init(restoreIdentifier: String?, selectedPeripheral: UUID) {
        self.selectedPeripheral = selectedPeripheral
        super.init()
        accessories.activate(on: .main) { [weak self] event in
            let type = event.eventType
            guard type == .activated || type == .accessoryAdded || type == .accessoryChanged
                || type == .accessoryRemoved || type == .invalidated else { return }
            MainActor.assumeIsolated {
                if type == .invalidated {
                    PairedDiagnosticStatus.record(.extensionAccessoryInvalidated)
                    self?.isAccessorySessionActive = false
                    self?.dropLink()
                    if let peer = self?.peripheral { self?.central?.cancelPeripheralConnection(peer) }
                    self?.peripheral = nil
                    return
                }
                if type == .activated {
                    PairedDiagnosticStatus.record(.extensionAccessoryActivated)
                    self?.isAccessorySessionActive = true
                }
                if type == .accessoryRemoved {
                    self?.leaveAWatchNoLongerPaired()
                }
                self?.leaveAWatchNoLongerPaired()
                self?.startCentralIfAuthorized(restoreIdentifier: restoreIdentifier)
                self?.connectIfPossible()
                self?.rediscoverRestoredLink()
            }
        }
    }

    // ASK activation must expose the selected authorized accessory before this
    // process creates its per-accessory central. Never treat Off as permission.
    private func startCentralIfAuthorized(restoreIdentifier: String?) {
        guard central == nil, isAccessorySessionActive, !bootstrapRetired,
              pairedIdentifiers.contains(selectedPeripheral) else { return }
        var options: [String: Any] = [CBCentralManagerOptionDeviceAccessForMedia: true]
        if let restoreIdentifier {
            options[CBCentralManagerOptionRestoreIdentifierKey] = restoreIdentifier
        }
        central = CBCentralManager(
            delegate: self,
            queue: .main,
            options: options
        )
    }

    private func rediscoverRestoredLink() {
        guard isAccessorySessionActive, let peripheral,
              pairedIdentifiers.contains(peripheral.identifier), peripheral.state == .connected,
              writeCharacteristic == nil else { return }
        peripheral.discoverServices([Self.service])
    }

    /// Writes the frames `makeFrames` builds for the link they go out on, in
    /// order and each acknowledged, and returns once the last one is. Waits for
    /// a link if there is none yet, for as long as the deadline allows.
    func write(_ makeFrames: @escaping (Connection) throws -> [[UInt8]]) async throws {
        try await enqueueWrite(to: Self.writeCharacteristicUUID, makeFrames: makeFrames)
    }
    func stageBootstrap(_ wire: Data, admit: @escaping () throws -> Void) async throws {
        guard !bootstrapRetired, (393...456).contains(wire.count) else { throw LinkError.unavailable }
        try await enqueueWrite(to: Self.relayCharacteristic) { [self] connection in
            guard !bootstrapRetired else { throw LinkError.unavailable }
            try admit()
            guard wire.count <= connection.maximumWriteLength else {
                PairedDiagnosticStatus.record(.extensionWriteSizeRejected)
                throw LinkError.unavailable
            }
            return [[UInt8](wire)]
        }
    }
    func writeDirect(_ frames: [Data], to characteristic: CBUUID) async throws {
        guard [Self.prepareCharacteristic, Self.activateCharacteristic,
               Self.directTransportCharacteristic].contains(characteristic),
              !frames.isEmpty, frames.count <= 32 else { throw LinkError.unavailable }
        try await enqueueWrite(to: characteristic) { connection in
            guard connection.peripheralIdentifier == self.selectedPeripheral,
                  frames.allSatisfy({ !$0.isEmpty && $0.count <= connection.maximumWriteLength }) else {
                throw LinkError.unavailable
            }
            return frames.map { [UInt8]($0) }
        }
    }
    func writeFramedDirect(_ packet: Data, transferID: Data,
                           to characteristic: CBUUID) async throws {
        guard [Self.activateCharacteristic, Self.directTransportCharacteristic].contains(characteristic),
              packet.count >= 4, packet.count <= 4096,
              transferID.count == 16 else { throw LinkError.unavailable }
        try await enqueueWrite(to: characteristic) { connection in
            let chunk = min(connection.maximumWriteLength, 512) - 24
            guard connection.peripheralIdentifier == self.selectedPeripheral,
                  chunk > 0, (packet.count + chunk - 1) / chunk <= 32 else { throw LinkError.unavailable }
            return stride(from: 0, to: packet.count, by: chunk).map { offset in
                let end = min(offset + chunk, packet.count)
                var frame = Data("NCF1".utf8)
                frame.append(UInt8(packet.count >> 8)); frame.append(UInt8(packet.count & 255))
                frame.append(UInt8(offset >> 8)); frame.append(UInt8(offset & 255))
                frame.append(transferID)
                frame.append(packet.subdata(in: offset..<end))
                return [UInt8](frame)
            }
        }
    }
    func readDirect(from characteristic: CBUUID) async throws -> Data {
        guard [Self.prepareCharacteristic, Self.activateCharacteristic,
               Self.directTransportCharacteristic].contains(characteristic),
              directRead == nil, let connection,
              connection.peripheralIdentifier == selectedPeripheral,
              let peripheral, let item = bootstrapCharacteristics[characteristic],
              item.properties.contains(.read) else { throw LinkError.unavailable }
        return try await withCheckedThrowingContinuation { continuation in
            let token = UUID()
            directRead = (token, characteristic, continuation)
            Task { [weak self] in
                try? await Task.sleep(for: .seconds(10))
                guard !Task.isCancelled, let self, self.directRead?.0 == token else { return }
                let waiting = self.directRead
                self.directRead = nil
                waiting?.2.resume(throwing: LinkError.timedOut)
            }
            peripheral.readValue(for: item)
        }
    }
    func waitForDirectService() async throws {
        for _ in 0..<80 {
            guard !bootstrapRetired else { throw LinkError.unavailable }
            if connection != nil,
               bootstrapCharacteristics[Self.prepareCharacteristic]?.properties.contains(.read) == true,
               bootstrapCharacteristics[Self.activateCharacteristic] != nil,
               bootstrapCharacteristics[Self.directTransportCharacteristic] != nil { return }
            connectIfPossible()
            try await Task.sleep(for: .milliseconds(250))
        }
        throw LinkError.timedOut
    }
    func readBootstrapReceipt() async throws -> Data {
        guard !bootstrapRetired, bootstrapRead == nil, let connection, connection.peripheralIdentifier == selectedPeripheral,
              let peripheral, let characteristic = bootstrapCharacteristics[Self.receiptCharacteristic],
              characteristic.properties.contains(.read) else { throw LinkError.unavailable }
        return try await withCheckedThrowingContinuation { continuation in
            let token = UUID(); bootstrapRead = (token, continuation)
            bootstrapReadDeadline = Task { [weak self] in
                try? await Task.sleep(for: .seconds(10))
                guard !Task.isCancelled, let self, self.bootstrapRead?.0 == token else { return }
                let waiting = self.bootstrapRead; self.bootstrapRead = nil
                waiting?.1.resume(throwing: LinkError.timedOut)
            }
            peripheral.readValue(for: characteristic)
        }
    }
    func retireBootstrap() {
        bootstrapRetired = true
        Self.restoredLinks = Self.restoredLinks.filter { $0.value !== self }
        for index in writes.indices.reversed() where writes[index].characteristicUUID == Self.relayCharacteristic {
            let abandoned = writes.remove(at: index)
            abandoned.deadline?.cancel()
            abandoned.continuation.resume(throwing: LinkError.unavailable)
        }
        let reading = bootstrapRead; bootstrapRead = nil
        bootstrapReadDeadline?.cancel(); bootstrapReadDeadline = nil
        reading?.1.resume(throwing: LinkError.unavailable)
    }
    func retireDirect() {
        bootstrapRetired = true
        Self.restoredLinks = Self.restoredLinks.filter { $0.value !== self }
        retry?.cancel(); retry = nil
        if let peripheral { central?.cancelPeripheralConnection(peripheral) }
        dropLink()
        accessories.invalidate()
    }
    private func enqueueWrite(to characteristicUUID: CBUUID,
                              makeFrames: @escaping (Connection) throws -> [[UInt8]]) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, any Error>) in
            guard !bootstrapRetired, writes.count < 16 else {
                continuation.resume(throwing: LinkError.unavailable); return
            }
            var write = PendingWrite(characteristicUUID: characteristicUUID, makeFrames: makeFrames, continuation: continuation)
            let id = write.id
            write.deadline = Task { [weak self] in
                try? await Task.sleep(for: .seconds(15))
                guard !Task.isCancelled else { return }
                self?.abortTimedOutWrite(id)
            }
            writes.append(write)
            connectIfPossible()
            writeNext()
        }
    }

    /// A watch forgotten in the app stays connected to this process until the
    /// link drops by itself, and every write in the meantime goes to it.
    private func leaveAWatchNoLongerPaired() {
        guard let central, let kept = peripheral, !pairedIdentifiers.contains(kept.identifier) else { return }
        forwardingLog.log("the watch is no longer one of this app's accessories; letting it go")
        central.cancelPeripheralConnection(kept)
        dropLink()
        peripheral = nil
    }

    private var pairedIdentifiers: [UUID] {
        accessories.accessories.filter { $0.state == .authorized && $0.bluetoothIdentifier == selectedPeripheral }.compactMap(\.bluetoothIdentifier)
    }

    /// The paired watches the phone is connected to now, whichever process
    /// connected them.
    private func connectedPairedWatches(_ central: CBCentralManager) -> [CBPeripheral] {
        let paired = Set(pairedIdentifiers)
        return central.retrieveConnectedPeripherals(withServices: [Self.pairingService, Self.service])
            .filter { paired.contains($0.identifier) }
    }

    private func connectIfPossible() {
        guard let central, central.state == .poweredOn, isAccessorySessionActive,
              peripheral == nil, candidates.isEmpty else {
            return
        }
        retry?.cancel()
        retry = nil
        // Any Pebble in range answers to the pairing service, so falling back
        // to whichever was found first could send SESSION and DATA to a
        // stranger's watch. Only the pinned authorized UUID is reached; with none
        // yet, the session's accessoryAdded event asks again.
        guard !pairedIdentifiers.isEmpty else {
            forwardingLog.log("no watch paired through the app yet; waiting")
            return
        }
        let paired = central.retrievePeripherals(withIdentifiers: pairedIdentifiers)
        let connected = paired.first { $0.state == .connected } ?? connectedPairedWatches(central).first
        let chosen = connected.map { [$0] } ?? paired
        guard !chosen.isEmpty else {
            forwardingLog.log("no paired watch known to Bluetooth yet; scanning")
            central.scanForPeripherals(withServices: [Self.pairingService])
            return
        }
        for candidate in chosen {
            attempt(candidate)
        }
    }

    private func attempt(_ candidate: CBPeripheral) {
        guard candidate.identifier == selectedPeripheral, pairedIdentifiers.contains(candidate.identifier),
              !candidates.contains(candidate) else { return }
        candidates.append(candidate)
        candidate.delegate = self
        central?.connect(candidate)
    }

    private var connection: Connection? {
        guard isAccessorySessionActive, let peripheral, peripheral.identifier == selectedPeripheral, pairedIdentifiers.contains(peripheral.identifier), peripheral.state == .connected, writeCharacteristic != nil else { return nil }
        return Connection(
            peripheralIdentifier: peripheral.identifier,
            maximumWriteLength: peripheral.maximumWriteValueLength(for: .withResponse)
        )
    }

    private func writeNext() {
        guard inFlight == nil, let connection, let peripheral, !writes.isEmpty else { return }
        let target = writes[0].characteristicUUID
        guard let writeCharacteristic = target == Self.writeCharacteristicUUID ? writeCharacteristic : bootstrapCharacteristics[target],
              writeCharacteristic.properties.contains(.write) else {
            finish(writes[0].id, with: .failure(LinkError.unavailable)); return
        }
        if writes[0].frames == nil {
            do {
                writes[0].frames = try writes[0].makeFrames(connection)
            } catch {
                finish(writes[0].id, with: .failure(error))
                return
            }
        }
        guard let frame = writes[0].frames?.first else {
            finish(writes[0].id, with: .success(()))
            return
        }
        inFlight = writes[0].id; inFlightCharacteristic = target
        inFlightAttribute = writeCharacteristic
        peripheral.writeValue(Data(frame), for: writeCharacteristic, type: .withResponse)
    }

    private func abortTimedOutWrite(_ id: UUID) {
        guard writes.contains(where: { $0.id == id }) else { return }
        guard inFlight == id else {
            finish(id, with: .failure(LinkError.timedOut))
            return
        }
        // CoreBluetooth has not acknowledged an on-air write. Retire this
        // connection before another write can use its callback slot: clearing
        // inFlight alone could let a late callback complete the next write.
        let oldPeripheral = peripheral
        finish(id, with: .failure(LinkError.timedOut))
        if let oldPeripheral { central?.cancelPeripheralConnection(oldPeripheral) }
        dropLink()
    }

    private func finish(_ id: UUID, with result: Result<Void, any Error>) {
        guard let index = writes.firstIndex(where: { $0.id == id }) else { return }
        let write = writes.remove(at: index)
        write.deadline?.cancel()
        write.continuation.resume(with: result)
        writeNext()
    }

    private func dropLink() {
        PairedDiagnosticStatus.record(.extensionBLELinkDropped)
        let reading = bootstrapRead; bootstrapRead = nil
        bootstrapReadDeadline?.cancel(); bootstrapReadDeadline = nil
        reading?.1.resume(throwing: LinkError.unavailable)
        let direct = directRead; directRead = nil
        direct?.2.resume(throwing: LinkError.unavailable)
        bootstrapCharacteristics.removeAll(); inFlightCharacteristic = nil
        inFlightAttribute = nil
        writeCharacteristic = nil
        publicKey = nil
        inFlight = nil
        onLinkDropped?()
        // A GAP drop destroys the watch's nonce/HPKE state. Never replay an
        // interrupted SESSION or DATA under the next physical connection.
        while let abandoned = writes.popFirst() {
            abandoned.deadline?.cancel()
            abandoned.continuation.resume(throwing: LinkError.unavailable)
        }

    }

    /// Tries again after a pause, a bounded number of times: a watch that is
    /// away fails every connect at once, and retrying without a pause or an
    /// end would spin.
    private func retryAfterFailure() {
        failures += 1
        guard failures < Self.maximumFailures else {
            forwardingLog.error("the watch could not be reached \(self.failures) times; waiting for the next write")
            return
        }
        retry?.cancel()
        retry = Task { [weak self] in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            self?.connectIfPossible()
        }
    }
}

extension WatchAccessoryLink: @MainActor CBCentralManagerDelegate {
    func centralManager(_ central: CBCentralManager, willRestoreState dict: [String: Any]) {
        let restored = dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral] ?? []
        // Reattach objects first; AccessorySetupKit authorization is checked before writes.
        for candidate in restored where candidate.identifier == selectedPeripheral {
            hasConnected = true
            candidate.delegate = self
            if peripheral == nil { peripheral = candidate }
        }
        // Cached characteristics are not authorization or current service proof.
        // Rediscover and subscribe only after AccessorySetupKit activates.
        writeCharacteristic = nil
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .unknown: PairedDiagnosticStatus.record(.extensionBluetoothUnknown)
        case .resetting: PairedDiagnosticStatus.record(.extensionBluetoothResetting)
        case .unsupported: PairedDiagnosticStatus.record(.extensionBluetoothUnsupported)
        case .unauthorized: PairedDiagnosticStatus.record(.extensionBluetoothUnauthorized)
        case .poweredOff: PairedDiagnosticStatus.record(.extensionBluetoothPoweredOff)
        case .poweredOn: PairedDiagnosticStatus.record(.extensionBluetoothPoweredOn)
        @unknown default: PairedDiagnosticStatus.record(.extensionBluetoothUnknown)
        }
        guard central.state == .poweredOn else {
            // ASK/DeviceAccess may initially report Off while the extension's
            // per-accessory access is starting. Preserve only never-started
            // work; its original deadline and send-time consent check remain.
            let startupPending = !hasConnected && peripheral == nil && inFlight == nil
                && bootstrapRead == nil && directRead == nil && !bootstrapRetired
            if startupPending && (central.state == .unknown || central.state == .resetting
                                  || central.state == .poweredOff) {
                candidates = []
                retry?.cancel(); retry = nil
                return
            }
            dropLink()
            peripheral = nil
            candidates = []
            retry?.cancel()
            retry = nil
            return
        }
        connectIfPossible()
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        guard peripheral.identifier == selectedPeripheral, pairedIdentifiers.contains(peripheral.identifier) else { return }
        central.stopScan()
        attempt(peripheral)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        hasConnected = true
        PairedDiagnosticStatus.record(.extensionBLEConnected)
        guard isAccessorySessionActive, peripheral.identifier == selectedPeripheral,
              pairedIdentifiers.contains(peripheral.identifier) else {
            central.cancelPeripheralConnection(peripheral); return
        }
        if self.peripheral == nil, candidates.contains(peripheral) {
            self.peripheral = peripheral
            central.stopScan()
            for other in candidates where other != peripheral {
                central.cancelPeripheralConnection(other)
            }
            candidates = []
        }
        guard peripheral == self.peripheral else {
            central.cancelPeripheralConnection(peripheral)
            return
        }
        peripheral.discoverServices([Self.service])
    }

    func centralManager(
        _ central: CBCentralManager,
        didFailToConnect peripheral: CBPeripheral,
        error: (any Error)?
    ) {
        forwardingLog.error("could not reach the watch")
        candidates.removeAll { $0 == peripheral }
        if peripheral == self.peripheral {
            dropLink()
            self.peripheral = nil
        }
        guard self.peripheral == nil, candidates.isEmpty else { return }
        retryAfterFailure()
    }

    func centralManager(
        _ central: CBCentralManager,
        didDisconnectPeripheral peripheral: CBPeripheral,
        error: (any Error)?
    ) {
        guard peripheral == self.peripheral else { return }
        forwardingLog.log("the watch went away; waiting for it to come back")
        dropLink()
        guard isAccessorySessionActive, peripheral.identifier == selectedPeripheral,
              pairedIdentifiers.contains(peripheral.identifier) else {
            self.peripheral = nil; return
        }
        central.connect(peripheral)
    }
}

extension WatchAccessoryLink: @MainActor CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: (any Error)?) {
        PairedDiagnosticStatus.record(.extensionBLEServices, error: error)
        guard peripheral == self.peripheral, isAccessorySessionActive,
              pairedIdentifiers.contains(peripheral.identifier), error == nil else { return }
        guard let service = peripheral.services?.first(where: { $0.uuid == Self.service }) else {
            // `didModifyServices` looks again if the watch publishes it later.
            forwardingLog.error("the watch has no forwarding service; its firmware predates it")
            return
        }
        peripheral.discoverCharacteristics([Self.notifyCharacteristic, Self.writeCharacteristicUUID,
            Self.relayCharacteristic, Self.receiptCharacteristic, Self.prepareCharacteristic,
            Self.activateCharacteristic, Self.directTransportCharacteristic], for: service)
    }

    func peripheral(_ peripheral: CBPeripheral, didModifyServices invalidatedServices: [CBService]) {
        guard peripheral == self.peripheral,
              writeCharacteristic == nil || invalidatedServices.contains(where: { $0.uuid == Self.service }) else {
            return
        }
        forwardingLog.log("the watch changed its services; looking for the forwarding service again")
        dropLink()
        peripheral.discoverServices([Self.service])
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didDiscoverCharacteristicsFor service: CBService,
        error: (any Error)?
    ) {
        PairedDiagnosticStatus.record(.extensionBLECharacteristics, error: error)
        guard peripheral == self.peripheral, isAccessorySessionActive,
              pairedIdentifiers.contains(peripheral.identifier), service.uuid == Self.service,
              error == nil else { return }
        let characteristics = service.characteristics ?? []
        for characteristic in characteristics where [Self.relayCharacteristic, Self.receiptCharacteristic,
                    Self.prepareCharacteristic, Self.activateCharacteristic,
                    Self.directTransportCharacteristic].contains(characteristic.uuid) {
            bootstrapCharacteristics[characteristic.uuid] = characteristic
        }
        if let direct = bootstrapCharacteristics[Self.directTransportCharacteristic],
           direct.properties.contains(.notify), onNotification != nil {
            peripheral.setNotifyValue(true, for: direct)
        }
        if let notify = characteristics.first(where: { $0.uuid == Self.notifyCharacteristic }) {
            peripheral.setNotifyValue(true, for: notify)
            // Another process subscribed first has already had the key, and the
            // watch notifies it only on a subscription that starts. Older
            // firmware refuses the read, which `didUpdateValueFor` ignores.
            if publicKey == nil {
                peripheral.readValue(for: notify)
            }
        }
        writeCharacteristic = characteristics.first { $0.uuid == Self.writeCharacteristicUUID }
        if writeCharacteristic != nil {
            failures = 0
        }
        writeNext()
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateValueFor characteristic: CBCharacteristic,
        error: (any Error)?
    ) {
        if let waiting = directRead, characteristic.uuid == waiting.1,
           isAccessorySessionActive, peripheral == self.peripheral,
           peripheral.identifier == selectedPeripheral, pairedIdentifiers.contains(peripheral.identifier),
           error != nil || characteristic.value?.prefix(4) != Data("NRF1".utf8) {
            directRead = nil
            guard error == nil, let data = characteristic.value,
                  data.count <= 4096 else { waiting.2.resume(throwing: LinkError.unavailable); return }
            waiting.2.resume(returning: data)
            return
        }
        if characteristic.uuid == Self.receiptCharacteristic,
           isAccessorySessionActive, peripheral == self.peripheral,
           peripheral.identifier == selectedPeripheral, pairedIdentifiers.contains(peripheral.identifier),
           let waiting = bootstrapRead {
            bootstrapRead = nil; bootstrapReadDeadline?.cancel(); bootstrapReadDeadline = nil
            guard error == nil, let data = characteristic.value, data.count <= 100 else {
                waiting.1.resume(throwing: LinkError.unavailable); return
            }
            waiting.1.resume(returning: data); return
        }
        guard isAccessorySessionActive, peripheral == self.peripheral,
              peripheral.identifier == selectedPeripheral, pairedIdentifiers.contains(peripheral.identifier),
              (characteristic.uuid == Self.notifyCharacteristic ||
               characteristic.uuid == Self.directTransportCharacteristic), error == nil,
              let value = characteristic.value else { return }
        let frame = [UInt8](value)
        if peripheral == self.peripheral, let key = AccessoryTransportFrame.publicKey(from: frame) {
            publicKey = key
        }
        guard isAccessorySessionActive, peripheral == self.peripheral,
              pairedIdentifiers.contains(peripheral.identifier) else { return }
        onNotification?(frame)
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didWriteValueFor characteristic: CBCharacteristic,
        error: (any Error)?
    ) {
        guard isAccessorySessionActive, peripheral === self.peripheral,
              peripheral.identifier == selectedPeripheral, pairedIdentifiers.contains(peripheral.identifier),
              characteristic === inFlightAttribute,
              characteristic.uuid == inFlightCharacteristic,
              let id = inFlight else { return }
        inFlight = nil; inFlightCharacteristic = nil; inFlightAttribute = nil
        guard let index = writes.firstIndex(where: { $0.id == id }) else {
            writeNext()
            return
        }
        if let error {
            PairedDiagnosticStatus.record(.extensionBLEWriteFailed, error: error)
            finish(id, with: .failure(LinkError.writeFailed(error.localizedDescription)))
            return
        }
        writes[index].frames?.removeFirst()
        writeNext()
    }
}

/// Security and Transport sessions do not expose an accessory property. ASK is
/// therefore queried in the extension process. Ambiguous authorization fails
/// closed instead of selecting the first accessory returned by the system.
@MainActor
private final class WatchAccessorySelector {
    private let session = ASAccessorySession()
    private var continuation: CheckedContinuation<UUID, any Error>?
    private var deadline: Task<Void, Never>?

    func select() async throws -> UUID {
        try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            deadline = Task { [weak self] in
                try? await Task.sleep(for: .seconds(20))
                guard !Task.isCancelled else { return }
                self?.finish(.failure(WatchAccessoryLink.LinkError.timedOut))
            }
            session.activate(on: .main) { [weak self] event in
                MainActor.assumeIsolated {
                    guard let self else { return }
                    switch event.eventType {
                    case .activated, .accessoryAdded, .accessoryChanged, .accessoryRemoved:
                        let authorized = Set(self.session.accessories.compactMap { accessory in
                            accessory.state == .authorized ? accessory.bluetoothIdentifier : nil
                        })
                        if authorized.count == 1, let selected = authorized.first {
                            self.finish(.success(selected))
                        } else if authorized.count > 1 {
                            self.finish(.failure(WatchAccessoryLink.LinkError.unavailable))
                        }
                    case .invalidated:
                        self.finish(.failure(WatchAccessoryLink.LinkError.unavailable))
                    default: break
                    }
                }
            }
        }
    }

    private func finish(_ result: Result<UUID, any Error>) {
        guard let continuation else { return }
        self.continuation = nil
        deadline?.cancel()
        deadline = nil
        session.invalidate()
        continuation.resume(with: result)
    }
}
#endif
