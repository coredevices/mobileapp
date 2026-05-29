package coredevices.ring.ui.navigation

import CoreNav
import CoreRoute
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.toRoute
import androidx.savedstate.read
import coredevices.ring.ui.dialog.ListenDialog
import coredevices.ring.ui.screens.indexfeed.AllAnswers
import coredevices.ring.ui.screens.indexfeed.AllLists
import coredevices.ring.ui.screens.indexfeed.FullFeed
import coredevices.ring.ui.screens.indexfeed.ObjectDetail
import coredevices.ring.ui.screens.notes.ReminderDetails
import coredevices.ring.ui.screens.recording.RecordingDetails
import coredevices.ring.ui.screens.settings.NotionOAuthResult
import coredevices.ring.ui.screens.settings.IndexSettings
import kotlinx.serialization.Serializable
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.ring.ui.screens.RingSyncInspectorScreen
import coredevices.ring.ui.screens.settings.AddIntegration
import coredevices.ring.ui.screens.settings.McpSandboxSettings
import kotlinx.coroutines.flow.flow
import org.koin.compose.koinInject

/** Marker for routes that belong to the Index/Ring feature. The
 *  WatchHomeScreen registers these in its inner NavHost too so they
 *  render WITH the bottom NavigationBar still visible — without the
 *  marker we'd have no way to tell from a generic [CoreRoute] which
 *  routes should be inner-scoped vs. outer-scoped. */
interface RingRoute : CoreRoute

object RingRoutes {
    @Serializable
    class RecordingDetails(val recordingId: Long) : RingRoute
    @Serializable
    class ReminderDetails(val reminderId: Int) : RingRoute
    /** Detail page for an item (`note`/`reminder`/`scheduled`/`message`/
     *  `answer`/`action_log`) or a list. The id is the Firestore doc id.
     *  When [startEditing] is true and the object is a list, the screen
     *  enters rename mode immediately on first composition (used by the
     *  "+ New list" flow on AllLists). */
    @Serializable
    class ObjectDetails(val objectId: String, val startEditing: Boolean = false) : RingRoute
    /** Full chronological feed of recordings. */
    @Serializable
    data object FullFeed : RingRoute
    /** Grid of every list except the system Todos. */
    @Serializable
    data object AllLists : RingRoute
    /** Every Q&A capture, newest first. */
    @Serializable
    data object AllAnswers : RingRoute
    @Serializable
    data object Settings : RingRoute
    @Serializable
    data object ListenDialog : RingRoute
    @Serializable
    data object RingSyncInspector : RingRoute
    @Serializable
    data object McpSandboxSettings : RingRoute
    @Serializable
    data object AddIntegration : RingRoute
}

fun NavGraphBuilder.addRingRoutes(coreNav: CoreNav) {
    composable<RingRoutes.RecordingDetails> {
        val route: RingRoutes.RecordingDetails = it.toRoute()
        RecordingDetails(route.recordingId, coreNav)
    }
    composable<RingRoutes.ReminderDetails> {
        val route: RingRoutes.ReminderDetails = it.toRoute()
        ReminderDetails(coreNav, route.reminderId)
    }
    composable<RingRoutes.ObjectDetails> {
        val route: RingRoutes.ObjectDetails = it.toRoute()
        ObjectDetail(coreNav, route.objectId, startEditing = route.startEditing)
    }
    composable<RingRoutes.FullFeed> {
        FullFeed(coreNav)
    }
    composable<RingRoutes.AllLists> {
        AllLists(coreNav)
    }
    composable<RingRoutes.AllAnswers> {
        AllAnswers(coreNav)
    }
    composable<RingRoutes.Settings> {
        IndexSettings(coreNav)
    }
    composable(
        "notion_oauth/{result}",
        deepLinks = listOf(
            NavDeepLink("voiceapp://notion_oauth/{result}")
        )
    ) {
        val result = it.arguments?.read { getString("result") }
        NotionOAuthResult(result == "success") { coreNav.goBack() }
    }
    dialog<RingRoutes.ListenDialog>(
        deepLinks = listOf(
            NavDeepLink("voiceapp://listen")
        )
    ) {
        ListenDialog { coreNav.goBack() }
    }
    composable<RingRoutes.RingSyncInspector> {
        RingSyncInspectorScreen(coreNav)
    }
    composable<RingRoutes.McpSandboxSettings> {
        val repo = koinInject<McpSandboxRepository>()
        val defaultGroup by remember { flow{ emit(repo.getDefaultGroupId()) } }.collectAsState(initial = null)
        defaultGroup?.let {
            McpSandboxSettings(coreNav, it)
        }
    }
    composable<RingRoutes.AddIntegration> {
        AddIntegration(coreNav)
    }
}