package coredevices.pebble.ui

import PlatformUiContext
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleIdentifier
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.languageIdentifier
import platform.Speech.SFSpeechRecognizer

@OptIn(ExperimentalComposeUiApi::class)
actual fun makeTokenClipEntry(token: String): ClipEntry = ClipEntry.withPlainText(token)

actual fun openGoogleFitApp(uiContext: PlatformUiContext?) {
    // Not applicable on iOS - the menu item is only shown on Android
}

actual fun getPlatformSTTLanguages(): List<Pair<String, String>> {
    @Suppress("UNCHECKED_CAST")
    val locales = SFSpeechRecognizer.supportedLocales() as Set<NSLocale>
    return locales.filter {
        SFSpeechRecognizer(it).supportsOnDeviceRecognition
    }.map { locale ->
        val code = locale.languageIdentifier
        val name = locale.displayNameForKey(NSLocaleIdentifier, value = code) ?: code
        code to name
    }.sortedBy { it.second }
}

@androidx.compose.runtime.Composable
internal actual fun phoneNotificationDismissalSetting(): SettingsItem? {
    if (!coredevices.pebble.supportsGuidedPebbleEnrollment()) return null
    val defaults = androidx.compose.runtime.remember {
        coredevices.pebble.notificationBridgeAppGroup()?.let { platform.Foundation.NSUserDefaults(suiteName = it) }
    }
    val enabled = androidx.compose.runtime.remember {
        defaults?.synchronize()
        androidx.compose.runtime.mutableStateOf(defaults?.boolForKey("notificationDismissOnBoth.v1") ?: false)
    }
    return basicSettingsToggleItem(
        id = "DismissNotificationsOnPhone",
        title = "Dismiss Notifications on Phone",
        description = "Turn on Dismissing notifications on the iPhone when you choose Dismiss on the watch",
        topLevelType = TopLevelType.Watch,
        section = Section.Notifications,
        checked = enabled.value,
        onCheckChanged = { value ->
            defaults?.setBool(value, forKey = "notificationDismissOnBoth.v1")
            defaults?.synchronize()
            enabled.value = value
            platform.Foundation.NSNotificationCenter.defaultCenter.postNotificationName(
                coredevices.pebble.notificationBridgeName("notification-dismissal-setting-changed"),
                `object` = null,
                userInfo = mapOf("enabled" to value),
            )
        },
    )
}
