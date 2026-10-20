import Foundation

/// The replies the reader keeps for the watch, where the notification
/// extension — a process of its own, which reads them for each notification
/// it forwards — can read them too.
public actor ReplyTemplateStore {
    public struct ContainerUnavailable: Error, Equatable, Sendable {}

    private let fileURL: URL?

    /// Nil when this process has no directory the extension shares.
    public init(directory: StorageDirectory?) {
        fileURL = directory?.file("reply-templates.json")
    }

    /// Nil when none have ever been saved, which is not the same as the reader
    /// having deleted every one. Empty where there is nowhere to keep them.
    public func templates() throws -> [ReplyTemplate]? {
        guard let fileURL else { return [] }
        return try PersistentJSON.loadRecovering([ReplyTemplate].self, from: fileURL)
    }

    public func save(_ templates: [ReplyTemplate]) throws {
        guard let fileURL else { throw ContainerUnavailable() }
        try PersistentJSON.save(templates, to: fileURL)
    }

    /// Reads, changes and saves the list in one step, and returns what was
    /// kept. A change built from the caller's copy instead started from the
    /// same list as another made meanwhile, and whichever saved last lost the
    /// other's edit.
    package func modify(_ change: @Sendable ([ReplyTemplate]) -> [ReplyTemplate]) throws -> [ReplyTemplate] {
        guard let fileURL else { throw ContainerUnavailable() }
        let changed = change(try PersistentJSON.loadRecovering([ReplyTemplate].self, from: fileURL) ?? [])
        try PersistentJSON.save(changed, to: fileURL)
        return changed
    }
}
