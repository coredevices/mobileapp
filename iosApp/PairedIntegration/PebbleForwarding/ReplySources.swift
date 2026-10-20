#if os(iOS)
import AccessoryNotifications
import Foundation
import PebbleProtocol
import Synchronization

/// Which forwarded notification a reply from the watch is about.
///
/// The watch answers with the identifier it was sent — source and notification
/// together, cut to what its length byte holds — and iOS wants the response
/// keyed by the two apart. The extension that asked is often not the process
/// that hears the answer, so the mapping is kept in the extension's own
/// defaults rather than in memory, and read from them afresh every time: a
/// copy read once when the handler was made never saw what another process
/// remembered.
final class ReplySources: Sendable {
    /// Well past what a watch keeps: `AN_MAX_TRACKED` forgets beyond 64.
    static let limit = 256
    /// How many are remembered between two counts of what is stored. Counting
    /// copies every defaults domain, the global one included, so it is not done
    /// for each notification; it is done on the first, though, because an
    /// extension's process may not live to see a second.
    private static let trimInterval = 64
    /// One key per notification rather than one list: two processes
    /// remembering at once each read the list, added their own, and the later
    /// write took the earlier one's entry away with it.
    private static let prefix = "forwarded."
    private static let formerListKey = "forwardedNotificationIdentifiers"

    private struct Entry: Codable {
        var identifier: AccessoryNotification.Identifier
        var remembered: Date
    }

    private let rememberedSinceStart = Mutex(0)

    func notification(onTheWatchAs identifier: String) -> AccessoryNotification.Identifier? {
        Self.entry(forKey: Self.prefix + identifier)?.identifier
    }

    func remember(_ identifier: AccessoryNotification.Identifier, token: String? = nil) {
        let entry = Entry(identifier: identifier, remembered: .now)
        UserDefaults.standard.set(try? JSONEncoder().encode(entry), forKey: token.map { Self.prefix + $0 } ?? Self.key(for: identifier))
        let count = rememberedSinceStart.withLock { count in
            defer { count += 1 }
            return count
        }
        if count.isMultiple(of: Self.trimInterval) {
            Self.keepWithinLimit()
        }
    }

    func forget(_ identifier: AccessoryNotification.Identifier, token: String? = nil) {
        UserDefaults.standard.removeObject(forKey: token.map { Self.prefix + $0 } ?? Self.key(for: identifier))
    }

    func forgetAll() {
        for key in Self.storedKeys() {
            UserDefaults.standard.removeObject(forKey: key)
        }
    }

    static func identifierOnTheWatch(_ identifier: AccessoryNotification.Identifier) -> String {
        AccessoryNotificationCodec.identifierOnTheWatch(
            sourceIdentifier: identifier.sourceIdentifier,
            notificationIdentifier: identifier.notificationIdentifier
        )
    }

    private static func key(for identifier: AccessoryNotification.Identifier) -> String {
        prefix + identifierOnTheWatch(identifier)
    }

    private static func entry(forKey key: String) -> Entry? {
        UserDefaults.standard.data(forKey: key).flatMap { try? JSONDecoder().decode(Entry.self, from: $0) }
    }

    private static func storedKeys() -> [String] {
        UserDefaults.standard.dictionaryRepresentation().keys.filter { $0.hasPrefix(prefix) }
    }

    /// Drops the oldest past the limit, and the single list this used to be.
    private static func keepWithinLimit() {
        UserDefaults.standard.removeObject(forKey: formerListKey)
        let keys = storedKeys()
        guard keys.count > limit else { return }
        let oldestFirst = keys
            .map { (key: $0, remembered: entry(forKey: $0)?.remembered ?? .distantPast) }
            .sorted { $0.remembered < $1.remembered }
        for stale in oldestFirst.prefix(keys.count - limit) {
            UserDefaults.standard.removeObject(forKey: stale.key)
        }
    }
}
#endif
