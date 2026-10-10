extension String {
    /// A cut inside a multi-byte character leaves the watch a byte it cannot read
    /// as the start of one: `utf8_get_bounds` fails and the text layout draws
    /// nothing at all, so a Japanese title one character too long would vanish
    /// rather than lose its tail.
    func utf8BytesEndingOnACharacter(maximumByteCount limit: Int) -> [UInt8] {
        var content: [UInt8] = []
        for character in self {
            let bytes = Array(String(character).utf8)
            guard content.count + bytes.count <= limit else { break }
            content.append(contentsOf: bytes)
        }
        return content
    }
}
