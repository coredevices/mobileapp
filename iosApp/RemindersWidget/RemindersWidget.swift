import AppIntents
import SwiftUI
import WidgetKit

private let fallbackDeepLink = URL(string: "pebble://navbar/index")!

struct RemindersEntry: TimelineEntry {
    let date: Date
    let snapshot: RemindersSnapshot?
    let page: Int
}

struct RemindersProvider: TimelineProvider {
    func placeholder(in context: Context) -> RemindersEntry {
        RemindersEntry(date: .now, snapshot: .preview, page: 0)
    }

    func getSnapshot(in context: Context, completion: @escaping (RemindersEntry) -> Void) {
        completion(context.isPreview ? placeholder(in: context) : currentEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<RemindersEntry>) -> Void) {
        // The app reloads the timeline whenever its reminders change. Just-completed rows show struck
        // through, then drop out 2s after they were first drawn.
        let entry = currentEntry()
        let now = entry.date
        var entries = [entry]
        if let snapshot = entry.snapshot {
            let doneSince = RemindersWidgetStore.doneSince(ids: snapshot.reminders.filter(\.done).map(\.id), now: now)
            let hideAt = doneSince.mapValues { $0.addingTimeInterval(2) }
            func visible(at date: Date) -> RemindersSnapshot {
                RemindersSnapshot(enabled: snapshot.enabled, reminders: snapshot.reminders.filter { hideAt[$0.id].map { $0 > date } ?? true })
            }
            entries = [RemindersEntry(date: now, snapshot: visible(at: now), page: entry.page)]
            for date in Set(hideAt.values).filter({ $0 > now }).sorted() {
                entries.append(RemindersEntry(date: date, snapshot: visible(at: date), page: entry.page))
            }
        }
        // Redraw when a due label changes: at midnight ("Tomorrow" -> "Today") and when the next reminder
        // turns overdue.
        let midnight = Calendar.current.startOfDay(for: now.addingTimeInterval(86_400))
        let nextDue = entry.snapshot?.reminders.compactMap(\.dueAt).filter { $0 > now }.min()
        completion(Timeline(entries: entries, policy: .after(min(midnight, nextDue ?? midnight))))
    }

    private func currentEntry() -> RemindersEntry {
        RemindersEntry(date: .now, snapshot: RemindersWidgetStore.read(), page: RemindersWidgetStore.page)
    }
}

/// Widgets can't scroll, so the header's chevrons page through the list.
struct PageRemindersIntent: AppIntent {
    static var title: LocalizedStringResource = "Page Reminders"
    static var isDiscoverable = false

    @Parameter(title: "Page")
    var page: Int

    init() {}

    init(page: Int) {
        self.page = page
    }

    func perform() async throws -> some IntentResult {
        RemindersWidgetStore.page = max(0, page)
        return .result()
    }
}

private let brand = Color(red: 0.98, green: 0.33, blue: 0.22)

struct RemindersWidgetView: View {
    let entry: RemindersEntry
    @Environment(\.widgetFamily) private var family

    private var rowsPerPage: Int { family == .systemLarge ? 11 : 4 }

    var body: some View {
        let reminders = entry.snapshot?.reminders ?? []
        let pageCount = max(1, (reminders.count + rowsPerPage - 1) / rowsPerPage)
        let page = min(entry.page, pageCount - 1)
        let rows = Array(reminders.dropFirst(page * rowsPerPage).prefix(rowsPerPage))

        VStack(alignment: .leading, spacing: 4) {
            Header(count: reminders.count, page: page, pageCount: pageCount)
            if entry.snapshot?.enabled == false {
                Message(text: "Turn on Index in the app to see reminders")
            } else if rows.isEmpty {
                Message(text: "No upcoming reminders")
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                        if index > 0 {
                            Rectangle()
                                .fill(.primary.opacity(0.1))
                                .frame(height: 0.5)
                                .padding(.leading, 27)
                        }
                        ReminderRow(row: row, now: entry.date)
                    }
                }
                Spacer(minLength: 0)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .tint(brand)
        .widgetURL(fallbackDeepLink)
        .containerBackground(for: .widget) { GlassBackground() }
    }
}

private struct Header: View {
    let count: Int
    let page: Int
    let pageCount: Int

    var body: some View {
        HStack(spacing: 6) {
            Text("Reminders")
                .font(.system(size: 13, weight: .semibold, design: .rounded))
                .foregroundStyle(.tint)
                .widgetAccentable()
            if count > 0 {
                Text("\(count)")
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundStyle(.secondary)
                    .monospacedDigit()
            }
            Spacer(minLength: 0)
            if pageCount > 1 {
                HStack(spacing: 0) {
                    PageButton(systemName: "chevron.up", page: max(page - 1, 0), enabled: page > 0)
                    PageButton(systemName: "chevron.down", page: min(page + 1, pageCount - 1), enabled: page < pageCount - 1)
                }
                .background(.primary.opacity(0.07), in: Capsule())
            }
        }
        .frame(height: 22)
    }
}

