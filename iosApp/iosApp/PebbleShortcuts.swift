import AppIntents
import ComposeApp
import Foundation
import UIKit

// Enums

@available(iOS 16.0, *)
enum QuietTimeShowOption: String, AppEnum {
    case hide = "Hide"
    case show = "Show"

    static var typeDisplayRepresentation: TypeDisplayRepresentation {
        TypeDisplayRepresentation(name: "Quiet Time Notifications")
    }

    static var caseDisplayRepresentations: [QuietTimeShowOption: DisplayRepresentation] {
        [.hide: "Hide", .show: "Show"]
    }
}

@available(iOS 16.0, *)
enum QuietTimeInterruptionsOption: String, AppEnum {
    case allOff = "AllOff"
    case phoneCalls = "PhoneCalls"

    static var typeDisplayRepresentation: TypeDisplayRepresentation {
        TypeDisplayRepresentation(name: "Quiet Time Interruptions")
    }

    static var caseDisplayRepresentations: [QuietTimeInterruptionsOption: DisplayRepresentation] {
        [.allOff: "All Off", .phoneCalls: "Phone Calls"]
    }
}

@available(iOS 16.0, *)
enum NotificationFilterOption: String, AppEnum {
    case allOn = "AllOn"
    case phoneCalls = "PhoneCalls"
    case allOff = "AllOff"

    static var typeDisplayRepresentation: TypeDisplayRepresentation {
        TypeDisplayRepresentation(name: "Notification Filter")
    }

    static var caseDisplayRepresentations: [NotificationFilterOption: DisplayRepresentation] {
        [.allOn: "All On", .phoneCalls: "Phone Calls", .allOff: "All Off"]
    }
}

@available(iOS 16.0, *)
enum NotificationMuteAction: String, AppEnum {
    case mute = "Mute"
    case unmute = "Unmute"

    static var typeDisplayRepresentation: TypeDisplayRepresentation {
        TypeDisplayRepresentation(name: "Notification")
    }

    static var caseDisplayRepresentations: [NotificationMuteAction: DisplayRepresentation] {
        [.mute: "Mute", .unmute: "Unmute"]
    }
}

// Entities

@available(iOS 16.0, *)
struct LockerWatchfaceEntity: AppEntity {
    var id: String
    var title: String

    static var typeDisplayRepresentation: TypeDisplayRepresentation = TypeDisplayRepresentation(
        name: "Watchface"
    )

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(title)")
    }

    static var defaultQuery = LockerWatchfaceEntityQuery()
}

@available(iOS 16.0, *)
struct LockerWatchfaceEntityQuery: EntityQuery, EntityStringQuery {
    /// Resolves specific identifiers into entities.
    func entities(for identifiers: [String]) async throws -> [LockerWatchfaceEntity] {
        let all = await fetchLockerWatchfaces()
        return identifiers.compactMap { id in
            all.first(where: { $0.id == id })
        }
    }

    /// Provides the default list of entities for the picker.
    func suggestedEntities() async throws -> [LockerWatchfaceEntity] {
        await fetchLockerWatchfaces()
    }

    /// Filters entities based on a search string.
    func entities(matching string: String) async throws -> [LockerWatchfaceEntity] {
        let all = await fetchLockerWatchfaces()
        guard !string.isEmpty else { return all }
        return all.filter { $0.title.localizedCaseInsensitiveContains(string) }
    }

    private func fetchLockerWatchfaces() async -> [LockerWatchfaceEntity] {
        await withCheckedContinuation { continuation in
            IOSDelegateShortcuts.shared.getLockerWatchfacesForShortcutsWithCompletion(callback: { items in
                continuation.resume(returning: items.map { LockerWatchfaceEntity(id: $0.id, title: $0.title) })
            })
        }
    }
}

@available(iOS 16.0, *)
struct LockerWatchappEntity: AppEntity {
    var id: String
    var title: String

    static var typeDisplayRepresentation: TypeDisplayRepresentation = TypeDisplayRepresentation(
        name: "Watch App"
    )

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(title)")
    }

    static var defaultQuery = LockerWatchappEntityQuery()
}

