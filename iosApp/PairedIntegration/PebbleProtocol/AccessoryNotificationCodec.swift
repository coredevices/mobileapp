/// A notification iOS forwarded through AccessoryNotifications, as the watch is
/// sent it.
package struct ForwardedNotification: Equatable, Sendable {
    package struct Action: Equatable, Sendable {
        package var identifier: String
        package var title: String
        /// A text-input action: the watch offers its reply menu and sends the
        /// chosen text back with the action.
        package var collectsText: Bool

        package init(identifier: String, title: String, collectsText: Bool) {
            self.identifier = identifier
            self.title = title
            self.collectsText = collectsText
        }
    }

    package var identifier: String
    package var title: String?
    package var subtitle: String?
    package var body: String?
    package var sourceName: String?
    package var sourceIdentifier: String?
    package var shouldAlert: Bool
    package var deliveryTimestamp: UInt32?
    package var dismissalActionIdentifier: String?
    package var originalNotificationIdentifier: String?
    package var dismissOnBothDevices: Bool?
    package var deliveryReceiptID: [UInt8]?
    package var actions: [Action]
    /// What the watch offers to send from each of its text-input actions, in
    /// place of its own replies.
    package var replies: [String]

    package init(
        identifier: String,
        title: String?,
        subtitle: String?,
        body: String?,
        sourceName: String?,
        sourceIdentifier: String?,
        shouldAlert: Bool,
        actions: [Action],
        replies: [String] = [],
        deliveryTimestamp: UInt32? = nil,
        dismissalActionIdentifier: String? = nil,
        originalNotificationIdentifier: String? = nil,
        dismissOnBothDevices: Bool? = nil,
        deliveryReceiptID: [UInt8]? = nil
    ) {
        self.identifier = identifier
        self.title = title
        self.subtitle = subtitle
        self.body = body
        self.sourceName = sourceName
        self.sourceIdentifier = sourceIdentifier
        self.shouldAlert = shouldAlert
        self.deliveryTimestamp = deliveryTimestamp
        self.dismissalActionIdentifier = dismissalActionIdentifier
        self.originalNotificationIdentifier = originalNotificationIdentifier
        self.dismissOnBothDevices = dismissOnBothDevices
        self.deliveryReceiptID = deliveryReceiptID
        self.actions = actions
        self.replies = replies
    }
}

package enum AccessoryNotificationMessage: Equatable, Sendable {
    case present(ForwardedNotification)
    case remove(sourceIdentifier: String, notificationIdentifier: String)
    case removeAll
    case replyResult(requestID: [UInt8], accepted: Bool)
    case dismissalPolicy(onBothDevices: Bool)
}

/// What the watch sends back when an action of a forwarded notification is
/// chosen.
package struct AccessoryNotificationReply: Equatable, Sendable {
    package var requestID: [UInt8]? = nil
    package var sourceIdentifier: String? = nil
    package var clearsNotification = false
    package var notificationIdentifier: String
    package var actionIdentifier: String
    /// Nil for a plain action, and for a text-input one left empty.
    package var text: String?

    package init(notificationIdentifier: String, actionIdentifier: String, text: String?) {
        self.notificationIdentifier = notificationIdentifier
        self.actionIdentifier = actionIdentifier
        self.text = text
    }
}

