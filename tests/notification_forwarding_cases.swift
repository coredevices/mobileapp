
func check(_ condition: @autoclosure () -> Bool, _ message: String) {
    if !condition() { fatalError(message) }
}
func replyBytes(_ requestID: [UInt8], _ token: String, _ action: String, text: String? = nil) -> Data {
    let tokenBytes = Array(token.utf8)
    let actionBytes = Array(action.utf8)
    let textBytes = Array((text ?? "").utf8)
    return Data(Array("NAR1".utf8) + requestID + [UInt8(tokenBytes.count)] + tokenBytes
                + [UInt8(actionBytes.count)] + actionBytes
                + UInt16(textBytes.count).littleEndianBytes + textBytes)
}
func waitForFrame(_ session: MockSession, _ index: Int) async -> [UInt8] {
    for _ in 0..<100 {
        let sent = session.snapshot.sent
        if sent.count > index { return Array(sent[index]) }
        try? await Task.sleep(for: .milliseconds(10))
    }
    fatalError("reply result was not sent")
}
func waitForPausedResponse(_ session: MockSession) async {
    for _ in 0..<100 {
        if session.snapshot.responseWaiter != nil { return }
        try? await Task.sleep(for: .milliseconds(10))
    }
    fatalError("mock Apple response did not pause")
}
func waitForPausedSend(_ session: MockSession) async {
    for _ in 0..<100 {
        if session.snapshot.sendWaiter != nil { return }
        try? await Task.sleep(for: .milliseconds(10))
    }
    fatalError("mock Apple send did not pause")
}
func expectResult(_ frame: [UInt8], _ id: [UInt8], _ accepted: Bool) {
    check(frame == [4] + id + [accepted ? 1 : 0], "incorrect reply result bytes")
}
func runCases() async throws {
    let session = MockSession()
    let handler = NotificationForwardingHandler()
    activate(handler, session)
    let identifier = AccessoryNotification.Identifier(sourceIdentifier: "com.example.messages", notificationIdentifier: "message-1")
    let notification = AccessoryNotification(identifier: identifier, title: "Message", subtitle: nil,
        body: .init(string: "hello"), sourceName: "Messages",
        actions: [.init(identifier: "reply", title: "Reply", type: .textInput)])
    let delivered = try await handler.addNotification(notification, alertingContext: .init(shouldAlert: true))
    check(delivered, "present not sent")
    let token = PairedNotificationToken.make(namespace: Data("PebbleD.SDKNotificationIdentity.v1".utf8),
        source: identifier.sourceIdentifier, notification: identifier.notificationIdentifier)
    let id1 = Array(0..<16).map(UInt8.init)
    let message1 = TransportMessage(sessionID: session.sessionID, data: replyBytes(id1, token, "reply", text: "yes"))
    handler.messageHandler(message1)
    expectResult(await waitForFrame(session, 1), id1, true)
    check(session.snapshot.responses.count == 1, "successful action not submitted once")
    check(session.snapshot.responses[0].userText == "yes", "reply text changed")
    handler.messageHandler(message1)
    expectResult(await waitForFrame(session, 2), id1, true)
    check(session.snapshot.responses.count == 1, "duplicate action submitted again")

    session.state.withLock { $0.fail = true }
    let id2 = Array(16..<32).map(UInt8.init)
    let message2 = TransportMessage(sessionID: session.sessionID, data: replyBytes(id2, token, "reply", text: "no"))
    handler.messageHandler(message2)
    expectResult(await waitForFrame(session, 3), id2, false)
    check(session.snapshot.responses.count == 2, "throwing API call was not attempted")
    handler.messageHandler(message2)
    expectResult(await waitForFrame(session, 4), id2, false)
    check(session.snapshot.responses.count == 2, "throwing action retried despite cache")

    let id3 = Array(32..<48).map(UInt8.init)
    handler.messageHandler(.init(sessionID: session.sessionID, data: replyBytes(id3, token, "unauthorized")))
    expectResult(await waitForFrame(session, 5), id3, false)
    check(session.snapshot.responses.count == 2, "unauthorized action reached API")
    let id4 = Array(48..<64).map(UInt8.init)
    handler.messageHandler(.init(sessionID: session.sessionID, data: replyBytes(id4, "missing", "reply")))
    expectResult(await waitForFrame(session, 6), id4, false)
    check(session.snapshot.responses.count == 2, "unmapped action reached API")

    handler.messageHandler(.init(sessionID: UUID(), data: replyBytes(Array(64..<80).map(UInt8.init), token, "reply")))
    try await Task.sleep(for: .milliseconds(50))
    check(session.snapshot.sent.count == 7 && session.snapshot.responses.count == 2,
          "wrong-session message changed action or result stream")

    // A reused ID with changed authenticated action bytes is not the same request.
    // It must neither execute another action nor replay a prior success receipt.
    handler.messageHandler(.init(sessionID: session.sessionID,
        data: replyBytes(id1, token, "reply", text: "changed")))
    expectResult(await waitForFrame(session, 7), id1, false)
    check(session.snapshot.responses.count == 2, "changed-body duplicate executed")

    handler.didInvalidate()
    let next = MockSession()
    next.state.withLock { $0.fail = false }
    activate(handler, next)
    let nextDelivered = try await handler.addNotification(notification, alertingContext: .init(shouldAlert: true))
    check(nextDelivered, "reactivated present failed")
    let nextToken = PairedNotificationToken.make(namespace: Data("PebbleD.SDKNotificationIdentity.v1".utf8),
        source: identifier.sourceIdentifier, notification: identifier.notificationIdentifier)
    handler.messageHandler(.init(sessionID: next.sessionID, data: replyBytes(id1, nextToken, "reply")))
    expectResult(await waitForFrame(next, 1), id1, true)
    check(next.snapshot.responses.count == 1, "new session reused old idempotency cache")
    handler.didInvalidate()
    print("notification forwarding host cases passed")
}
func runConcurrentCases() async throws {
    let handler = NotificationForwardingHandler()
    let session = MockSession()
    activate(handler, session)
    let identifier = AccessoryNotification.Identifier(sourceIdentifier: "com.example.messages", notificationIdentifier: "parallel")
    let notification = AccessoryNotification(identifier: identifier, title: "Parallel", subtitle: nil,
        body: nil, sourceName: "Messages", actions: [.init(identifier: "reply", title: "Reply", type: .textInput)])
    let delivered = try await handler.addNotification(notification, alertingContext: .init(shouldAlert: true))
    check(delivered, "parallel present failed")
    let token = PairedNotificationToken.make(namespace: Data("PebbleD.SDKNotificationIdentity.v1".utf8),
        source: identifier.sourceIdentifier, notification: identifier.notificationIdentifier)
    let id = Array(80..<96).map(UInt8.init)
    let reply = TransportMessage(sessionID: session.sessionID, data: replyBytes(id, token, "reply", text: "one"))
    session.state.withLock { $0.pauseResponse = true }
    handler.messageHandler(reply)
    await waitForPausedResponse(session)
    handler.messageHandler(reply)
    try await Task.sleep(for: .milliseconds(50))
    check(session.snapshot.responses.count == 1, "simultaneous duplicate executed a second action")
    check(session.snapshot.sent.count == 1, "result sent before Apple response completed")
    session.resumeResponse()
    expectResult(await waitForFrame(session, 1), id, true)
    handler.messageHandler(reply)
    expectResult(await waitForFrame(session, 2), id, true)
    check(session.snapshot.responses.count == 1, "cached duplicate executed a second action")

    let pendingID = Array(96..<112).map(UInt8.init)
    session.state.withLock { $0.pauseResponse = true }
    handler.messageHandler(.init(sessionID: session.sessionID,
        data: replyBytes(pendingID, token, "reply", text: "two")))
    await waitForPausedResponse(session)
    handler.didInvalidate()
    let replacement = MockSession()
    activate(handler, replacement)
    session.resumeResponse()
    try await Task.sleep(for: .milliseconds(50))
    check(session.snapshot.sent.count == 3, "invalidated session emitted stale result")
    check(replacement.snapshot.sent.isEmpty, "old result leaked into new session")
    let nextDelivered = try await handler.addNotification(notification, alertingContext: .init(shouldAlert: true))
    check(nextDelivered, "replacement present failed")
    let nextToken = PairedNotificationToken.make(namespace: Data("PebbleD.SDKNotificationIdentity.v1".utf8),
        source: identifier.sourceIdentifier, notification: identifier.notificationIdentifier)
    handler.messageHandler(.init(sessionID: replacement.sessionID,
        data: replyBytes(pendingID, nextToken, "reply", text: "three")))
    expectResult(await waitForFrame(replacement, 1), pendingID, true)
    check(replacement.snapshot.responses.count == 1, "old pending ID blocked replacement session")
    handler.didInvalidate()
}
func runPriorityCase() async throws {
    let handler = NotificationForwardingHandler()
    let session = MockSession()
    activate(handler, session)
    let identifier = AccessoryNotification.Identifier(sourceIdentifier: "com.example.messages", notificationIdentifier: "priority")
    let notification = AccessoryNotification(identifier: identifier, title: "Priority", subtitle: nil,
        body: nil, sourceName: "Messages", actions: [.init(identifier: "reply", title: "Reply", type: .textInput)])
    let delivered = try await handler.addNotification(notification, alertingContext: .init(shouldAlert: true))
    check(delivered, "priority present failed")
    let token = PairedNotificationToken.make(namespace: Data("PebbleD.SDKNotificationIdentity.v1".utf8),
        source: identifier.sourceIdentifier, notification: identifier.notificationIdentifier)
    session.state.withLock { $0.pauseSend = true }
    handler.updateNotification(notification)
    await waitForPausedSend(session)
    handler.updateNotification(notification)
    handler.updateNotification(notification)
    let id = Array(112..<128).map(UInt8.init)
    handler.messageHandler(.init(sessionID: session.sessionID, data: replyBytes(id, token, "reply")))
    for _ in 0..<100 {
        if session.snapshot.responses.count == 1 { break }
        try? await Task.sleep(for: .milliseconds(10))
    }
    check(session.snapshot.responses.count == 1, "priority reply was not submitted")
    try await Task.sleep(for: .milliseconds(30))
    session.resumeSend()
    expectResult(await waitForFrame(session, 2), id, true)
    handler.didInvalidate()
}
func attribute(_ frame: [UInt8], _ tag: UInt8) -> [UInt8]? {
    guard frame.first == 1 else { return nil }
    var offset = 1
    while offset + 2 <= frame.count {
        let current = frame[offset]; let count = Int(frame[offset + 1]); offset += 2
        guard offset + count <= frame.count else { return nil }
        if current == tag { return Array(frame[offset..<offset + count]) }
        offset += count
    }
    return nil
}
func timestampBytes(_ value: UInt32) -> [UInt8] {
    [UInt8(truncatingIfNeeded: value >> 24), UInt8(truncatingIfNeeded: value >> 16),
     UInt8(truncatingIfNeeded: value >> 8), UInt8(truncatingIfNeeded: value)]
}
func runOrderingCases() async throws {
    let session = MockSession()
    let handler = NotificationForwardingHandler()
    activate(handler, session)
    let now: UInt32 = 1_780_000_000
    var notification = AccessoryNotification(
        identifier: .init(sourceIdentifier: "com.example.messages", notificationIdentifier: "ordering-1"),
        title: "Fresh", subtitle: nil, body: .init(string: "fresh content"), sourceName: "Messages",
        actions: [], deliveryDate: Date(timeIntervalSince1970: Double(now)))
    _ = try await handler.addNotification(notification, alertingContext: .init(shouldAlert: true))
    let fresh = Array(session.snapshot.sent[0])
    check(attribute(fresh, 6) == [1], "fresh alert policy changed")
    check(attribute(fresh, 10) == timestampBytes(now), "SDK delivery date lost")
    let old = now - 26 * 60
    notification.deliveryDate = Date(timeIntervalSince1970: Double(old))
    handler.updateNotification(notification)
    let updated = await waitForFrame(session, 1)
    check(attribute(updated, 6) == [0], "old update became alerting")
    check(attribute(updated, 10) == timestampBytes(old), "old update was re-dated")
    check(attribute(updated, 5) == attribute(fresh, 5), "same notification changed identity")
    let quietAlerted = try await handler.addNotification(notification, alertingContext: .init(shouldAlert: false))
    check(quietAlerted, "accepted quiet-add alert policy changed")
    check(attribute(Array(session.snapshot.sent[2]), 6) == [1], "quiet-add default policy changed")
    let historical = try await handler.addNotification(notification,
        alertingContext: .init(shouldAlert: false, notificationCanAlert: false, isSuppressedByFocus: false))
    let historicalFrame = Array(session.snapshot.sent[3])
    check(!historical && attribute(historicalFrame, 6) == [0], "ineligible historical add requested an alert")
    check(attribute(historicalFrame, 10) == timestampBytes(old), "historical content was redated or dropped")
    let focusAllowed = try await handler.addNotification(notification,
        alertingContext: .init(shouldAlert: false, notificationCanAlert: true, isSuppressedByFocus: false))
    check(focusAllowed && attribute(Array(session.snapshot.sent[4]), 6) == [1], "eligible Focus-allowed quiet add lost its alert")
    let focusSuppressed = try await handler.addNotification(notification,
        alertingContext: .init(shouldAlert: true, notificationCanAlert: true, isSuppressedByFocus: true))
    check(!focusSuppressed && attribute(Array(session.snapshot.sent[5]), 6) == [0], "actual Focus suppression was overridden")
    handler.didInvalidate()
    print("notification ordering/date/alert eligibility codec host cases passed")
}

