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
import coredevices.ring.ui.screens.RingDebug
import coredevices.ring.ui.screens.RingPairing
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

object RingRoutes {
    @Serializable
    class RecordingDetails(val recordingId: Long) : CoreRoute
    @Serializable
    class ReminderDetails(val reminderId: Int) : CoreRoute
    @Serializable
    data object Settings : CoreRoute
    @Serializable
    data object ListenDialog : CoreRoute
    @Serializable
    data object RingDebug : CoreRoute
    @Serializable
    data object RingPairing : CoreRoute
    @Serializable
    data object RingSyncInspector : CoreRoute
    @Serializable
    data object McpSandboxSettings : CoreRoute
    @Serializable
    data object AddIntegration : CoreRoute
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
    composable<RingRoutes.RingDebug> {
        RingDebug(coreNav)
    }
    composable<RingRoutes.RingPairing> {
        RingPairing(coreNav)
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