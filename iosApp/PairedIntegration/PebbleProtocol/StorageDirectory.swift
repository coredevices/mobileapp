public import Foundation

/// Values supplied by the containing app and each ExtensionKit target. A missing
/// opt-in or signing identifier leaves the bridge inactive.
public enum NotificationBridgeConfiguration {
    public static var enabled: Bool {
        (Bundle.main.object(forInfoDictionaryKey: "PebbleNotificationsEnabled") as? Bool) == true
    }

    public static var namespace: String {
        Bundle.main.object(forInfoDictionaryKey: "PebbleNotificationNamespace") as? String
            ?? "coredevices.coreapp"
    }

    public static var appGroup: String {
        Bundle.main.object(forInfoDictionaryKey: "PebbleNotificationAppGroup") as? String ?? ""
    }

    public static var keychainGroup: String {
        Bundle.main.object(forInfoDictionaryKey: "PebbleNotificationKeychainGroup") as? String ?? ""
    }

    public static func notificationName(_ suffix: String) -> String { namespace + "." + suffix }
}

/// Where the app keeps its own files.
///
/// One value passed down rather than a default inside each store, because each
/// store defaulting to the real Application Support directory meant every
/// `AppModel` in a process shared the same files. In the app there is one
/// model, so that was invisible; in the test suite, which Swift Testing runs
/// concurrently, two tests read and wrote the same queue — one test's queued
/// notification arrived at another's watch — and a full run overwrote the
/// reader's own notification history (#59).
public struct StorageDirectory: Hashable, Sendable {
    public var url: URL

    public init(url: URL) {
        self.url = url
    }

    public static var applicationSupport: StorageDirectory {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        return StorageDirectory(url: base.appendingPathComponent("Pebble", isDirectory: true))
    }

    /// The App Group the app and its notification extension both hold.
    public static var appGroupIdentifier: String { NotificationBridgeConfiguration.appGroup }

    /// The App Group's container, which the extensions iOS runs in processes of
    /// their own can read as well as the app. Nil for a process that holds no
    /// such entitlement.
    public static var sharedWithExtensions: StorageDirectory? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupIdentifier).map {
            StorageDirectory(url: $0.appendingPathComponent("Pebble", isDirectory: true))
        }
    }

    public func file(_ name: String) -> URL {
        url.appendingPathComponent(name, isDirectory: false)
    }
}