func runDismissalCases() async throws {
    NotificationDismissalSettings.onBothDevices = false
    let session = MockSession(); let handler = NotificationForwardingHandler(); activate(handler, session)
    let identifier = AccessoryNotification.Identifier(sourceIdentifier: "com.example.messages", notificationIdentifier: "dismiss-1")
    let notification = AccessoryNotification(identifier: identifier, title: "Dismiss me", subtitle: nil, body: nil,
        sourceName: "Messages", actions: [.init(identifier: "pebbled-dismiss", title: "Real action", type: .plain)])
    _ = try await handler.addNotification(notification, alertingContext: .init(shouldAlert: true))
    let present = Array(session.snapshot.sent[0]); let token = String(decoding: attribute(present, 5)!, as: UTF8.self)
    guard let dismissalBytes = attribute(present, 11) else { fatalError("missing authenticated phone-dismiss identifier") }
    let dismissal = String(decoding: dismissalBytes, as: UTF8.self)
    check(dismissal != "pebbled-dismiss", "synthetic identifier collided with a real action")
    check(attribute(present, 12) == [0] && session.snapshot.policies.isEmpty, "atomic watch-only default missing or separate control traffic sent")
    let id0 = Array(repeating: UInt8(200), count: 16)
    handler.messageHandler(.init(sessionID: session.sessionID, data: replyBytes(id0, token, dismissal)))
    expectResult(await waitForFrame(session, 1), id0, false)
    check(session.snapshot.removals.isEmpty, "watch-only policy removed phone notification")
    NotificationDismissalSettings.onBothDevices = true
    check(session.snapshot.policies.isEmpty, "preference change emitted blocking standalone traffic")
    let id1 = Array(repeating: UInt8(201), count: 16)
    let message = TransportMessage(sessionID: session.sessionID, data: replyBytes(id1, token, dismissal))
    handler.messageHandler(message); expectResult(await waitForFrame(session, 2), id1, true)
    check(session.snapshot.removals.count == 1, "phone remove API was not called once")
    check(session.snapshot.removals[0][0].notificationIdentifier == identifier.notificationIdentifier && session.snapshot.removals[0][0].sourceIdentifier == identifier.sourceIdentifier, "wrong phone notification removed")
    check(session.snapshot.responses.isEmpty, "dismissal sent an app action instead of clearing Notification Center")
    handler.messageHandler(message); expectResult(await waitForFrame(session, 3), id1, true)
    check(session.snapshot.removals.count == 1, "duplicate dismissal repeated Apple API")
    let id2 = Array(repeating: UInt8(202), count: 16)
    handler.messageHandler(.init(sessionID: session.sessionID, data: replyBytes(id2, token, dismissal, text: "forged text")))
    expectResult(await waitForFrame(session, 4), id2, false)
    check(session.snapshot.removals.count == 1, "dismissal accepted text")
    session.state.withLock { $0.fail = true }
    let id3 = Array(repeating: UInt8(203), count: 16); let failed = TransportMessage(sessionID: session.sessionID, data: replyBytes(id3, token, dismissal))
    handler.messageHandler(failed); expectResult(await waitForFrame(session, 5), id3, false)
    handler.messageHandler(failed); expectResult(await waitForFrame(session, 6), id3, false)
    check(session.snapshot.removals.count == 2, "ambiguous failed removal was retried")
    session.state.withLock { $0.fail = false }
    handler.removeNotification(identifier: identifier)
    let removal = await waitForFrame(session, 7); check(removal.first == 2, "phone-to-watch removal was lost")
    let id4 = Array(repeating: UInt8(204), count: 16)
    handler.messageHandler(.init(sessionID: session.sessionID, data: replyBytes(id4, token, dismissal)))
    expectResult(await waitForFrame(session, 8), id4, false)
    check(session.snapshot.removals.count == 2, "stale dismissal mapping removed another item")
    // A phone removal callback may erase mappings before the Apple call returns.
    _ = try await handler.addNotification(notification, alertingContext: .init(shouldAlert: true))
    _ = await waitForFrame(session, 9)
    session.state.withLock { $0.pauseResponse = true }
    let id5 = Array(repeating: UInt8(205), count: 16)
    let paused = TransportMessage(sessionID: session.sessionID, data: replyBytes(id5, token, dismissal))
    handler.messageHandler(paused); await waitForPausedResponse(session); handler.messageHandler(paused)
    check(session.snapshot.removals.count == 3, "concurrent duplicate removal was executed")
    handler.removeNotification(identifier: identifier); _ = await waitForFrame(session, 10)
    session.resumeResponse(); expectResult(await waitForFrame(session, 11), id5, true)
    handler.messageHandler(paused); expectResult(await waitForFrame(session, 12), id5, true)
    check(session.snapshot.removals.count == 3, "callback race lost exactly-once result")
    _ = try await handler.addNotification(notification, alertingContext: .init(shouldAlert: true)); _ = await waitForFrame(session, 13)
    let collisionUpdate = AccessoryNotification(identifier: identifier, title: "Changed actions", subtitle: nil, body: nil, sourceName: "Messages",
        actions: [.init(identifier: dismissal, title: "Different app action", type: .plain)])
    handler.updateNotification(collisionUpdate); _ = await waitForFrame(session, 14)
    let id6 = Array(repeating: UInt8(206), count: 16)
    handler.messageHandler(.init(sessionID: session.sessionID, data: replyBytes(id6, token, dismissal)))
    expectResult(await waitForFrame(session, 15), id6, false)
    check(session.snapshot.responses.isEmpty && session.snapshot.removals.count == 3, "retired watch Dismiss became an unrelated app action")
    handler.didInvalidate(); NotificationDismissalSettings.onBothDevices = false
    check(NotificationDismissalSettings.state.withLock { $0.observers.isEmpty }, "preference observer outlived session")
    print("phone dismissal policy/correlation/removal host cases passed")
}

