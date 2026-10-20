#!/usr/bin/env python3
"""Compile the production link write state machine against stalled BLE callbacks."""

from pathlib import Path
import subprocess
import tempfile


root = Path(__file__).resolve().parents[1]
source = (root / "iosApp/PairedIntegration/PebbleForwarding/WatchAccessoryLink.swift").read_text()


def method(signature: str) -> str:
    start = source.index(signature)
    brace = source.index("{", start)
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


methods = "\n".join(method(signature) for signature in (
    "static func restored(for identifier:",
    "func retireDirect()",
    "private func writeNext()",
    "private func abortTimedOutWrite(",
    "private func finish(_ id: UUID, with result:",
    "private func dropLink()",
    "func peripheral(\n        _ peripheral: CBPeripheral,\n        didWriteValueFor",
))
assert "self?.abortTimedOutWrite(id)" in source

swift = r'''
import Foundation

struct CBUUID: Hashable { let raw: String; init(_ raw: String) { self.raw = raw } }
enum Property { case write }
final class CBCharacteristic {
    let uuid: CBUUID
    let properties: Set<Property> = [.write]
    init(_ uuid: CBUUID) { self.uuid = uuid }
}
enum WriteType { case withResponse }
final class CBPeripheral {
    let identifier: UUID
    var writes = 0
    init(_ identifier: UUID) { self.identifier = identifier }
    func writeValue(_ value: Data, for characteristic: CBCharacteristic, type: WriteType) {
        writes += 1
    }
}
final class CBCentralManager {
    var cancelled: [CBPeripheral] = []
    func cancelPeripheralConnection(_ peer: CBPeripheral) { cancelled.append(peer) }
}
final class FakeAccessories { var invalidated = false; func invalidate() { invalidated = true } }
final class FakeDeadline { var cancelled = false; func cancel() { cancelled = true } }
final class FakeContinuation<T> {
    var outcomes: [String] = []
    func resume(with result: Result<T, any Error>) {
        switch result {
        case .success: outcomes.append("success")
        case .failure(let error): outcomes.append(String(describing: error))
        }
    }
    func resume(throwing error: any Error) { outcomes.append(String(describing: error)) }
}
enum PairedDiagnosticStatus {
    enum Event { case extensionBLELinkDropped, extensionBLEWriteFailed }
    static func record(_ event: Event, error: (any Error)? = nil) {}
}
''' + (root / "iosApp/PairedIntegration/PebbleForwarding/PairedFIFO.swift").read_text() + r'''
@MainActor final class WatchAccessoryLink {
    enum LinkError: Error { case unavailable, timedOut, writeFailed(String) }
    struct Connection { let peripheralIdentifier: UUID }
    struct PendingWrite {
        let id: UUID
        let characteristicUUID: CBUUID
        let makeFrames: (Connection) throws -> [[UInt8]]
        var frames: [[UInt8]]? = nil
        let continuation: FakeContinuation<Void>
        var deadline: FakeDeadline? = FakeDeadline()
    }
    static let writeCharacteristicUUID = CBUUID("direct")
    private static var restoredLinks: [String: WatchAccessoryLink] = [:]
    let selectedPeripheral: UUID
    private init(restoreIdentifier: String?, selectedPeripheral: UUID) {
        self.selectedPeripheral = selectedPeripheral
    }
    var bootstrapRetired = false
    var retry: FakeDeadline?
    let accessories = FakeAccessories()
    var central: CBCentralManager? = CBCentralManager()
    var peripheral: CBPeripheral?
    var writeCharacteristic: CBCharacteristic?
    var bootstrapCharacteristics: [CBUUID: CBCharacteristic] = [:]
    var bootstrapRead: (UUID, FakeContinuation<Data>)?
    var bootstrapReadDeadline: FakeDeadline?
    var directRead: (UUID, CBUUID, FakeContinuation<Data>)?
    var publicKey: [UInt8]?
    var onLinkDropped: (() -> Void)?
    var inFlightCharacteristic: CBUUID?
    var inFlightAttribute: CBCharacteristic?
    var inFlight: UUID?
    var writes: PairedFIFO<PendingWrite> = []
    var isAccessorySessionActive = true
    var pairedIdentifiers: [UUID] { [selectedPeripheral] }
    var connection: Connection? {
        guard let peripheral, writeCharacteristic != nil else { return nil }
        return Connection(peripheralIdentifier: peripheral.identifier)
    }
''' + methods + r'''
    func run() {
        let oldPeer = CBPeripheral(selectedPeripheral)
        let oldAttribute = CBCharacteristic(Self.writeCharacteristicUUID)
        peripheral = oldPeer; writeCharacteristic = oldAttribute
        let first = FakeContinuation<Void>(), queued = FakeContinuation<Void>()
        let firstID = UUID(), queuedID = UUID()
        writes.append(PendingWrite(id: firstID, characteristicUUID: Self.writeCharacteristicUUID,
                                   makeFrames: { _ in [[1]] }, continuation: first))
        writeNext()
        precondition(oldPeer.writes == 1 && inFlight == firstID)
        writes.append(PendingWrite(id: queuedID, characteristicUUID: Self.writeCharacteristicUUID,
                                   makeFrames: { _ in [[2]] }, continuation: queued))
        abortTimedOutWrite(queuedID)
        precondition(queued.outcomes == ["timedOut"] && first.outcomes.isEmpty)
        precondition(central?.cancelled.isEmpty == true && inFlight == firstID)
        let waiting = FakeContinuation<Void>()
        writes.append(PendingWrite(id: UUID(), characteristicUUID: Self.writeCharacteristicUUID,
                                   makeFrames: { _ in [[2]] }, continuation: waiting))
        abortTimedOutWrite(firstID)
        precondition(first.outcomes == ["timedOut"] && waiting.outcomes == ["unavailable"])
        precondition(central?.cancelled.count == 1 && inFlight == nil && writeCharacteristic == nil)
        peripheral(oldPeer, didWriteValueFor: oldAttribute, error: nil)
        precondition(first.outcomes.count == 1 && queued.outcomes.count == 1 && waiting.outcomes.count == 1)

        let newPeer = CBPeripheral(selectedPeripheral)
        let newAttribute = CBCharacteristic(Self.writeCharacteristicUUID)
        peripheral = newPeer; writeCharacteristic = newAttribute
        let next = FakeContinuation<Void>(), nextID = UUID()
        writes.append(PendingWrite(id: nextID, characteristicUUID: Self.writeCharacteristicUUID,
                                   makeFrames: { _ in [[3]] }, continuation: next))
        writeNext()
        precondition(newPeer.writes == 1 && inFlight == nextID)
        peripheral(oldPeer, didWriteValueFor: newAttribute, error: nil)
        peripheral(newPeer, didWriteValueFor: oldAttribute, error: nil)
        precondition(inFlight == nextID && next.outcomes.isEmpty)
        peripheral(newPeer, didWriteValueFor: newAttribute, error: nil)
        precondition(inFlight == nil && next.outcomes == ["success"])
    }
    static func runRestoration() throws {
        let selected = UUID()
        let old = try restored(for: "transport-session", selectedPeripheral: selected)
        let reused = try restored(for: "transport-session", selectedPeripheral: selected)
        precondition(old === reused)
        var newOwnerCallbacks = 0
        old.onLinkDropped = { }
        old.retireDirect()
        precondition(old.bootstrapRetired && old.accessories.invalidated)
        let fresh = try restored(for: "transport-session", selectedPeripheral: selected)
        precondition(fresh !== old && !fresh.bootstrapRetired && !fresh.accessories.invalidated)
        fresh.onLinkDropped = { newOwnerCallbacks += 1 }
        old.onLinkDropped?()
        precondition(newOwnerCallbacks == 0)
        do {
            _ = try restored(for: "transport-session", selectedPeripheral: UUID())
            preconditionFailure("restored identifier crossed selected-watch identity")
        } catch LinkError.unavailable { }
        for number in 0..<4 {
            let temporary = try restored(for: "retired-\(number)", selectedPeripheral: selected)
            temporary.retireDirect()
        }
        let fifth = try restored(for: "after-four-retirements", selectedPeripheral: selected)
        precondition(!fifth.bootstrapRetired)
    }
}
Task { @MainActor in
    do {
        let link = try WatchAccessoryLink.restored(for: "write-timeout", selectedPeripheral: UUID())
        link.run()
        try WatchAccessoryLink.runRestoration()
        print("active write timeout/reconnect/late callback and retired restoration passed")
        exit(0)
    } catch { print(error); exit(1) }
}
dispatchMain()
'''

with tempfile.TemporaryDirectory(prefix="pebble-link-timeout-") as directory:
    path = Path(directory) / "main.swift"
    binary = Path(directory) / "link-timeout"
    path.write_text(swift)
    subprocess.run(["xcrun", "swiftc", "-swift-version", "5", "-o", str(binary), str(path)], check=True)
    subprocess.run([str(binary)], check=True, timeout=10)
