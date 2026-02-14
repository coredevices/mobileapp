import AppIntents
import ComposeApp
import Foundation

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
    func entities(for identifiers: [String]) async throws -> [LockerWatchfaceEntity] {
        let all = await fetchLockerItems()
        return identifiers.compactMap { id in
            all.first(where: { $0.id == id }).map { LockerWatchfaceEntity(id: $0.id, title: $0.title) }
        }
    }

    func suggestedEntities() async throws -> [LockerWatchfaceEntity] {
        await fetchLockerItems()
    }

    func entities(matching string: String) async throws -> [LockerWatchfaceEntity] {
        let all = await fetchLockerItems()
        guard !string.isEmpty else { return all }
        return all.filter { $0.title.localizedCaseInsensitiveContains(string) }
    }

    private func fetchLockerItems() async -> [LockerWatchfaceEntity] {
        await withCheckedContinuation { continuation in
            IOSDelegate.shared.getLockerWatchfacesForShortcutsWithCompletion(callback: { json in
                let items = Self.parseLockerItemsJSON(json)
                continuation.resume(returning: items)
            }) 
        }
    }

    private static func parseLockerItemsJSON(_ json: String) -> [LockerWatchfaceEntity] {
        guard let data = json.data(using: .utf8),
              let array = try? JSONDecoder().decode([[String: String]].self, from: data) else {
            return []
        }
        return array.compactMap { dict in
            guard let id = dict["id"], let title = dict["title"] else { return nil }
            return LockerWatchfaceEntity(id: id, title: title)
        }
    }
}

@available(iOS 16.0, *)
struct LaunchWatchfaceOnWatchIntent: AppIntent {
    static var title: LocalizedStringResource = "Launch Watchface on Watch"
    static var description = IntentDescription("Launches the selected watchface on your connected watch. Only works for watchfaces already on the watch (pre-loaded).")

    @Parameter(title: "Watchface")
    var watchface: LockerWatchfaceEntity?

    static var parameterSummary: some ParameterSummary {
        Summary("Launch \(\.$watchface) on watch")
    }

    func perform() async throws -> some IntentResult {
        if let watchface {
            IOSDelegate.shared.launchAppByUuidWithUuid(uuid: watchface.id)
        }
        return .result()
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
    func entities(for identifiers: [String]) async throws -> [LockerWatchappEntity] {
        let all = await fetchLockerItems()
        return identifiers.compactMap { id in
            all.first(where: { $0.id == id }).map { LockerWatchappEntity(id: $0.id, title: $0.title) }
        }
    }

    func suggestedEntities() async throws -> [LockerWatchappEntity] {
        await fetchLockerItems()
    }

    func entities(matching string: String) async throws -> [LockerWatchappEntity] {
        let all = await fetchLockerItems()
        guard !string.isEmpty else { return all }
        return all.filter { $0.title.localizedCaseInsensitiveContains(string) }
    }

    private func fetchLockerItems() async -> [LockerWatchappEntity] {
        await withCheckedContinuation { continuation in
            IOSDelegate.shared.getLockerWatchappsForShortcutsWithCompletion(callback: { json in
                let items = Self.parseLockerItemsJSON(json)
                continuation.resume(returning: items)
            })
        }
    }

    private static func parseLockerItemsJSON(_ json: String) -> [LockerWatchappEntity] {
        guard let data = json.data(using: .utf8),
              let array = try? JSONDecoder().decode([[String: String]].self, from: data) else {
            return []
        }
        return array.compactMap { dict in
            guard let id = dict["id"], let title = dict["title"] else { return nil }
            return LockerWatchappEntity(id: id, title: title)
        }
    }
}

@available(iOS 16.0, *)
struct LaunchWatchappOnWatchIntent: AppIntent {
    static var title: LocalizedStringResource = "Launch Watch App on Watch"
    static var description = IntentDescription("Launches the selected watch app on your connected watch. Only works for apps already on the watch (pre-loaded).")

    @Parameter(title: "Watch App")
    var watchapp: LockerWatchappEntity?

    static var parameterSummary: some ParameterSummary {
        Summary("Launch \(\.$watchapp) on watch")
    }

