public import Foundation

/// A reply the reader keeps for the watch to offer when they answer a
/// notification from it.
public struct ReplyTemplate: Codable, Hashable, Identifiable, Sendable {
    /// Two templates may say the same thing, and one is still deleted or moved
    /// without the other.
    public var id: UUID
    public var text: String

    public init(id: UUID = UUID(), text: String) {
        self.id = id
        self.text = text
    }
}
