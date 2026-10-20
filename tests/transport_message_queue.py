#!/usr/bin/env python3
"""Execute production transport FIFO/send/invalidation with mock radio and real signed receipts."""
from pathlib import Path
import hashlib,json,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
p=ROOT/'iosApp/PairedIntegration/PebbleForwarding/Transport.swift'
s=p.read_text()
def extract(marker):
 a=s.index(marker);i=s.index('{',a)+1;depth=1
 while depth:
  depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
methods='\n'.join(extract(x) for x in ['        func messageReceived(', '        @MainActor private func drainMessages(', '        @MainActor private func sendMessage(', '        @MainActor private func acknowledgeReverse(', '        func sessionInvalidated(']).replace('private func','func')
pending=extract('        private struct PendingMessage:').replace('private struct','struct')
wire=extract('private enum DirectTransportWire').replace('private enum','enum',1)
fifo=(ROOT/'iosApp/PairedIntegration/PebbleForwarding/PairedFIFO.swift').read_text()
code=r'''
import Foundation
import CryptoKit
enum DirectTransportError: Error { case invalid, unavailable, notReady }
struct TransportMessage: Sendable {let sessionID: UUID;let data:Data}
enum AccessoryMessage {enum Result:Sendable {case success,failure(Failure)};enum Failure:Sendable {case transportUnavailable}}
final class AccessoryTransportSession:Sendable {enum Error:Swift.Error {case unavailable}}
struct PairedDiagnosticStatus {enum Event {case transportMessageReceived,transportWriteAcknowledged,transportFailure,transportInvalidated};static func record(_ event:Event,error:(any Error)? = nil){}}
struct Reverse {mutating func reset(){}}
@MainActor final class WatchAccessoryLink {
 static let directTransportCharacteristic=1
 let key=P256.Signing.PrivateKey();let epoch=Data(repeating:7,count:32)
 var packets:[Data]=[];var writes:[Int]=[];var reverseACKs=0
 var waiter:CheckedContinuation<Data,any Error>?;var retired=false
 var lateSuccess=false
 func writeFramedDirect(_ packet:Data,transferID:Data,to:Int) async throws {guard !retired else {throw DirectTransportError.unavailable};packets.append(packet);writes.append(Int(packet.last!))}
 func receipt() throws -> Data {let packet=packets.last!;var r=Data("NTR1".utf8);r.append(epoch);r.append(packet.subdata(in:36..<52));r.append(Data(SHA256.hash(data:packet)));r.append(try key.signature(for:r).rawRepresentation);return r}
 func readDirect(from:Int) async throws -> Data {try await withCheckedThrowingContinuation{waiter=$0}}
 func release() throws {let c=waiter;waiter=nil;c?.resume(returning:try receipt())}
 func writeDirect(_ frames:[Data],to:Int) async throws {reverseACKs+=1}
 func releaseCallbacks(_ token:UUID){}
 func retireDirect(){retired=true;let c=waiter;waiter=nil;if lateSuccess {c?.resume(returning:try! receipt())}else{c?.resume(throwing:DirectTransportError.unavailable)}}
}
final class Results:@unchecked Sendable {
 private let lock=NSLock();private var storage:[Int:[Bool]]=[:]
 func record(_ id:Int,_ r:AccessoryMessage.Result){lock.lock();defer{lock.unlock()};let ok:Bool;if case .success=r {ok=true}else{ok=false};storage[id,default:[]].append(ok)}
 var values:[Int:[Bool]]{lock.lock();defer{lock.unlock()};return storage}
}
final class Handler:Sendable {
'''+pending+r'''
 @MainActor final class State {
 var pending:PairedFIFO<PendingMessage>=[];var sending=false;var invalidated=false
 var link:WatchAccessoryLink?;var callbackToken:UUID?;var session:AccessoryTransportSession?;var epoch:Data?;var reverse=Reverse()
 nonisolated init(){}
 }
 let state=State()
 @MainActor init(_ link:WatchAccessoryLink){state.link=link;state.epoch=link.epoch}
 @MainActor func ready(until:TimeInterval) async throws -> (WatchAccessoryLink,Data,Data){guard !state.invalidated,let l=state.link else{throw DirectTransportError.unavailable};return(l,l.epoch,l.key.publicKey.x963Representation)}
'''+methods+r'''
}
@main struct Run {
 @MainActor static func until(_ predicate:() -> Bool) async {for _ in 0..<20000 {if predicate(){return};await Task.yield()};fatalError("bounded scheduler wait expired")}
 @MainActor static func enqueue(_ h:Handler,_ r:Results,_ id:Int) {h.messageReceived(.init(sessionID:UUID(),data:Data([UInt8(id%251)]))){r.record(id,$0)}}
 @MainActor static func main() async throws {
  for count in [5,10] {
   let link=WatchAccessoryLink(),r=Results();let h=Handler(link)
   enqueue(h,r,0);await until{link.waiter != nil}
   for i in 1..<count {enqueue(h,r,i);await until{h.state.pending.count==i}}
   precondition(link.writes==[0] && r.values.isEmpty)
   try await h.acknowledgeReverse(.init(epoch:link.epoch,replyID:Data(repeating:1,count:16),sessionID:UUID(),data:Data()),digest:Data(repeating:2,count:32),over:link)
   precondition(link.reverseACKs==1 && r.values.isEmpty)
   for i in 0..<count {await until{link.waiter != nil};try link.release();await until{r.values[i] != nil}}
   precondition(link.writes==Array(0..<count));precondition(r.values.count==count && r.values.values.allSatisfy{$0==[true]})
  }
  do {
   let link=WatchAccessoryLink(),r=Results();let h=Handler(link)
   enqueue(h,r,0);await until{link.waiter != nil}
   for i in 1...128 {enqueue(h,r,i);await until{h.state.pending.count==i}}
   enqueue(h,r,129);await until{r.values[129] != nil};precondition(r.values[129]==[false] && link.writes==[0])
   h.sessionInvalidated(error:nil);await until{r.values.count==130}
   precondition(r.values.values.allSatisfy{$0==[false]} && h.state.pending.isEmpty)
   enqueue(h,r,130);await until{r.values[130] != nil};precondition(r.values[130]==[false])
  }
  do {
   let link=WatchAccessoryLink(),r=Results();link.lateSuccess=true;let h=Handler(link)
   enqueue(h,r,0);await until{link.waiter != nil};enqueue(h,r,1);await until{h.state.pending.count==1}
   h.sessionInvalidated(error:nil);await until{r.values.count==2};precondition(r.values[0]==[false] && r.values[1]==[false])
   precondition(link.writes==[0])
  }
  print("PASS: actual FIFO/send methods; 5+10 ordered signed receipts; reverse ACK while active; overflow; active/queued invalidation exactly once; late valid receipt cannot succeed")
 }
}
'''
with tempfile.TemporaryDirectory(prefix='transport-fifo-') as tmp:
 f=Path(tmp)/'Queue.swift';f.write_text(fifo+'\n'+code+'\n'+wire)
 subprocess.run(['xcrun','swiftc','-parse-as-library',str(f),'-o',tmp+'/check'],check=True)
 subprocess.run([tmp+'/check'],check=True,timeout=30)
print(json.dumps({'source_sha256':hashlib.sha256(s.encode()).hexdigest(),'scope':'host mock radio; actual queue/send/receipt verification; no target proof'}))