func runPolicyFailureCases() async throws {
    NotificationDismissalSettings.onBothDevices = false
    let session = MockSession(); session.state.withLock { $0.failPolicy = true }
    let handler = NotificationForwardingHandler(); activate(handler, session)
    let notification = AccessoryNotification(identifier: .init(sourceIdentifier: "com.example.messages", notificationIdentifier: "policy-failure"),
        title: "Notification", subtitle: nil, body: nil, sourceName: "Messages", actions: [])
    let alerted = try await handler.addNotification(notification, alertingContext: .init(shouldAlert: true))
    check(alerted && session.snapshot.sent.count == 1, "settings send failure blocked notification delivery")
    handler.didInvalidate()
    let paused = MockSession(); paused.state.withLock { $0.pausePolicy = true }
    let second = NotificationForwardingHandler(); activate(second, paused)
    let immediate = try await second.addNotification(notification, alertingContext: .init(shouldAlert: true))
    check(immediate && paused.snapshot.sent.count == 1, "settings send blocked notification completion")
    let another = try await second.addNotification(notification, alertingContext: .init(shouldAlert: true))
    check(another && paused.snapshot.sent.count == 2 && paused.snapshot.policies.isEmpty,
          "a pending policy frame blocked subsequent notification delivery")
    second.didInvalidate()
    print("policy failure/pending delivery isolation host cases passed")
}

