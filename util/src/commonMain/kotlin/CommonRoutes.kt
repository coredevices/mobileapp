import androidx.compose.runtime.Stable
import io.rebble.libpebblecommon.locker.AppType
import kotlinx.serialization.Serializable

/**
 * All routes which take up the whole screen (i.e. don't sit between the top bar/nav bar).
 */
interface CoreRoute

object CommonRoutes {
    @Serializable
    class BugReport(
        val pebble: Boolean,
        val recordingPath: String? = null,
        val screenshotPath: String? = null,
        /** Deep link only: base64url-encoded context attached to the report. */
        val context: String? = null,
        /** Deep link only: prefilled description. */
        val text: String? = null,
    ) : CoreRoute

    @Serializable
    data object SignIn : CoreRoute

    @Serializable
    data object AlphaTestInstructionsRoute : CoreRoute

    @Serializable
    data object ViewMyBugReportsRoute : CoreRoute

    @Serializable
    data object TroubleshootingRoute : CoreRoute

    @Serializable
    data class ViewBugReportRoute(
        val conversationId: String,
    ) : CoreRoute

    @Serializable
    data object RoadmapChangelogRoute : CoreRoute

    @Serializable
    data object PebbleOsChangelogRoute : CoreRoute

    @Serializable
    data object OnboardingRoute : CoreRoute

    @Serializable
    data object WatchOnboardingRoute : CoreRoute

    @Serializable
    data class WatchappSettingsRoute(
        val uuid: String,
        val title: String,
    ) : CoreRoute

    @Serializable
    data class LockerAppRoute(val uuid: String) : CoreRoute

    @Serializable
    data class LockerTabRoute(val initialTab: AppType) : CoreRoute

    @Serializable
    data object RingOnboardingRoute : CoreRoute

    /** Dialog offering to download the on-device speech model the configured engine needs.
     *  Routed so it can be opened from a notification on top of whatever screen is showing. */
    @Serializable
    data object SpeechModelDownloadDialog : CoreRoute

    const val SPEECH_MODEL_DOWNLOAD_DEEP_LINK_PATH = "speech-model"
    const val SPEECH_MODEL_DOWNLOAD_DEEP_LINK = "pebblecore://deep-link/$SPEECH_MODEL_DOWNLOAD_DEEP_LINK_PATH"
}

@Stable
interface CoreNav {
    fun navigateTo(route: CoreRoute)
    fun goBack()
    fun goBackToPebble()
    fun replaceWith(route: CoreRoute) {
        goBack()
        navigateTo(route)
    }
}

object NoOpCoreNav : CoreNav {
    override fun navigateTo(route: CoreRoute) {}
    override fun goBack() {}
    override fun goBackToPebble() {}
}