/// The plaintext inside an AccessoryNotifications message — sealed by iOS, opened
/// by the watch's `accessory_notifications.c`, whose parser is the other half of
/// this file.
package enum AccessoryNotificationCodec {
    static let presentType: UInt8 = 0x01
    static let removeType: UInt8 = 0x02
    static let removeAllType: UInt8 = 0x03

    static let titleTag: UInt8 = 0x01
    static let subtitleTag: UInt8 = 0x02
    static let bodyTag: UInt8 = 0x03
    static let sourceTag: UInt8 = 0x04
    static let identifierTag: UInt8 = 0x05
    static let alertTag: UInt8 = 0x06
    static let actionTag: UInt8 = 0x07
    static let sourceIdentifierTag: UInt8 = 0x08
    static let repliesTag: UInt8 = 0x09
    static let deliveryTimeTag: UInt8 = 0x0A
    static let dismissalActionTag: UInt8 = 0x0B

    static let textInputFlag: UInt8 = 0x01

    /// `AN_MAX_ACTIONS`: the watch keeps the first four and drops the rest.
    package static let maximumActionCount = 4

    package static func encode(_ message: AccessoryNotificationMessage) -> [UInt8] {
        switch message {
        case .present(let notification):
            present(notification)
        case .remove(let sourceIdentifier, let notificationIdentifier):
            [removeType] + identifierBytes(
                sourceIdentifier: sourceIdentifier,
                notificationIdentifier: notificationIdentifier
            )
        case .removeAll:
            [removeAllType]
        case .replyResult(let requestID, let accepted):
            [4] + requestID + [accepted ? 1 : 0]
        case .dismissalPolicy(let onBothDevices):
            [5, onBothDevices ? 1 : 0]
        }
    }

    /// `u8 notification_id_len | notification_id | u8 action_id_len | action_id |
    /// u16 text_len (LE) | text`, from `accessory_notifications_invoke_action`.
    package static func decodeReply(_ bytes: [UInt8]) throws -> AccessoryNotificationReply {
        var reader = bytes[...]
        guard bytes.count <= 2048 else { throw AccessoryNotificationCodecError.truncated }
        var requestID: [UInt8]?
        if bytes.starts(with: Array("NAR1".utf8)) {
            guard bytes.count > 20 else { throw AccessoryNotificationCodecError.truncated }
            requestID = Array(bytes[4..<20])
            reader = bytes.dropFirst(20)
        }
        var sourceIdentifier: String?
        var clearsNotification = false
        if reader.starts(with: Array("PNI1".utf8)) || reader.starts(with: Array("PND1".utf8)) {
            guard requestID != nil else { throw AccessoryNotificationCodecError.truncated }
            clearsNotification = reader.starts(with: Array("PND1".utf8))
            reader = reader.dropFirst(4)
            sourceIdentifier = try lengthPrefixed(&reader)
            guard sourceIdentifier!.utf8.count <= 127 else { throw AccessoryNotificationCodecError.truncated }
        }
        let notificationIdentifier = try lengthPrefixed(&reader)
        let actionIdentifier: String
        if clearsNotification {
            guard reader.first == 0 else { throw AccessoryNotificationCodecError.truncated }
            reader = reader.dropFirst()
            actionIdentifier = ""
        } else { actionIdentifier = try lengthPrefixed(&reader) }
        if sourceIdentifier != nil, reader.count < 2 { throw AccessoryNotificationCodecError.truncated }
        var text: String?
        if reader.count >= 2 {
            let length = Int(UInt16(littleEndianBytes: reader.prefix(2)))
            reader = reader.dropFirst(2)
            guard reader.count >= length else { throw AccessoryNotificationCodecError.truncated }
            if length > 0 {
                guard !reader.prefix(length).contains(0), let decoded = String(bytes: reader.prefix(length), encoding: .utf8) else {
                    throw AccessoryNotificationCodecError.truncated
                }
                text = decoded
            }
            reader = reader.dropFirst(length)
        }
        guard reader.isEmpty, !clearsNotification || text == nil else { throw AccessoryNotificationCodecError.truncated }
        var reply = AccessoryNotificationReply(notificationIdentifier: notificationIdentifier,
                                               actionIdentifier: actionIdentifier, text: text)
        reply.requestID = requestID
        reply.sourceIdentifier = sourceIdentifier
        reply.clearsNotification = clearsNotification
        return reply
    }

    private static func present(_ notification: ForwardedNotification) -> [UInt8] {
        var bytes = [presentType]
        func append(_ tag: UInt8, _ value: [UInt8]) {
            guard !value.isEmpty else { return }
            bytes += [tag, UInt8(value.count)] + value
        }
        // `MAX_ATTRIBUTE_LENGTHS` for the three the watch has a limit for; the
        // length byte for the rest. Either way the cut has to fall between
        // characters.
        append(titleTag, text(notification.title, maximumByteCount: 64))
        append(subtitleTag, text(notification.subtitle, maximumByteCount: 64))
        append(bodyTag, text(notification.body, maximumByteCount: 255))
        append(sourceTag, text(notification.sourceName, maximumByteCount: 64))
        // Not the length byte's 255: the watch keys the app's notification
        // preferences by it, and ignores one longer than a settings key
        // (`SETTINGS_KEY_MAX_LEN`), which leaves the reader no Mute.
        // SDK identities are opaque: omit an unrepresentable identity rather than truncate it.
        if let source = notification.sourceIdentifier, validIdentity(source, maximum: 127) {
            append(sourceIdentifierTag, Array(source.utf8))
            if let original = notification.originalNotificationIdentifier, validIdentity(original, maximum: 255) {
                append(13, Array(original.utf8))
            }
        }
        append(identifierTag, identifierBytes(
            sourceIdentifier: notification.sourceIdentifier ?? "",
            notificationIdentifier: notification.identifier
        ))
        append(alertTag, [notification.shouldAlert ? 1 : 0])
        if let mode = notification.dismissOnBothDevices { append(12, [mode ? 1 : 0]) }
        if let event = notification.deliveryReceiptID, event.count == 16 { append(14, event) }
        if let timestamp = notification.deliveryTimestamp, timestamp > 0 {
            append(deliveryTimeTag, [UInt8(truncatingIfNeeded: timestamp >> 24),
                UInt8(truncatingIfNeeded: timestamp >> 16), UInt8(truncatingIfNeeded: timestamp >> 8),
                UInt8(truncatingIfNeeded: timestamp)])
        }
        append(dismissalActionTag, text(notification.dismissalActionIdentifier, maximumByteCount: 127))
        let actions = notification.actions
            .compactMap { action in actionEntry(action).map { (collectsText: action.collectsText, entry: $0) } }
            .prefix(maximumActionCount)
        for action in actions {
            append(actionTag, action.entry)
        }
        if actions.contains(where: { $0.collectsText }) {
            append(repliesTag, repliesValue(notification.replies))
        }
        return bytes
    }

    /// `u8 len | reply` for each, no terminators, as many as fit in one TLV. The
    /// watch keeps up to `MAX_LENGTH_CANNED_RESPONSES` of a list, more than the
    /// length byte holds, so the length byte is the limit. A reply that does not
    /// fit beside the ones before it is left out whole rather than cut to the
    /// room left, which would send the other person a fragment of it.
    private static func repliesValue(_ replies: [String]) -> [UInt8] {
        var value: [UInt8] = []
        for reply in replies {
            let bytes = reply.utf8BytesEndingOnACharacter(maximumByteCount: 254)
            guard !bytes.isEmpty, value.count + 1 + bytes.count <= 255 else { continue }
            value += [UInt8(bytes.count)] + bytes
        }
        return value
    }

    /// Nil for an action the watch could not show or could not answer: a row with
    /// no title is blank and unselectable, and an identifier cut short comes back
    /// as one iOS never issued.
    private static func actionEntry(_ action: ForwardedNotification.Action) -> [UInt8]? {
        let title = text(action.title, maximumByteCount: 64)
        let identifier = Array(action.identifier.utf8)
        // flags, two length bytes and the title have to fit beside it in one TLV.
        guard !title.isEmpty, validIdentity(action.identifier, maximum: 255 - 3 - title.count) else { return nil }
        return [action.collectsText ? textInputFlag : 0, UInt8(identifier.count)] + identifier
            + [UInt8(title.count)] + title
    }

    /// The identifier as the watch holds it, and as a reply names the notification.
    package static func identifierOnTheWatch(sourceIdentifier: String, notificationIdentifier: String) -> String {
        String(
            decoding: identifierBytes(sourceIdentifier: sourceIdentifier, notificationIdentifier: notificationIdentifier),
            as: UTF8.self
        )
    }

    /// The watch derives a notification's UUID from these bytes alone, so a
    /// present and the remove that follows it have to cut the same identifier the
    /// same way. The source is in it because iOS scopes an identifier to its app:
    /// sent alone, two apps' "1" were one notification on the watch, each
    /// overwriting the other. U+001F cannot occur in a bundle identifier, so no
    /// two pairs make the same bytes.
    private static func identifierBytes(sourceIdentifier: String, notificationIdentifier: String) -> [UInt8] {
        if notificationIdentifier.hasPrefix("pnt1:"), notificationIdentifier.utf8.count == 69,
           notificationIdentifier.dropFirst(5).allSatisfy({ $0.isHexDigit && !$0.isUppercase }) {
            return Array(notificationIdentifier.utf8)
        }
        return (sourceIdentifier + "\u{1F}" + notificationIdentifier).utf8BytesEndingOnACharacter(maximumByteCount: 255)
    }

    private static func validIdentity(_ value: String, maximum: Int) -> Bool {
        !value.isEmpty && !value.utf8.contains(0) && value.utf8.count <= maximum
    }

    private static func text(_ value: String?, maximumByteCount: Int) -> [UInt8] {
        value?.utf8BytesEndingOnACharacter(maximumByteCount: maximumByteCount) ?? []
    }

    private static func lengthPrefixed(_ reader: inout ArraySlice<UInt8>) throws -> String {
        guard let length = reader.first.map(Int.init), reader.count > length else {
            throw AccessoryNotificationCodecError.truncated
        }
        guard length > 0, !reader.dropFirst().prefix(length).contains(0),
              let value = String(bytes: reader.dropFirst().prefix(length), encoding: .utf8) else {
            throw AccessoryNotificationCodecError.truncated
        }
        reader = reader.dropFirst(1 + length)
        return value
    }
}

package enum AccessoryNotificationCodecError: Error, Equatable, Sendable {
    case truncated
}
