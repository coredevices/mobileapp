import androidx.compose.runtime.Stable
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
}

@Stable
interface CoreNav {
    fun navigateTo(route: CoreRoute)
    fun goBack()
    fun goBackToPebble()
}

object NoOpCoreNav : CoreNav {
    override fun navigateTo(route: CoreRoute) {}
    override fun goBack() {}
    override fun goBackToPebble() {}
}