private struct PageButton: View {
    let systemName: String
    let page: Int
    let enabled: Bool

    var body: some View {
        Button(intent: PageRemindersIntent(page: page)) {
            Image(systemName: systemName)
                .font(.system(size: 10, weight: .bold))
                .frame(width: 28, height: 22)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .foregroundStyle(.secondary)
        .opacity(enabled ? 1 : 0.3)
    }
}

private struct ReminderRow: View {
    let row: RemindersSnapshot.Row
    let now: Date

    var body: some View {
        HStack(spacing: 9) {
            Button(intent: MarkReminderDoneIntent(reminderId: row.id)) {
                Image(systemName: row.done ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 17, weight: .light))
                    .foregroundStyle(row.done ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))
                    .contentTransition(.symbolEffect(.replace))
                    .widgetAccentable()
            }
            .buttonStyle(.plain)
            .invalidatableContent()
            .disabled(row.done)
            .accessibilityLabel("Mark \(row.title) done")

            Link(destination: URL(string: row.deepLink) ?? fallbackDeepLink) {
                HStack(spacing: 6) {
                    Text(row.title)
                        .font(.system(size: 13.5))
                        .strikethrough(row.done)
                        .foregroundStyle(row.done ? .secondary : .primary)
                        .lineLimit(1)
                    Spacer(minLength: 0)
                    if let dueAt = row.dueAt {
                        Text(dueLabel(dueAt, now: now))
                            .font(.system(size: 11, weight: .medium, design: .rounded))
                            .foregroundStyle(dueAt <= now && !row.done ? AnyShapeStyle(.red) : AnyShapeStyle(.secondary))
                            .lineLimit(1)
                            .layoutPriority(1)
                    }
                }
            }
        }
        .frame(height: 29)
    }
}

/// "Today 3 PM", "Tomorrow 9:30 AM", "Yesterday", "Mon 2 PM" within the week, else "Nov 7".
func dueLabel(_ date: Date, now: Date, calendar: Calendar = .current) -> String {
    let minutes = calendar.component(.minute, from: date)
    let time = date.formatted(minutes == 0 ? .dateTime.hour() : .dateTime.hour().minute())
    let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: now), to: calendar.startOfDay(for: date)).day ?? 0
    switch days {
    case 0: return "Today \(time)"
    case 1: return "Tomorrow \(time)"
    case -1: return "Yesterday"
    case 2...6: return "\(date.formatted(.dateTime.weekday(.abbreviated))) \(time)"
    default: return date.formatted(.dateTime.month(.abbreviated).day())
    }
}

private struct Message: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .multilineTextAlignment(.center)
    }
}

/// Frosted tint with a top sheen; `.glassEffect` draws nothing inside widgets.
private struct GlassBackground: View {
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let dark = colorScheme == .dark
        ZStack {
            LinearGradient(
                colors: dark
                    ? [Color(red: 0.15, green: 0.15, blue: 0.19), Color(red: 0.09, green: 0.09, blue: 0.12)]
                    : [Color(red: 0.99, green: 0.97, blue: 0.96), Color(red: 0.94, green: 0.94, blue: 0.97)],
                startPoint: .top,
                endPoint: .bottom
            )
            LinearGradient(
                colors: [.white.opacity(dark ? 0.08 : 0.6), .clear],
                startPoint: .top,
                endPoint: .center
            )
        }
    }
}

struct RemindersWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: RemindersWidgetStore.kind, provider: RemindersProvider()) { entry in
            RemindersWidgetView(entry: entry)
        }
        .configurationDisplayName("Reminders")
        .description("Upcoming Index reminders.")
        .supportedFamilies([.systemMedium, .systemLarge])
        .contentMarginsDisabled()
    }
}

@main
struct RemindersWidgetBundle: WidgetBundle {
    var body: some Widget {
        RemindersWidget()
    }
}

private func previewDue(hours: Double) -> Int64 {
    Int64(Date.now.addingTimeInterval(hours * 3600).timeIntervalSince1970 * 1000)
}

extension RemindersSnapshot {
    static let preview = RemindersSnapshot(enabled: true, reminders: [
        Row(id: "1", title: "Call the dentist", dueAtMillis: previewDue(hours: 3), done: false, deepLink: ""),
        Row(id: "2", title: "Pick up dry cleaning", dueAtMillis: previewDue(hours: -20), done: false, deepLink: ""),
        Row(id: "3", title: "Book flights", dueAtMillis: nil, done: false, deepLink: ""),
        Row(id: "4", title: "Renew passport", dueAtMillis: previewDue(hours: 26), done: false, deepLink: ""),
        Row(id: "5", title: "Send invoice to Alex", dueAtMillis: nil, done: false, deepLink: ""),
    ])
}
