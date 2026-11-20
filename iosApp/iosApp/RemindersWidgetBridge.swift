import ComposeApp
import WidgetKit

/// Writes each reminders snapshot from Kotlin to the App Group and reloads the widget.
enum RemindersWidgetBridge {
    static func register() {
        IOSDelegate.shared.registerRemindersWidget { json in
            RemindersWidgetStore.write(json)
            WidgetCenter.shared.reloadTimelines(ofKind: RemindersWidgetStore.kind)
        }
    }
}