func directCommand(_ id: [UInt8], source: String, notification: String, action: String = "", text: String = "", clear: Bool = false) -> Data {
    let source = Array(source.utf8); let notification = Array(notification.utf8)
    let action = Array(action.utf8); let text = Array(text.utf8)
    return Data(Array("NAR1".utf8) + id + Array((clear ? "PND1" : "PNI1").utf8)
        + [UInt8(source.count)] + source + [UInt8(notification.count)] + notification
        + [UInt8(action.count)] + action + UInt16(text.count).littleEndianBytes + text)
}
func runDirectClearAfterPhoneRemovalCases() async throws {
    NotificationDismissalSettings.onBothDevices = true
    let session = MockSession(); let handler = NotificationForwardingHandler(); activate(handler, session)
    let identifier = AccessoryNotification.Identifier(sourceIdentifier: "com.example.messages",
        notificationIdentifier: String(repeating: "x", count: 251) + "😀")
    let notification = AccessoryNotification(identifier: identifier, title: "Removed", subtitle: nil,
        body: nil, sourceName: "Messages", actions: [])
    handler.updateNotification(notification)
    let setupFrame = await waitForFrame(session, 0)
    check(setupFrame.first == 1, "setup update missing")
    handler.removeNotification(identifier: identifier)
    let removeFrame = await waitForFrame(session, 1)
    check(removeFrame.first == 2, "phone removal callback missing")
    let id = Array(repeating: UInt8(231), count: 16)
    let command = TransportMessage(sessionID: session.sessionID, data: directCommand(id,
        source: identifier.sourceIdentifier, notification: identifier.notificationIdentifier, clear: true))
    session.state.withLock { $0.pauseResponse = true }
    handler.messageHandler(command)
    await waitForPausedResponse(session)
    check(session.snapshot.removals.count == 1 && session.snapshot.removals[0].count == 1,
          "direct clear depended on removed volatile map")
    let actual = session.snapshot.removals[0][0]
    check(actual.sourceIdentifier == identifier.sourceIdentifier && actual.notificationIdentifier == identifier.notificationIdentifier,
          "clear after removal changed full SDK identity")
    handler.messageHandler(command)
    try await Task.sleep(for: .milliseconds(50))
    check(session.snapshot.removals.count == 1 && session.snapshot.sent.count == 2,
          "in-flight duplicate or premature clear result")
    session.resumeResponse()
    expectResult(await waitForFrame(session, 2), id, true)
    handler.messageHandler(command)
    expectResult(await waitForFrame(session, 3), id, true)
    check(session.snapshot.removals.count == 1, "accepted clear executed twice")

    session.state.withLock { $0.fail = true }
    let failedID = Array(repeating: UInt8(232), count: 16)
    let failed = TransportMessage(sessionID: session.sessionID, data: directCommand(failedID,
        source: identifier.sourceIdentifier, notification: identifier.notificationIdentifier, clear: true))
    handler.messageHandler(failed); expectResult(await waitForFrame(session, 4), failedID, false)
    handler.messageHandler(failed); expectResult(await waitForFrame(session, 5), failedID, false)
    check(session.snapshot.removals.count == 2, "ambiguous failed clear was repeated")
    session.state.withLock { $0.fail = false }
    NotificationDismissalSettings.onBothDevices = false
    let disabledID = Array(repeating: UInt8(233), count: 16)
    handler.messageHandler(.init(sessionID: session.sessionID, data: directCommand(disabledID,
        source: identifier.sourceIdentifier, notification: identifier.notificationIdentifier, clear: true)))
    expectResult(await waitForFrame(session, 6), disabledID, false)
    check(session.snapshot.removals.count == 2, "disabled mode reached removal API")
    NotificationDismissalSettings.onBothDevices = true
    session.state.withLock { $0.pauseResponse = true }
    let pendingID = Array(repeating: UInt8(234), count: 16)
    handler.messageHandler(.init(sessionID: session.sessionID, data: directCommand(pendingID,
        source: identifier.sourceIdentifier, notification: identifier.notificationIdentifier, clear: true)))
    await waitForPausedResponse(session)
    handler.didInvalidate()
    let replacement = MockSession(); activate(handler, replacement)
    session.resumeResponse()
    handler.messageHandler(command) // retired session cannot adopt the replacement
    try await Task.sleep(for: .milliseconds(50))
    check(session.snapshot.sent.count == 7 && replacement.snapshot.sent.isEmpty && replacement.snapshot.removals.isEmpty,
          "retired clear result or request crossed sessions")
    handler.didInvalidate()
    NotificationDismissalSettings.onBothDevices = false
    print("direct clear after phone removal: exact identity, awaited completion, duplicate/failure/mode/session cases passed")
}