@available(iOS 16.0, *)
struct LockerWatchappEntityQuery: EntityQuery, EntityStringQuery {
    /// Resolves specific identifiers into entities.
    func entities(for identifiers: [String]) async throws -> [LockerWatchappEntity] {
        let all = await fetchLockerWatchapps()
        return identifiers.compactMap { id in
            all.first(where: { $0.id == id })
        }
    }

    /// Provides the default list of entities for the picker.
    func suggestedEntities() async throws -> [LockerWatchappEntity] {
        await fetchLockerWatchapps()
    }

    /// Filters entities based on a search string.
    func entities(matching string: String) async throws -> [LockerWatchappEntity] {
        let all = await fetchLockerWatchapps()
        guard !string.isEmpty else { return all }
        return all.filter { $0.title.localizedCaseInsensitiveContains(string) }
    }

    private func fetchLockerWatchapps() async -> [LockerWatchappEntity] {
        await withCheckedContinuation { continuation in
            IOSDelegateShortcuts.shared.getLockerWatchappsForShortcutsWithCompletion(callback: { items in
                continuation.resume(returning: items.map { LockerWatchappEntity(id: $0.id, title: $0.title) })
            })
        }
    }
}

@available(iOS 16.0, *)
struct TimelineColorEntity: AppEntity {
    var id: String
    var title: String

    static var typeDisplayRepresentation: TypeDisplayRepresentation = TypeDisplayRepresentation(name: "Color")
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(title)") }
    static var defaultQuery = TimelineColorEntityQuery()
}

@available(iOS 16.0, *)
struct TimelineColorEntityQuery: EntityQuery, EntityStringQuery {
    /// Resolves specific identifiers into entities.
    func entities(for identifiers: [String]) async throws -> [TimelineColorEntity] {
        let all = await fetchItems()
        return identifiers.compactMap { id in all.first(where: { $0.id == id }) }
    }
    
    /// Provides the default list of entities for the picker.
    func suggestedEntities() async throws -> [TimelineColorEntity] { await fetchItems() }

    /// Filters entities based on a search string.
    func entities(matching string: String) async throws -> [TimelineColorEntity] {
        let all = await fetchItems()
        guard !string.isEmpty else { return all }
        return all.filter { $0.title.localizedCaseInsensitiveContains(string) }
    }
    private func fetchItems() async -> [TimelineColorEntity] {
        await withCheckedContinuation { cont in
            IOSDelegateShortcuts.shared.getTimelineColorsForShortcutsWithCompletion(callback: { items in
                cont.resume(returning: items.map { TimelineColorEntity(id: $0.id, title: $0.title) })
            })
        }
    }
}

@available(iOS 16.0, *)
struct TimelineIconEntity: AppEntity {
    var id: String
    var title: String

    static var typeDisplayRepresentation: TypeDisplayRepresentation = TypeDisplayRepresentation(name: "Icon")
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(title)") }
    static var defaultQuery = TimelineIconEntityQuery()
}

@available(iOS 16.0, *)
struct TimelineIconEntityQuery: EntityQuery, EntityStringQuery {
    /// Resolves specific identifiers into entities.
    func entities(for identifiers: [String]) async throws -> [TimelineIconEntity] {
        let all = await fetchItems()
        return identifiers.compactMap { id in all.first(where: { $0.id == id }) }
    }

    /// Provides the default list of entities for the picker.
    func suggestedEntities() async throws -> [TimelineIconEntity] { await fetchItems() }

    /// Filters entities based on a search string.
    func entities(matching string: String) async throws -> [TimelineIconEntity] {
        let all = await fetchItems()
        guard !string.isEmpty else { return all }
        return all.filter { $0.title.localizedCaseInsensitiveContains(string) }
    }

    private func fetchItems() async -> [TimelineIconEntity] {
        await withCheckedContinuation { cont in
            IOSDelegateShortcuts.shared.getTimelineIconsForShortcutsWithCompletion(callback: { items in
                cont.resume(returning: items.map { TimelineIconEntity(id: $0.id, title: $0.title) })
            })
        }
    }
}

@available(iOS 16.0, *)
struct NotificationAppEntity: AppEntity {
    var id: String
    var title: String
    var muted: Bool

    static var typeDisplayRepresentation: TypeDisplayRepresentation = TypeDisplayRepresentation(
        name: "Notification App"
    )

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(title)")
    }

    static var defaultQuery = NotificationAppEntityQuery()
}

