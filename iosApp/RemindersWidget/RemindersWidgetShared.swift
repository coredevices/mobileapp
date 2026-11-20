// Compiled into both the app and the RemindersWidget extension.
import AppIntents
import Foundation
#if !WIDGET_EXTENSION
import ComposeApp
#endif

struct RemindersSnapshot: Codable {
    struct Row: Codable, Hashable {
        let id: String
        let title: String
        let dueAtMillis: Int64?
        let done: Bool
        let deepLink: String

        var dueAt: Date? { dueAtMillis.map { Date(timeIntervalSince1970: TimeInterval($0) / 1000) } }
    }

    let enabled: Bool
    let reminders: [Row]
}

enum RemindersWidgetStore {
    static let kind = "RemindersWidget"
    private static let fileName = "reminders-widget.json"
    private static let pageKey = "remindersWidgetPage"
    private static let doneKey = "remindersWidgetDoneSince"

    private static var groupId: String? {
        Bundle.main.object(forInfoDictionaryKey: "RemindersAppGroup") as? String
    }

    private static var fileURL: URL? {
        guard let groupId else { return nil }
        return FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: groupId)?
            .appendingPathComponent(fileName)
    }

    static var defaults: UserDefaults? { groupId.flatMap(UserDefaults.init(suiteName:)) }

    static func write(_ json: String) {
        guard let url = fileURL else { return }
        try? Data(json.utf8).write(to: url, options: .atomic)
    }

    static func read() -> RemindersSnapshot? {
        guard let url = fileURL, let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(RemindersSnapshot.self, from: data)
    }

    /// When each of [ids] was first seen completed; forgets ids no longer completed.
    static func doneSince(ids: [String], now: Date) -> [String: Date] {
        let stored = defaults?.dictionary(forKey: doneKey) as? [String: Date] ?? [:]
        var seen = stored.filter { ids.contains($0.key) }
        for id in ids where seen[id] == nil { seen[id] = now }
        defaults?.set(seen, forKey: doneKey)
        return seen
    }

    // ponytail: one page offset shared by every widget instance; key it by widget id if people stack several.
    static var page: Int {
        get { defaults?.integer(forKey: pageKey) ?? 0 }
        set { defaults?.set(newValue, forKey: pageKey) }
    }
}

/// LiveActivityIntent so the system runs perform() in the app process, where the Kotlin
/// ReminderCompleter lives; a plain AppIntent would run in the widget extension.
@available(iOS 17.0, *)
struct MarkReminderDoneIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Mark Reminder Done"
    static var isDiscoverable = false

    @Parameter(title: "Reminder ID")
    var reminderId: String

    init() {}

    init(reminderId: String) {
        self.reminderId = reminderId
    }

    func perform() async throws -> some IntentResult {
        #if !WIDGET_EXTENSION
        await withCheckedContinuation { continuation in
            IOSDelegate.shared.markReminderDoneFromWidget(itemId: reminderId) {
                continuation.resume()
            }
        }
        #endif
        return .result()
    }
}