    func perform() async throws -> some IntentResult {
        if let watchapp {
            IOSDelegate.shared.launchAppByUuidWithUuid(uuid: watchapp.id)
        }
        return .result()
    }
}


@available(iOS 16.0, *)
struct SendSimpleNotificationIntent: AppIntent {
    static var title: LocalizedStringResource = "Send Simple Notification"
    static var description = IntentDescription("Sends a simple notification to your watch with a title and message.")

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
        IOSDelegate.shared.sendSimpleNotificationToWatchWithTitleBody(title: title, body: message)
        return .result()
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
    func entities(for identifiers: [String]) async throws -> [TimelineColorEntity] {
        let all = await fetchItems()
        return identifiers.compactMap { id in all.first(where: { $0.id == id }) }
    }
    func suggestedEntities() async throws -> [TimelineColorEntity] { await fetchItems() }
    func entities(matching string: String) async throws -> [TimelineColorEntity] {
        let all = await fetchItems()
        guard !string.isEmpty else { return all }
        return all.filter { $0.title.localizedCaseInsensitiveContains(string) }
    }
    private func fetchItems() async -> [TimelineColorEntity] {
        await withCheckedContinuation { cont in
            IOSDelegate.shared.getTimelineColorsForShortcutsWithCompletion(callback: { json in
                let items = (try? JSONDecoder().decode([[String: String]].self, from: Data(json.utf8))) ?? []
                cont.resume(returning: items.compactMap { d in guard let id = d["id"], let t = d["title"] else { return nil }; return TimelineColorEntity(id: id, title: t) })
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
    func entities(for identifiers: [String]) async throws -> [TimelineIconEntity] {
        let all = await fetchItems()
        return identifiers.compactMap { id in all.first(where: { $0.id == id }) }
    }
    func suggestedEntities() async throws -> [TimelineIconEntity] { await fetchItems() }
    func entities(matching string: String) async throws -> [TimelineIconEntity] {
        let all = await fetchItems()
        guard !string.isEmpty else { return all }
        return all.filter { $0.title.localizedCaseInsensitiveContains(string) }
    }
    private func fetchItems() async -> [TimelineIconEntity] {
        await withCheckedContinuation { cont in
            IOSDelegate.shared.getTimelineIconsForShortcutsWithCompletion(callback: { json in
                let items = (try? JSONDecoder().decode([[String: String]].self, from: Data(json.utf8))) ?? []
                cont.resume(returning: items.compactMap { d in guard let id = d["id"], let t = d["title"] else { return nil }; return TimelineIconEntity(id: id, title: t) })
            })
        }
    }
}

@available(iOS 16.0, *)
struct SendDetailedNotificationIntent: AppIntent {
    static var title: LocalizedStringResource = "Send Detailed Notification"
    static var description = IntentDescription("Send a notification to your watch with custom title, body, color and icon.")

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
        IOSDelegate.shared.sendDetailedNotificationToWatch(
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
    static var description = IntentDescription("Turn Quiet Time on or off on your watch.")

    @Parameter(title: "Enable", default: true)
    var enable: Bool

    static var parameterSummary: some ParameterSummary {
        Summary("Set Quiet Time \(\.$enable)")
    }

    func perform() async throws -> some IntentResult {
        IOSDelegate.shared.setQuietTimeEnabledWithEnabled(enabled: enable)
        return .result()
    }
}

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
struct SetQuietTimeShowNotificationsIntent: AppIntent {
    static var title: LocalizedStringResource = "Set Quiet Time Show Notifications"
    static var description = IntentDescription("Choose to show or hide notifications during Quiet Time on your watch.")

    @Parameter(title: "Show Notifications", default: .show)
    var option: QuietTimeShowOption

    static var parameterSummary: some ParameterSummary {
        Summary("Set Quiet Time show notifications to \(\.$option)")
    }

    func perform() async throws -> some IntentResult {
        IOSDelegate.shared.setQuietTimeShowNotificationsWithShow(show: option == .show)
        return .result()
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
struct SetQuietTimeInterruptionsIntent: AppIntent {
    static var title: LocalizedStringResource = "Set Quiet Time Interruptions"
    static var description = IntentDescription("Choose which alerts are allowed during Quiet Time on your watch (e.g. phone calls only).")

    @Parameter(title: "Interruptions", default: .allOff)
    var option: QuietTimeInterruptionsOption

    static var parameterSummary: some ParameterSummary {
        Summary("Set Quiet Time interruptions to \(\.$option)")
    }

    func perform() async throws -> some IntentResult {
        IOSDelegate.shared.setQuietTimeInterruptionsWithAlertMaskName(alertMaskName: option.rawValue)
        return .result()
    }
}

@available(iOS 16.0, *)
struct SetNotificationBacklightIntent: AppIntent {
    static var title: LocalizedStringResource = "Set Notification Backlight"
    static var description = IntentDescription("Turn the backlight on or off when a notification arrives on your watch.")

    @Parameter(title: "Enable", default: true)
    var enable: Bool

    static var parameterSummary: some ParameterSummary {
        Summary("Set notification backlight \(\.$enable)")
    }

    func perform() async throws -> some IntentResult {
        IOSDelegate.shared.setNotificationBacklightWithEnabled(enabled: enable)
        return .result()
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
struct SetNotificationFilterIntent: AppIntent {
    static var title: LocalizedStringResource = "Set Notification Filter"
    static var description = IntentDescription("Choose which notifications trigger alerts on your watch (all, phone calls only, or all off).")

    @Parameter(title: "Filter", default: .allOn)
    var option: NotificationFilterOption

    static var parameterSummary: some ParameterSummary {
        Summary("Set notification filter to \(\.$option)")
    }

    func perform() async throws -> some IntentResult {
        IOSDelegate.shared.setNotificationFilterWithAlertMaskName(alertMaskName: option.rawValue)
        return .result()
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
    func entities(for identifiers: [String]) async throws -> [NotificationAppEntity] {
        let all = await fetchNotificationApps()
        return identifiers.compactMap { id in
            all.first(where: { $0.id == id }).map { NotificationAppEntity(id: $0.id, title: $0.title, muted: $0.muted) }
        }
    }

    func suggestedEntities() async throws -> [NotificationAppEntity] {
        await fetchNotificationApps()
    }

    func entities(matching string: String) async throws -> [NotificationAppEntity] {
        let all = await fetchNotificationApps()
        guard !string.isEmpty else { return all }
        return all.filter { $0.title.localizedCaseInsensitiveContains(string) }
    }

    private func fetchNotificationApps() async -> [NotificationAppEntity] {
        await withCheckedContinuation { continuation in
            IOSDelegate.shared.getNotificationAppsForShortcutsWithCompletion(callback: { json in
                let items = Self.parseNotificationAppsJSON(json)
                continuation.resume(returning: items)
            })
        }
    }

    private static func parseNotificationAppsJSON(_ json: String) -> [NotificationAppEntity] {
        struct Item: Decodable {
            let id: String
            let title: String
            let muted: Bool
        }
        guard let data = json.data(using: .utf8),
              let array = try? JSONDecoder().decode([Item].self, from: data) else {
            return []
        }
        return array.map { NotificationAppEntity(id: $0.id, title: $0.title, muted: $0.muted) }
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

@available(iOS 16.0, *)
struct SetNotificationAppMuteIntent: AppIntent {
    static var title: LocalizedStringResource = "Set Notification App Mute"
    static var description = IntentDescription("Mute or unmute notifications for an app on your watch.")

    @Parameter(title: "App")
    var app: NotificationAppEntity?

    @Parameter(title: "Action", default: .mute)
    var action: NotificationMuteAction

    static var parameterSummary: some ParameterSummary {
        Summary("\(\.$action) \(\.$app) notifications")
    }

    func perform() async throws -> some IntentResult {
        if let app {
            IOSDelegate.shared.setNotificationAppMuteStateWithPackageNameMute(packageName: app.id, mute: action == .mute)
        }
        return .result()
    }
}

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
            intent: SetQuietTimeIntent(),
            phrases: [
                "Set Quiet Time in \(.applicationName)",
                "Enable Quiet Time in \(.applicationName)",
                "Disable Quiet Time in \(.applicationName)"
            ]
        )
        AppShortcut(
            intent: SetQuietTimeShowNotificationsIntent(),
            phrases: [
                "Set Quiet Time show notifications in \(.applicationName)",
                "Quiet Time show or hide notifications in \(.applicationName)"
            ]
        )
        AppShortcut(
            intent: SetQuietTimeInterruptionsIntent(),
            phrases: [
                "Set Quiet Time interruptions in \(.applicationName)"
            ]
        )
        AppShortcut(
            intent: SetNotificationBacklightIntent(),
            phrases: [
                "Set notification backlight in \(.applicationName)"
            ]
        )
        AppShortcut(
            intent: SetNotificationFilterIntent(),
            phrases: [
                "Set notification filter in \(.applicationName)"
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
            intent: SetNotificationAppMuteIntent(),
            phrases: [
                "Set notification app mute in \(.applicationName)",
                "Mute notification app in \(.applicationName)"
            ]
        )
    }
}