@available(iOS 16.0, *)
struct NotificationAppEntityQuery: EntityQuery, EntityStringQuery {
    /// Resolves specific identifiers into entities.
    func entities(for identifiers: [String]) async throws -> [NotificationAppEntity] {
        let all = await fetchNotificationApps()
        return identifiers.compactMap { id in
            all.first(where: { $0.id == id })
        }
    }

    /// Provides the default list of entities for the picker.
    func suggestedEntities() async throws -> [NotificationAppEntity] {
        await fetchNotificationApps()
    }

    /// Filters entities based on a search string.
    func entities(matching string: String) async throws -> [NotificationAppEntity] {
        let all = await fetchNotificationApps()
        guard !string.isEmpty else { return all }
        return all.filter { $0.title.localizedCaseInsensitiveContains(string) }
    }

    private func fetchNotificationApps() async -> [NotificationAppEntity] {
        await withCheckedContinuation { continuation in
            IOSDelegateShortcuts.shared.getNotificationAppsForShortcutsWithCompletion(callback: { items in
                continuation.resume(returning: items.map { NotificationAppEntity(id: $0.id, title: $0.title, muted: $0.isMuted) })
            })
        }
    }
}

// Intents

@available(iOS 16.0, *)
struct LaunchWatchfaceOnWatchIntent: AppIntent {
    static var title: LocalizedStringResource = "Launch Watchface on Watch"
    static var description = IntentDescription("Launches the selected watchface on your connected watch. Only works for watchfaces already on the watch (pre-loaded).", categoryName: "Watch")

    @Parameter(title: "Watchface")
    var watchface: LockerWatchfaceEntity?

    static var parameterSummary: some ParameterSummary {
        Summary("Launch \(\.$watchface) on watch")
    }

    func perform() async throws -> some IntentResult {
        if let watchface {
            IOSDelegateShortcuts.shared.launchAppByUuidWithUuid(uuid: watchface.id)
        }
        return .result()
    }
}

@available(iOS 16.0, *)
struct LaunchWatchappOnWatchIntent: AppIntent {
    static var title: LocalizedStringResource = "Launch Watch App on Watch"
    static var description = IntentDescription("Launches the selected watch app on your connected watch. Only works for apps already on the watch (pre-loaded).", categoryName: "Watch")

    @Parameter(title: "Watch App")
    var watchapp: LockerWatchappEntity?

    static var parameterSummary: some ParameterSummary {
        Summary("Launch \(\.$watchapp) on watch")
    }

    func perform() async throws -> some IntentResult {
        if let watchapp {
            IOSDelegateShortcuts.shared.launchAppByUuidWithUuid(uuid: watchapp.id)
        }
        return .result()
    }
}

@available(iOS 16.0, *)
struct SendSimpleNotificationIntent: AppIntent {
    static var title: LocalizedStringResource = "Send Simple Notification"
    static var description = IntentDescription("Sends a simple notification to your watch with a title and message.", categoryName: "Notifications")

    @Parameter(title: "Title")
    var title: String

    @Parameter(
        title: "Body",
        inputOptions: String.IntentInputOptions(multiline: true)
    )
    var message: String

    // Parameters in the closure appear "below the fold" — tap the blue chevron to see Title and Message as form fields (like Things).
    static var parameterSummary: some ParameterSummary {
        Summary("Send simple notification") {
            \.$title
            \.$message
        }
    }

    func perform() async throws -> some IntentResult {
        IOSDelegateShortcuts.shared.sendSimpleNotificationToWatchWithTitleBody(title: title, body: message)
        return .result()
    }
}

@available(iOS 16.0, *)
struct SendDetailedNotificationIntent: AppIntent {
    static var title: LocalizedStringResource = "Send Detailed Notification"
    static var description = IntentDescription("Send a notification to your watch with custom title, body, color and icon.", categoryName: "Notifications")

    @Parameter(title: "Title")
    var title: String

    @Parameter(
        title: "Body",
        inputOptions: String.IntentInputOptions(multiline: true)
    )
    var body: String

    @Parameter(title: "Color")
    var color: TimelineColorEntity?

    @Parameter(title: "Icon")
    var icon: TimelineIconEntity?

    // Parameters in the closure appear "below the fold" — tap the blue chevron to see Title, Body, Color, Icon.
    static var parameterSummary: some ParameterSummary {
        Summary("Send detailed notification") {
            \.$title
            \.$body
            \.$color
            \.$icon
        }
    }