func runDirectIdentityCases() async throws {
    // A replacement handler has no prior notification map, as after an extension restart.
    let session = MockSession(); let handler = NotificationForwardingHandler(); activate(handler, session)
    let source = "com.example.messages"; let opaque = String(repeating: "x", count: 251) + "😀"
    let id = Array(repeating: UInt8(220), count: 16)
    let command = directCommand(id, source: source, notification: opaque, action: "reply", text: "exact reply")
    handler.messageHandler(.init(sessionID: session.sessionID, data: command))
    expectResult(await waitForFrame(session, 0), id, true)
    check(session.snapshot.responses[0].notificationIdentifier == opaque, "SDK opaque identity was changed")
    handler.messageHandler(.init(sessionID: session.sessionID, data: command))
    expectResult(await waitForFrame(session, 1), id, true)
    check(session.snapshot.responses.count == 1, "direct action repeated after accepted result")
    handler.messageHandler(.init(sessionID: session.sessionID,
        data: directCommand(id, source: source, notification: opaque, action: "reply", text: "changed")))
    expectResult(await waitForFrame(session, 2), id, false)
    check(session.snapshot.responses.count == 1, "changed direct payload replayed action")
    NotificationDismissalSettings.onBothDevices = true
    let clearID = Array(repeating: UInt8(221), count: 16)
    handler.messageHandler(.init(sessionID: session.sessionID, data: directCommand(clearID, source: source, notification: opaque, clear: true)))
    expectResult(await waitForFrame(session, 3), clearID, true)
    check(session.snapshot.removals.count == 1 && session.snapshot.removals[0][0].notificationIdentifier == opaque,
          "direct clear did not use exact SDK identity")
    NotificationDismissalSettings.onBothDevices = false
    let disabledID = Array(repeating: UInt8(222), count: 16)
    handler.messageHandler(.init(sessionID: session.sessionID, data: directCommand(disabledID, source: source, notification: opaque, clear: true)))
    expectResult(await waitForFrame(session, 4), disabledID, false)
    check(session.snapshot.removals.count == 1, "direct clear ignored watch-only setting")
    do {
        _ = try AccessoryNotificationCodec.decodeReply(Array(directCommand(id, source: source, notification: opaque, text: "invalid", clear: true)))
        fatalError("clear with text decoded")
    } catch {}
    let n = ForwardedNotification(identifier: "display", title: nil, subtitle: nil, body: nil, sourceName: nil,
        sourceIdentifier: source, shouldAlert: true, actions: [], originalNotificationIdentifier: opaque)
    check(attribute(AccessoryNotificationCodec.encode(.present(n)), 13) == Array(opaque.utf8), "maximum-size opaque identity truncated")
    var tooLong = n; tooLong.originalNotificationIdentifier = opaque + "x"
    check(attribute(AccessoryNotificationCodec.encode(.present(tooLong)), 13) == nil, "oversized opaque identity partially sent")
    handler.didInvalidate()
    print("self-contained SDK identity/reply/clear/digest regression cases passed")
}
func runReceiptCases() async throws {
    let session = MockSession(); session.state.withLock { $0.autoReceipt = false }
    let handler = NotificationForwardingHandler(replySuggester: StoredReplies(), receiptTimeout: .seconds(2))
    activate(handler, session)
    let result = Mutex([Bool]())
    let n = AccessoryNotification(identifier: .init(sourceIdentifier: "com.example.messages", notificationIdentifier: "receipt-1"),
        title: "Receipt", subtitle: nil, body: nil, sourceName: "Messages", actions: [])
    let first = Task { let v = try await handler.addNotification(n, alertingContext: .init(shouldAlert: true)); result.withLock { $0.append(v) } }
    let frame = await waitForFrame(session, 0); let event = attribute(frame, 14)!
    try await Task.sleep(for: .milliseconds(30))
    check(result.withLock { $0.isEmpty }, "transport acceptance falsely reported an alert")
    handler.messageHandler(.init(sessionID: session.sessionID, data: Data(Array("NDA1".utf8) + Array(repeating: 99, count: 16) + [1])))
    handler.messageHandler(.init(sessionID: UUID(), data: Data(Array("NDA1".utf8) + event + [1])))
    try await Task.sleep(for: .milliseconds(30))
    check(result.withLock { $0.isEmpty }, "wrong event/session resolved receipt")
    // A receipt wait must not hold the delivery or action-result FIFO.
    let second = Task { try await handler.addNotification(n, alertingContext: .init(shouldAlert: true)) }
    let secondFrame = await waitForFrame(session, 1); let secondEvent = attribute(secondFrame, 14)!
    let actionID = Array(repeating: UInt8(223), count: 16)
    handler.messageHandler(.init(sessionID: session.sessionID,
        data: directCommand(actionID, source: "com.example.messages", notification: "receipt-1", action: "reply", text: "yes")))
    expectResult(await waitForFrame(session, 2), actionID, true)
    handler.messageHandler(.init(sessionID: session.sessionID, data: Data(Array("NDA1".utf8) + event + [0])))
    try await first.value
    check(result.withLock { $0 == [false] }, "receiver didAlert=false ignored")
    handler.messageHandler(.init(sessionID: session.sessionID, data: Data(Array("NDA1".utf8) + event + [1])))
    handler.messageHandler(.init(sessionID: session.sessionID, data: Data(Array("NAR1".utf8) + Array(repeating: 0, count: 16) + Array("NDA1".utf8) + secondEvent + [1])))
    let secondResult = try await second.value; check(secondResult, "wrapped receiver alert receipt lost")
    let pending = Task { try await handler.addNotification(n, alertingContext: .init(shouldAlert: true)) }
    _ = await waitForFrame(session, 3); handler.didInvalidate()
    let invalidated = try await pending.value; check(!invalidated, "invalidation abandoned receipt waiter")
    let timeoutSession = MockSession(); timeoutSession.state.withLock { $0.autoReceipt = false }
    let timeoutHandler = NotificationForwardingHandler(replySuggester: StoredReplies(), receiptTimeout: .milliseconds(40))
    activate(timeoutHandler, timeoutSession)
    let expired = try await timeoutHandler.addNotification(n, alertingContext: .init(shouldAlert: true))
    check(!expired && timeoutSession.snapshot.sent.count == 1, "receipt timeout did not fail closed")
    timeoutHandler.didInvalidate()
    print("receiver receipt/session/timeout/queue regression cases passed")
}

