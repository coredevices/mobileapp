import Foundation

// A distinct new-service frame contract. Legacy 0x02 SESSION / 0x03 DATA
// are never treated as authenticated. Header: POF1, LAST flag, index,
// request[16], total-envelope-length BE16, chunk. No feature ID is taken
// from an unsigned frame: dispatch uses the authenticated session binding.
enum PairedOuterFrames {
    static func writes(_ wire: Data, maximumWriteLength: Int) throws -> [Data] {
        guard wire.count >= 128, wire.count <= 2432,
              wire.prefix(4) == Data("POT1".utf8) else { throw PairedOwnerError.invalid }
        let chunk = min(maximumWriteLength, 512) - 24
        guard chunk > 0, (wire.count + chunk - 1) / chunk <= 32 else { throw PairedOwnerError.invalid }
        let request = wire.subdata(in: 46..<62)
        return stride(from: 0, to: wire.count, by: chunk).enumerated().map { index, offset in
            let end = min(offset + chunk, wire.count)
            var frame = Data("POF1".utf8)
            frame.append(end == wire.count ? 1 : 0); frame.append(UInt8(index))
            frame.append(request)
            frame.append(UInt8(wire.count >> 8)); frame.append(UInt8(wire.count & 255))
            frame.append(wire.subdata(in: offset..<end))
            return frame
        }
    }
}

struct PairedOuterReassembler {
    private var request: Data?
    private var total = 0
    private var index = 0
    private var deadline: UInt64 = 0
    private var bytes = Data()
    mutating func reset() { request = nil; total = 0; index = 0; deadline = 0; bytes = Data() }
    mutating func receive(_ frame: Data, now: UInt64) throws -> Data? {
        guard frame.count > 24, frame.count <= 512, frame.prefix(4) == Data("POF1".utf8),
              frame[4] <= 1, frame[5] < 32 else { reset(); throw PairedOwnerError.invalid }
        let incomingRequest = frame.subdata(in: 6..<22)
        let incomingTotal = Int(frame[22]) * 256 + Int(frame[23])
        guard incomingTotal >= 128, incomingTotal <= 2432 else { reset(); throw PairedOwnerError.invalid }
        if frame[5] == 0 {
            guard request == nil, now <= UInt64.max - 5 else { reset(); throw PairedOwnerError.pending }
            request = incomingRequest; total = incomingTotal; deadline = now + 5
        }
        guard request == incomingRequest, total == incomingTotal,
              Int(frame[5]) == index, now < deadline,
              frame.count - 24 <= total - bytes.count else { reset(); throw PairedOwnerError.invalid }
        bytes.append(frame.dropFirst(24)); index += 1
        if frame[4] == 0 {
            guard bytes.count < total, index < 32 else { reset(); throw PairedOwnerError.invalid }
            return nil
        }
        guard bytes.count == total, bytes.subdata(in: 46..<62) == request else { reset(); throw PairedOwnerError.invalid }
        let complete = bytes; reset(); return complete
    }
}