    func perform() async throws -> some IntentResult {
        IOSDelegateShortcuts.shared.sendDetailedNotificationToWatch(
            title: title,
            body: body,
            colorName: color?.id.isEmpty == true ? nil : color?.id,
            iconCode: icon?.id.isEmpty == true ? nil : icon?.id
        )
        return .result()
    }
}

@available(iOS 16.0, *)
struct SetQuietTimeIntent: AppIntent {
    static var title: LocalizedStringResource = "Set Quiet Time"
    static var description = IntentDescription("Turn Quiet Time on or off on your watch.", categoryName: "Preferences")

    @Parameter(title: "Enable", default: true)
    var enable: Bool

    static var parameterSummary: some ParameterSummary {
        Summary("Set Quiet Time \(\.$enable)")
    }

    func perform() async throws -> some IntentResult {
        IOSDelegateShortcuts.shared.setQuietTimeEnabledWithEnabled(enabled: enable)
        return .result()
    }
}

@available(iOS 16.0, *)
struct SetQuietTimeShowNotificationsIntent: AppIntent {
    static var title: LocalizedStringResource = "Set Quiet Time Show Notifications"
    static var description = IntentDescription("Choose to show or hide notifications during Quiet Time on your watch.", categoryName: "Preferences")

    @Parameter(title: "Show Notifications", default: .show)
    var option: QuietTimeShowOption

    static var parameterSummary: some ParameterSummary {
        Summary("Set Quiet Time show notifications to \(\.$option)")
    }

    func perform() async throws -> some IntentResult {
        IOSDelegateShortcuts.shared.setQuietTimeShowNotificationsWithShow(show: option == .show)
        return .result()
    }
}

@available(iOS 16.0, *)
struct SetQuietTimeInterruptionsIntent: AppIntent {
    static var title: LocalizedStringResource = "Set Quiet Time Interruptions"
    static var description = IntentDescription("Choose which alerts are allowed during Quiet Time on your watch (e.g. phone calls only).", categoryName: "Preferences")

    @Parameter(title: "Interruptions", default: .allOff)
    var option: QuietTimeInterruptionsOption

    static var parameterSummary: some ParameterSummary {
        Summary("Set Quiet Time interruptions to \(\.$option)")
    }

    func perform() async throws -> some IntentResult {
        IOSDelegateShortcuts.shared.setQuietTimeInterruptionsWithAlertMaskName(alertMaskName: option.rawValue)
        return .result()
    }
}

@available(iOS 16.0, *)
struct SetNotificationBacklightIntent: AppIntent {
    static var title: LocalizedStringResource = "Set Notification Backlight"
    static var description = IntentDescription("Turn the backlight on or off when a notification arrives on your watch.", categoryName: "Preferences")

    @Parameter(title: "Enable", default: true)
    var enable: Bool

    static var parameterSummary: some ParameterSummary {
        Summary("Set notification backlight \(\.$enable)")
    }

    func perform() async throws -> some IntentResult {
        IOSDelegateShortcuts.shared.setNotificationBacklightWithEnabled(enabled: enable)
        return .result()
    }
}

@available(iOS 16.0, *)
struct SetMotionBacklightIntent: AppIntent {
    static var title: LocalizedStringResource = "Set Motion Backlight"
    static var description = IntentDescription("Turn the backlight on or off when you raise your wrist.", categoryName: "Preferences")

    @Parameter(title: "Enable", default: true)
    var enable: Bool

    static var parameterSummary: some ParameterSummary {
        Summary("Set motion backlight \(\.$enable)")
    }

    func perform() async throws -> some IntentResult {
        IOSDelegateShortcuts.shared.setBacklightMotionWithEnabled(enabled: enable)
        return .result()
    }
}

@available(iOS 16.0, *)
struct SetNotificationFilterIntent: AppIntent {
    static var title: LocalizedStringResource = "Set Notification Filter"
    static var description = IntentDescription("Choose which notifications trigger alerts on your watch (all, phone calls only, or all off).", categoryName: "Preferences")

    @Parameter(title: "Filter", default: .allOn)
    var option: NotificationFilterOption

    static var parameterSummary: some ParameterSummary {
        Summary("Set notification filter to \(\.$option)")
    }