func runConcurrentReceiptBurstCases() async throws {
    let session = MockSession(); session.state.withLock { $0.autoReceipt = false }
    let handler = NotificationForwardingHandler(replySuggester: StoredReplies(), receiptTimeout: .seconds(5))
    activate(handler, session)
    let completed = Mutex([Bool]())
    for wave in 0..<2 {
        let tasks = (0..<5).map { index in
            Task {
                let notification = AccessoryNotification(
                    identifier: .init(sourceIdentifier: "com.example.burst",
                                      notificationIdentifier: "burst-\(wave)-\(index)"),
                    title: "Burst", subtitle: nil, body: nil, sourceName: "Burst", actions: [])
                let delivered = try await handler.addNotification(notification, alertingContext: .init(shouldAlert: true))
                completed.withLock { $0.append(delivered) }
                return delivered
            }
        }
        var events: [[UInt8]] = []
        for index in 0..<5 {
            events.append(attribute(await waitForFrame(session, wave * 5 + index), 14)!)
        }
        check(Set(events.map(Data.init)).count == 5, "concurrent notifications reused a receipt ID")
        handler.messageHandler(.init(sessionID: session.sessionID,
            data: Data(Array("NDA1".utf8) + Array(repeating: 0xaa, count: 16) + [1])))
        handler.messageHandler(.init(sessionID: UUID(),
            data: Data(Array("NDA1".utf8) + events[0] + [1])))
        try await Task.sleep(for: .milliseconds(25))
        check(completed.withLock { $0.count } == wave * 5, "unrelated burst receipt completed an add")
        for event in events.reversed() {
            handler.messageHandler(.init(sessionID: session.sessionID,
                data: Data(Array("NDA1".utf8) + event + [1])))
        }
        handler.messageHandler(.init(sessionID: session.sessionID,
            data: Data(Array("NDA1".utf8) + events[0] + [0])))
        for task in tasks { let delivered = try await task.value; check(delivered, "reversed burst receipt was lost") }
        check(completed.withLock { $0.count } == (wave + 1) * 5, "burst add callback remained pending")
    }
    check(session.snapshot.sent.count == 10, "concurrent bursts did not dispatch all notifications")
    check(completed.withLock { $0.allSatisfy { $0 } }, "duplicate receipt changed an accepted result")
    handler.didInvalidate()
    print("two concurrent five-notification receipt bursts passed")
}

