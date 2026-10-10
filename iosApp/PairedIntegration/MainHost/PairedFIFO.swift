// Bounded by each caller. Small native queues avoid bringing an entire SwiftPM
// dependency graph into the KMP companion's three extension targets.
struct PairedFIFO<Element>: ExpressibleByArrayLiteral, Sequence {
    private var items: [Element]
    init(arrayLiteral elements: Element...) { items = elements }
    var count: Int { items.count }
    var isEmpty: Bool { items.isEmpty }
    var indices: Range<Int> { items.indices }
    subscript(index: Int) -> Element { get { items[index] } set { items[index] = newValue } }
    func makeIterator() -> IndexingIterator<[Element]> { items.makeIterator() }
    mutating func append(_ element: Element) { items.append(element) }
    mutating func remove(at index: Int) -> Element { items.remove(at: index) }
    mutating func popFirst() -> Element? { items.isEmpty ? nil : items.removeFirst() }
    func firstIndex(where predicate: (Element) throws -> Bool) rethrows -> Int? { try items.firstIndex(where: predicate) }
}
extension PairedFIFO: Sendable where Element: Sendable {}