    func perform() async throws -> some IntentResult {
        IOSDelegateShortcuts.shared.setNotificationFilterWithAlertMaskName(alertMaskName: option.rawValue)
        return .result()
    }
}

@available(iOS 16.0, *)
struct SetNotificationAppMuteIntent: AppIntent {
    static var title: LocalizedStringResource = "Set Notification App Mute"
    static var description = IntentDescription("Mute or unmute notifications for an app on your watch.", categoryName: "Preferences")

    @Parameter(title: "App")
    var app: NotificationAppEntity?

    @Parameter(title: "Action", default: .mute)
    var action: NotificationMuteAction

    static var parameterSummary: some ParameterSummary {
        Summary("\(\.$action) \(\.$app) notifications")
    }

    func perform() async throws -> some IntentResult {
        if let app {
            IOSDelegateShortcuts.shared.setNotificationAppMuteStateWithPackageNameMute(packageName: app.id, mute: action == .mute)
        }
        return .result()
    }
}

@available(iOS 16.0, *)
struct InsertTimelinePinIntent: AppIntent {
    static var title: LocalizedStringResource = "Insert Timeline Pin"
    static var description = IntentDescription("Add a pin to the watch timeline. Choose the date and time. Use Return ID to get the Pin ID for \"Delete Timeline Pin\".", categoryName: "Timeline")

    @Parameter(title: "Title")
    var title: String

    @Parameter(title: "Body", inputOptions: String.IntentInputOptions(multiline: true))
    var body: String

    @Parameter(title: "Date & Time", description: "When the pin should appear.")
    var pinDate: Date?

    @Parameter(title: "Subtitle")
    var subtitle: String?

    @Parameter(title: "Icon")
    var icon: TimelineIconEntity?

    @Parameter(title: "Return ID", description: "When on, returns the Pin ID for use with \"Delete Timeline Pin\".", default: false)
    var returnId: Bool

    static var parameterSummary: some ParameterSummary {
        Summary("Insert timeline pin") {
            \.$title
            \.$body
            \.$pinDate
            \.$subtitle
            \.$icon
            \.$returnId
        }
    }

    func perform() async throws -> some IntentResult & ReturnsValue<String> {
        let epochSeconds = Int64(pinDate?.timeIntervalSince1970 ?? 0)
        let pinId = IOSDelegateShortcuts.shared.insertTimelinePinRichWithTitleBodySubtitleIconCodeEpochSeconds(
            title: title,
            body: body,
            subtitle: subtitle,
            iconCode: icon?.id,
            epochSeconds: epochSeconds
        )
        return .result(value: returnId ? pinId : "")
    }
}

@available(iOS 16.0, *)
struct DeleteTimelinePinIntent: AppIntent {
    static var title: LocalizedStringResource = "Delete Timeline Pin"
    static var description = IntentDescription("Remove a timeline pin from the watch. Use the Pin ID returned by \"Insert Timeline Pin\" when Return ID is on.", categoryName: "Timeline")

    @Parameter(title: "Pin ID")
    var pinId: String

    static var parameterSummary: some ParameterSummary {
        Summary("Delete timeline pin") {
            \.$pinId
        }
    }

    func perform() async throws -> some IntentResult {
        guard !pinId.isEmpty else { return .result() }
        IOSDelegateShortcuts.shared.deleteTimelinePinWithPinId(pinId: pinId)
        return .result()
    }
}

@available(iOS 16.0, *)
struct GetWatchBatteryLevelIntent: AppIntent {
    static var title: LocalizedStringResource = "Get Watch Battery Level"
    static var description = IntentDescription("Returns the battery percentage (0–100) of the connected watch. Returns empty if no watch is connected or battery is unknown.", categoryName: "Watch")

    static var parameterSummary: some ParameterSummary {
        Summary("Get watch battery level")
    }

    func perform() async throws -> some IntentResult & ReturnsValue<String> {
        let value = await withCheckedContinuation { (continuation: CheckedContinuation<String, Never>) in
            IOSDelegateShortcuts.shared.getWatchBatteryLevelWithCompletion(callback: { level in
                continuation.resume(returning: level)
            })
        }
        return .result(value: value)
    }
}