actor PolicyReplyPause: ReplySuggesting {
    var waiter: CheckedContinuation<Void, Never>?
    var paused: Bool { waiter != nil }
    func replies(for notification: ForwardedNotification) async -> [String] {
        await withCheckedContinuation { waiter = $0 }
        return []
    }
    func resume() { let pending = waiter; waiter = nil; pending?.resume() }
}
func waitPolicy(_ session: MockSession, count: Int) async {
    for _ in 0..<100 {
        if session.snapshot.policies.count >= count { return }
        try? await Task.sleep(for: .milliseconds(10))
    }
    fatalError("preference change did not dispatch policy")
}
func runImmediatePolicyCases() async throws {
    NotificationDismissalSettings.onBothDevices = false
    let session = MockSession(); session.state.withLock { $0.pausePolicy = true }
    let handler = NotificationForwardingHandler(); activate(handler, session)
    check(session.snapshot.policies.isEmpty, "activation sent an unsolicited policy")
    let oldCallback = NotificationDismissalSettings.state.withLock { Array($0.observers.values) }
    NotificationDismissalSettings.onBothDevices = true
    await waitPolicy(session, count: 1)
    for _ in 0..<100 {
        if session.snapshot.policyWaiter != nil { break }
        try? await Task.sleep(for: .milliseconds(10))
    }
    check(session.snapshot.policyWaiter != nil, "policy did not pause")
    check(session.snapshot.policies == [Data([5, 1])], "policy frame is not exact type5+true")
    let n = AccessoryNotification(identifier: .init(sourceIdentifier: "com.example.policy", notificationIdentifier: "policy"),
        title: "Policy", subtitle: nil, body: nil, sourceName: "Policy", actions: [])
    let alerted = try await handler.addNotification(n, alertingContext: .init(shouldAlert: true))
    check(alerted && session.snapshot.sent.count == 1, "paused policy blocked present")
    let request = [UInt8](repeating: 214, count: 16)
    handler.messageHandler(.init(sessionID: session.sessionID,
        data: directCommand(request, source: "com.example.policy", notification: "policy", action: "reply", text: "yes")))
    expectResult(await waitForFrame(session, 1), request, true)
    handler.didInvalidate()
    check(NotificationDismissalSettings.state.withLock { $0.observers.isEmpty }, "observer survived invalidation")
    let replacement = MockSession(); activate(handler, replacement)
    for callback in oldCallback { callback() }
    let waiter = session.state.withLock { state -> CheckedContinuation<Void, Never>? in
        let w = state.policyWaiter; state.policyWaiter = nil; state.pausePolicy = false; return w
    }
    waiter?.resume()
    try await Task.sleep(for: .milliseconds(30))
    check(replacement.snapshot.policies.isEmpty && replacement.snapshot.sent.isEmpty,
          "old observer or pending policy crossed session generation")
    replacement.state.withLock { $0.failPolicy = true }
    NotificationDismissalSettings.onBothDevices = false
    await waitPolicy(replacement, count: 1)
    check(replacement.snapshot.policies == [Data([5, 0])], "false policy frame changed")
    let afterFailure = try await handler.addNotification(n, alertingContext: .init(shouldAlert: true))
    check(afterFailure, "failed policy blocked notification")
    handler.didInvalidate()
    let pause = PolicyReplyPause(); let queued = NotificationForwardingHandler(replySuggester: pause)
    let queuedSession = MockSession(); activate(queued, queuedSession)
    let textNotification = AccessoryNotification(identifier: n.identifier, title: n.title, subtitle: nil, body: nil,
        sourceName: n.sourceName, actions: [.init(identifier: "reply", title: "Reply", type: .textInput)])
    queued.updateNotification(textNotification)
    for _ in 0..<100 { if await pause.paused { break }; try await Task.sleep(for: .milliseconds(10)) }
    let didPause = await pause.paused
    check(didPause, "reply curation did not pause")
    NotificationDismissalSettings.onBothDevices = true
    await waitPolicy(queuedSession, count: 1)
    await pause.resume()
    let frame = await waitForFrame(queuedSession, 0)
    check(attribute(frame, 12) == [1], "queued present restored the old dismissal policy")
    queued.didInvalidate()
    NotificationDismissalSettings.onBothDevices = false
    print("immediate policy/frame/isolation/generation/send-time refresh cases passed")
}

