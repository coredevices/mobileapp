#if os(iOS)
import PebbleProtocol

/// What decides the replies the watch offers from one notification.
///
/// One seam, so that something reading the notification itself can take the
/// stored list's place. Whatever does has to answer within a deadline of its
/// own and fall back to the stored list: the notification waits for it, and so
/// does every message queued behind it.
protocol ReplySuggesting: Sendable {
    func replies(for notification: ForwardedNotification) async -> [String]
}

/// The replies the reader keeps in the app, the same for every notification.
struct StoredReplies: ReplySuggesting {
    var store = ReplyTemplateStore(directory: .sharedWithExtensions)

    func replies(for notification: ForwardedNotification) async -> [String] {
        let templates = (try? await store.templates()) ?? []
        return templates.map(\.text).filter { !$0.isEmpty }
    }
}
#endif