@available(iOS 16.0, *)
struct GetWatchConnectedIntent: AppIntent {
    static var title: LocalizedStringResource = "Verify Watch Connected"
    static var description = IntentDescription("Returns true if a watch is fully connected, false otherwise.", categoryName: "Watch")

    static var parameterSummary: some ParameterSummary {
        Summary("Get watch connected")
    }

    func perform() async throws -> some IntentResult & ReturnsValue<Bool> {
        let value = await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
            IOSDelegateShortcuts.shared.getWatchConnectedWithCompletion(callback: { connected in
                continuation.resume(returning: connected.boolValue)
            })
        }
        return .result(value: value)
    }
}

@available(iOS 16.0, *)
struct GetWatchNameIntent: AppIntent {
    static var title: LocalizedStringResource = "Get Watch Name"
    static var description = IntentDescription("Returns the name of the connected watch. Returns empty if no watch is connected.", categoryName: "Watch")

    static var parameterSummary: some ParameterSummary {
        Summary("Get watch name")
    }

    func perform() async throws -> some IntentResult & ReturnsValue<String> {
        let value = await withCheckedContinuation { (continuation: CheckedContinuation<String, Never>) in
            IOSDelegateShortcuts.shared.getWatchNameWithCompletion(callback: { name in
                continuation.resume(returning: name)
            })
        }
        return .result(value: value)
    }
}

@available(iOS 16.0, *)
struct GetWatchHealthStatsIntent: AppIntent {
    static var title: LocalizedStringResource = "Get Watch Health Stats"
    static var description = IntentDescription("Returns health stats (steps, sleep) as JSON. Returns empty object on error.", categoryName: "Watch")

    static var parameterSummary: some ParameterSummary {
        Summary("Get watch health stats")
    }

    func perform() async throws -> some IntentResult & ReturnsValue<String> {
        let value = await withCheckedContinuation { (continuation: CheckedContinuation<String, Never>) in
            IOSDelegateShortcuts.shared.getWatchHealthStatsWithCompletion(callback: { json in
                continuation.resume(returning: json)
            })
        }
        return .result(value: value)
    }
}

@available(iOS 16.0, *)
struct GetWatchScreenshotIntent: AppIntent {
    static var title: LocalizedStringResource = "Get Watch Screenshot"
    static var description = IntentDescription("Returns the current watch screen as a PNG image. Empty image if no watch connected or screenshot failed.", categoryName: "Watch")

    static var parameterSummary: some ParameterSummary {
        Summary("Get watch screenshot")
    }

    func perform() async throws -> some IntentResult & ReturnsValue<IntentFile> {
        let data = await withCheckedContinuation { (continuation: CheckedContinuation<Data, Never>) in
            IOSDelegateShortcuts.shared.getWatchScreenshotRawBytes(callback: { bytes in
                continuation.resume(returning: bytes ?? Data())
            })
        }
        let file = IntentFile(data: data, filename: "watch_screenshot.png")
        return .result(value: file)
    }
}

// Shortcuts Provider

@available(iOS 16.0, *)
struct PebbleShortcutsProvider: AppShortcutsProvider {
    @AppShortcutsBuilder
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: SendSimpleNotificationIntent(),
            phrases: [
                "Send simple notification in \(.applicationName)"
            ]
        )
        AppShortcut(
            intent: SendDetailedNotificationIntent(),
            phrases: [
                "Send detailed notification in \(.applicationName)",
                "Send notification with title and icon in \(.applicationName)"
            ]
        )
        AppShortcut(
            intent: LaunchWatchfaceOnWatchIntent(),
            phrases: [
                "Launch watchface on watch in \(.applicationName)",
                "Open watchface on watch in \(.applicationName)"
            ]
        )
        AppShortcut(
            intent: LaunchWatchappOnWatchIntent(),
            phrases: [
                "Launch watch app on watch in \(.applicationName)",
                "Open watch app on watch in \(.applicationName)"
            ]
        )
        AppShortcut(
            intent: InsertTimelinePinIntent(),
            phrases: [
                "Insert timeline pin in \(.applicationName)",
                "Add timeline pin in \(.applicationName)"
            ]
        )
        AppShortcut(
            intent: GetWatchScreenshotIntent(),
            phrases: [
                "Get watch screenshot in \(.applicationName)",
                "Watch screenshot in \(.applicationName)"
            ]
        )
    }
}