func runReceiptSessionBindingCases() async throws {
    let handler = NotificationForwardingHandler(replySuggester: StoredReplies(), receiptTimeout: .seconds(5))
    let firstSession = MockSession()
    firstSession.state.withLock { $0.autoReceipt = false }
    activate(handler, firstSession)
    let notification = AccessoryNotification(
        identifier: .init(sourceIdentifier: "com.example.binding", notificationIdentifier: "binding"),
        title: "Binding", subtitle: nil, body: nil, sourceName: "Binding",
        actions: [.init(identifier: "reply", title: "Reply", type: .plain)])
    let pending = Task { try await handler.addNotification(notification, alertingContext: .init(shouldAlert: true)) }
    let event = attribute(await waitForFrame(firstSession, 0), 14)!
    let action = directCommand(Array(repeating: 91, count: 16), source: "com.example.binding",
                               notification: "binding", action: "reply")
    let on26: (NotificationsForwarding.Session) -> UUID? = { _ in nil }
    handler.handleMessage(.init(sessionID: firstSession.sessionID, data: action), expectedSessionID: on26)
    handler.handleMessage(.init(sessionID: firstSession.sessionID,
        data: Data(Array("NDA1".utf8) + Array(repeating: 9, count: 16) + [1])), expectedSessionID: on26)
    handler.handleMessage(.init(sessionID: firstSession.sessionID,
        data: Data(Array("NDA1".utf8) + event)), expectedSessionID: on26)
    handler.handleMessage(.init(sessionID: firstSession.sessionID,
        data: Data(Array("NDA1".utf8) + event + [2])), expectedSessionID: on26)
    handler.handleMessage(.init(sessionID: firstSession.sessionID, data: action), expectedSessionID: on26)
    try await Task.sleep(for: .milliseconds(30))
    check(firstSession.snapshot.responses.isEmpty && firstSession.snapshot.sent.count == 1,
          "26.5 action or malformed/stale receipt bound a session")
    handler.handleMessage(.init(sessionID: firstSession.sessionID,
        data: Data(Array("NDA1".utf8) + event + [1])), expectedSessionID: on26)
    let firstDelivered = try await pending.value
    check(firstDelivered, "current receipt did not bind the 26.5 session")
    handler.handleMessage(.init(sessionID: UUID(), data: action), expectedSessionID: on26)
    try await Task.sleep(for: .milliseconds(30))
    check(firstSession.snapshot.responses.isEmpty, "mismatched 26.5 reply reached Apple")
    handler.handleMessage(.init(sessionID: firstSession.sessionID, data: action), expectedSessionID: on26)
    expectResult(await waitForFrame(firstSession, 1), Array(repeating: 91, count: 16), true)
    check(firstSession.snapshot.responses.count == 1, "bound 26.5 reply was not submitted once")

    handler.didInvalidate()
    let secondSession = MockSession()
    secondSession.state.withLock { $0.autoReceipt = false }
    activate(handler, secondSession)
    handler.handleMessage(.init(sessionID: firstSession.sessionID,
        data: Data(Array("NDA1".utf8) + event + [1])), expectedSessionID: on26)
    handler.handleMessage(.init(sessionID: secondSession.sessionID, data: action), expectedSessionID: on26)
    try await Task.sleep(for: .milliseconds(30))
    check(secondSession.snapshot.responses.isEmpty, "old receipt or first action survived reactivation")
    let nextPending = Task { try await handler.addNotification(notification, alertingContext: .init(shouldAlert: true)) }
    let nextEvent = attribute(await waitForFrame(secondSession, 0), 14)!
    handler.handleMessage(.init(sessionID: secondSession.sessionID,
        data: Data(Array("NDA1".utf8) + nextEvent + [0])), expectedSessionID: on26)
    let secondAlerted = try await nextPending.value
    check(!secondAlerted, "quiet receipt did not complete after reactivation")
    handler.handleMessage(.init(sessionID: firstSession.sessionID, data: action), expectedSessionID: on26)
    handler.handleMessage(.init(sessionID: secondSession.sessionID, data: action), expectedSessionID: on26)
    expectResult(await waitForFrame(secondSession, 1), Array(repeating: 91, count: 16), true)
    check(secondSession.snapshot.responses.count == 1, "new 26.5 generation accepted wrong session")
    handler.didInvalidate()

    let expiring = MockSession(); expiring.state.withLock { $0.autoReceipt = false }
    let timeoutHandler = NotificationForwardingHandler(replySuggester: StoredReplies(), receiptTimeout: .milliseconds(40))
    activate(timeoutHandler, expiring)
    let expiredPending = Task { try await timeoutHandler.addNotification(notification, alertingContext: .init(shouldAlert: true)) }
    let expiredEvent = attribute(await waitForFrame(expiring, 0), 14)!
    let expiredResult = try await expiredPending.value
    check(!expiredResult, "first 26.5 receipt did not time out")
    timeoutHandler.handleMessage(.init(sessionID: expiring.sessionID,
        data: Data(Array("NDA1".utf8) + expiredEvent + [1])), expectedSessionID: on26)
    timeoutHandler.handleMessage(.init(sessionID: expiring.sessionID, data: action), expectedSessionID: on26)
    try await Task.sleep(for: .milliseconds(30))
    check(expiring.snapshot.responses.isEmpty, "expired receipt bound a 26.5 session")
    let freshPending = Task { try await timeoutHandler.addNotification(notification, alertingContext: .init(shouldAlert: true)) }
    let freshEvent = attribute(await waitForFrame(expiring, 1), 14)!
    timeoutHandler.handleMessage(.init(sessionID: expiring.sessionID,
        data: Data(Array("NDA1".utf8) + freshEvent + [1])), expectedSessionID: on26)
    let freshResult = try await freshPending.value
    check(freshResult, "fresh receipt failed after expired receipt")
    timeoutHandler.didInvalidate()
    print("26.5 receipt session binding cases passed")
}

Task {
    do { try await runReceiptSessionBindingCases(); try await runDirectClearAfterPhoneRemovalCases(); try await runImmediatePolicyCases(); try await runConcurrentReceiptBurstCases(); try await runReceiptCases(); try await runDirectIdentityCases(); try await runPolicyFailureCases(); try await runCases(); try await runConcurrentCases(); try await runPriorityCase(); try await runOrderingCases(); try await runDismissalCases(); exit(0) }
    catch { fputs("host case failed: \(error)\n", stderr); exit(1) }
}
dispatchMain()
