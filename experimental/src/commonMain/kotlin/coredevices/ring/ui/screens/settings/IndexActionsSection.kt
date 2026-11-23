package coredevices.ring.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withCompositionLocal
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.settings_actions_add_mcp_server
import coreapp.ring.generated.resources.settings_actions_add_mcp_server_subtitle
import coreapp.ring.generated.resources.settings_actions_add_more_later
import coreapp.ring.generated.resources.settings_actions_add_plugin
import coreapp.ring.generated.resources.settings_actions_add_plugin_browse
import coreapp.ring.generated.resources.settings_actions_add_plugin_later
import coreapp.ring.generated.resources.settings_actions_allowed
import coreapp.ring.generated.resources.settings_actions_beeper_messaging
import coreapp.ring.generated.resources.settings_actions_beeper_messaging_example
import coreapp.ring.generated.resources.settings_actions_bug_reports
import coreapp.ring.generated.resources.settings_actions_bug_reports_example
import coreapp.ring.generated.resources.settings_actions_calendar
import coreapp.ring.generated.resources.settings_actions_calendar_example
import coreapp.ring.generated.resources.settings_actions_capture_note_subtitle
import coreapp.ring.generated.resources.settings_actions_capture_reminder_subtitle
import coreapp.ring.generated.resources.settings_actions_capture_sheet_subtitle
import coreapp.ring.generated.resources.settings_actions_capture_sheet_title
import coreapp.ring.generated.resources.settings_actions_capture_type_note
import coreapp.ring.generated.resources.settings_actions_capture_type_note_title
import coreapp.ring.generated.resources.settings_actions_capture_type_reminder
import coreapp.ring.generated.resources.settings_actions_capture_type_reminder_title
import coreapp.ring.generated.resources.settings_actions_configure_plugin
import coreapp.ring.generated.resources.settings_actions_contacts_count
import coreapp.ring.generated.resources.settings_actions_denied
import coreapp.ring.generated.resources.settings_actions_description
import coreapp.ring.generated.resources.settings_actions_get_more
import coreapp.ring.generated.resources.settings_actions_grant_actions
import coreapp.ring.generated.resources.settings_actions_grant_sources
import coreapp.ring.generated.resources.settings_actions_header
import coreapp.ring.generated.resources.settings_actions_lists
import coreapp.ring.generated.resources.settings_actions_lists_example
import coreapp.ring.generated.resources.settings_actions_mcp_server
import coreapp.ring.generated.resources.settings_actions_no_agent
import coreapp.ring.generated.resources.settings_actions_notes
import coreapp.ring.generated.resources.settings_actions_notes_example
import coreapp.ring.generated.resources.settings_actions_notification_reply
import coreapp.ring.generated.resources.settings_actions_notification_reply_example
import coreapp.ring.generated.resources.settings_actions_plugin
import coreapp.ring.generated.resources.settings_actions_plugin_actions_count
import coreapp.ring.generated.resources.settings_actions_plugin_sources_count
import coreapp.ring.generated.resources.settings_actions_reminders
import coreapp.ring.generated.resources.settings_actions_reminders_example
import coreapp.ring.generated.resources.settings_actions_saves_to
import coreapp.ring.generated.resources.settings_actions_sentence_note
import coreapp.ring.generated.resources.settings_actions_sentence_reminder
import coreapp.ring.generated.resources.settings_actions_timers_alarms
import coreapp.ring.generated.resources.settings_actions_timers_alarms_example
import coreapp.ring.generated.resources.settings_actions_where_lists_save
import coreapp.ring.generated.resources.settings_actions_where_notes_save
import coreapp.ring.generated.resources.settings_actions_where_reminders_save
import coreapp.ring.generated.resources.settings_gesture_dest_index_agent
import coreapp.util.generated.resources.cancel
import coreapp.util.generated.resources.connect
import coreapp.util.generated.resources.settings
import coredevices.ring.agent.DefaultCaptureType
import coredevices.ring.agent.IndexAction
import coredevices.ring.agent.IndexPlugin
import coredevices.ring.agent.NOT_CONNECTED_REASON
import coredevices.ring.agent.builtin_servlets.calendar.CalendarServlet
import coredevices.ring.agent.builtin_servlets.bugreport.BugReportServlet
import coredevices.ring.agent.builtin_servlets.clock.ClockServlet
import coredevices.ring.agent.builtin_servlets.messaging.MessagingServlet
import coredevices.ring.agent.builtin_servlets.notifications.NotificationReplyServlet
import coredevices.ring.agent.builtin_servlets.notes.NoteServlet
import coredevices.ring.agent.builtin_servlets.reminders.ListServlet
import coredevices.ring.agent.builtin_servlets.reminders.ReminderServlet
import coredevices.ring.agent.integrations.providers.BuiltInIntegrationProviders
import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.agent.pebble_plugin.PblPluginIntegrationProvider
import coredevices.ring.agent.pebble_plugin.PluginGrant
import coredevices.ring.agent.pebble_plugin.StorePlugin
import coredevices.indexai.data.entity.mcp.HttpMcpServerEntity
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.RingGesture
import coredevices.ring.ui.components.SectionHeader
import coredevices.ring.ui.screens.settings.geofence.GeofenceLocationsListDialog
import coredevices.ring.ui.screens.settings.mcp.HttpServerEditDialog
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.theme.toggleButtonColors
import coredevices.ring.ui.viewmodel.DestinationItem
import coredevices.ring.ui.viewmodel.isConnected
import coredevices.ring.ui.viewmodel.isPlugin
import coredevices.ui.M3Dialog
import io.rebble.libpebblecommon.permissions.InstallReview
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid
import coreapp.util.generated.resources.Res as UtilRes

/** Actions are only reachable through an Index agent, so they are off when no gesture routes to one. */
internal fun nothingRoutesToIndexAgent(routes: Map<RingGesture, GestureDestination>): Boolean =
    routes.values.none { it == GestureDestination.IndexAgent || it == GestureDestination.LocalAgent }

internal fun captureTypeLabel(type: DefaultCaptureType): StringResource = when (type) {
    DefaultCaptureType.Note -> Res.string.settings_actions_capture_type_note
    DefaultCaptureType.Reminder -> Res.string.settings_actions_capture_type_reminder
}

private fun captureSentence(type: DefaultCaptureType): StringResource = when (type) {
    DefaultCaptureType.Note -> Res.string.settings_actions_sentence_note
    DefaultCaptureType.Reminder -> Res.string.settings_actions_sentence_reminder
}

private fun captureTypeTitle(type: DefaultCaptureType): StringResource = when (type) {
    DefaultCaptureType.Note -> Res.string.settings_actions_capture_type_note_title
    DefaultCaptureType.Reminder -> Res.string.settings_actions_capture_type_reminder_title
}

@Composable
internal fun contactsBadge(count: Int): String =
    pluralStringResource(Res.plurals.settings_actions_contacts_count, count, count)

internal fun captureDestinationLabel(
    type: DefaultCaptureType,
    noteProvider: IntegrationProvider,
    reminderProvider: IntegrationProvider,
): String = when (type) {
    DefaultCaptureType.Note -> noteProvider.notesTitle
    DefaultCaptureType.Reminder -> reminderProvider.remindersTitle
}

/** Actions that are switched off because they still need linking get a Connect button. */
internal fun actionConnectDialog(action: IndexAction): ActionsDialog? =
    ActionsDialog.PhoneCalendar.takeIf {
        action.name == CalendarServlet.NAME && action.disabledReason == NOT_CONNECTED_REASON
    }

/** Plugins connect from their own settings page; built-ins without a flow here fall back to the integration list. */
internal fun noteConnectDialog(provider: IntegrationProvider): ActionsDialog? = when (provider) {
    BuiltInIntegrationProviders.Notion -> ActionsDialog.Notion
    BuiltInIntegrationProviders.Obsidian -> ActionsDialog.Obsidian
    BuiltInIntegrationProviders.Tasker -> ActionsDialog.Tasker
    else -> null
}

internal fun reminderConnectDialog(provider: IntegrationProvider): ActionsDialog? = when (provider) {
    BuiltInIntegrationProviders.GoogleTasks -> ActionsDialog.GoogleTasks
    BuiltInIntegrationProviders.Tasker -> ActionsDialog.Tasker
    else -> null
}

/** Connected providers with their own settings reopen them from a hold on their sheet row. */
internal fun noteConfigureDialog(provider: IntegrationProvider): ActionsDialog? = when (provider) {
    BuiltInIntegrationProviders.Notion -> ActionsDialog.NotionPage
    BuiltInIntegrationProviders.Obsidian -> ActionsDialog.Obsidian
    else -> null
}

private data class ActionPresentation(val icon: ImageVector, val title: String, val example: String)

@Composable
private fun presentationFor(action: IndexAction): ActionPresentation = when (action.name) {
    NoteServlet.NAME -> ActionPresentation(
        Icons.Default.EditNote,
        stringResource(Res.string.settings_actions_notes),
        stringResource(Res.string.settings_actions_notes_example),
    )
    ReminderServlet.NAME -> ActionPresentation(
        Icons.Default.Checklist,
        stringResource(Res.string.settings_actions_reminders),
        stringResource(Res.string.settings_actions_reminders_example),
    )
    ListServlet.NAME -> ActionPresentation(
        Icons.AutoMirrored.Filled.FormatListBulleted,
        stringResource(Res.string.settings_actions_lists),
        stringResource(Res.string.settings_actions_lists_example),
    )
    CalendarServlet.NAME -> ActionPresentation(
        Icons.Default.CalendarMonth,
        stringResource(Res.string.settings_actions_calendar),
        stringResource(Res.string.settings_actions_calendar_example),
    )
    ClockServlet.name -> ActionPresentation(
        Icons.Default.Alarm,
        stringResource(Res.string.settings_actions_timers_alarms),
        stringResource(Res.string.settings_actions_timers_alarms_example),
    )
    MessagingServlet.name -> ActionPresentation(
        Icons.AutoMirrored.Filled.Chat,
        stringResource(Res.string.settings_actions_beeper_messaging),
        stringResource(Res.string.settings_actions_beeper_messaging_example),
    )
    NotificationReplyServlet.NAME -> ActionPresentation(
        Icons.AutoMirrored.Filled.Reply,
        stringResource(Res.string.settings_actions_notification_reply),
        stringResource(Res.string.settings_actions_notification_reply_example),
    )
    BugReportServlet.NAME -> ActionPresentation(
        Icons.Default.BugReport,
        stringResource(Res.string.settings_actions_bug_reports),
        stringResource(Res.string.settings_actions_bug_reports_example),
    )
    else -> ActionPresentation(Icons.Default.Power, action.title, "")
}

@Composable
private fun presentationFor(server: HttpMcpServerEntity) = ActionPresentation(
    Icons.Default.Dns,
    server.cachedTitle.ifBlank { server.name },
    stringResource(Res.string.settings_actions_mcp_server),
)

internal fun grantLabel(grant: PluginGrant): StringResource = when (grant) {
    PluginGrant.Actions -> Res.string.settings_actions_grant_actions
    PluginGrant.Sources -> Res.string.settings_actions_grant_sources
}

/** Collapsed-row summary, e.g. "2 actions · 1 data source". */
@Composable
internal fun pluginSummary(plugin: IndexPlugin): String =
    pluginGrantCounts(plugin).map { (plural, count) -> pluralStringResource(plural, count, count) }.joinToString(" · ")

internal fun pluginGrantCounts(plugin: IndexPlugin): List<Pair<PluralStringResource, Int>> =
    plugin.grants.entries.map { (grant, state) ->
        when (grant) {
            PluginGrant.Actions -> Res.plurals.settings_actions_plugin_actions_count
            PluginGrant.Sources -> Res.plurals.settings_actions_plugin_sources_count
        } to state.names.size
    }

/** The parent checkbox reflects every grant, allowed or not by the user. */
internal fun pluginToggleState(plugin: IndexPlugin): ToggleableState {
    val enabled = plugin.grants.values.map { it.enabled }
    return when {
        enabled.all { it } -> ToggleableState.On
        enabled.none { it } -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }
}

/**
 * Tapping the parent turns every changeable grant on unless all are already on, in which case
 * it turns them all off. Returns the grants that would change and their new value.
 */
internal fun pluginToggleChanges(plugin: IndexPlugin): List<Pair<PluginGrant, Boolean>> {
    val target = pluginToggleState(plugin) != ToggleableState.On
    return plugin.grants
        .filterValues { it.disabledReason == null && it.enabled != target }
        .keys
        .map { it to target }
}

private enum class ActionsSheet { CaptureType, NoteDestination, ReminderDestination, ListDestination }
internal enum class ActionsDialog {
    AddServer, Sideload, Notion, NotionPage, GoogleTasks, Obsidian, Tasker, PhoneCalendar,
    TargetCalendar, SavedPlaces,
}

@Composable
internal fun IndexActionsSection(
    agentReachable: Boolean,
    actions: List<IndexAction>,
    plugins: List<IndexPlugin>,
    httpMcpServers: List<HttpMcpServerEntity>,
    httpMcpDisabledReason: String?,
    captureType: DefaultCaptureType,
    noteProvider: IntegrationProvider,
    reminderProvider: IntegrationProvider,
    listProvider: IntegrationProvider,
    approvedContactCount: Int,
    noteDestinations: List<DestinationItem>,
    reminderDestinations: List<DestinationItem>,
    listDestinations: List<DestinationItem>,
    lockerPlugins: Set<Uuid>,
    onSetActionEnabled: (name: String, enabled: Boolean) -> Unit,
    onSetPluginGrantEnabled: (uuid: Uuid, grant: PluginGrant, enabled: Boolean) -> Unit,
    onSetHttpServerEnabled: (server: HttpMcpServerEntity, enabled: Boolean) -> Unit,
    onSaveHttpServer: (server: HttpMcpServerEntity) -> Unit,
    onDeleteHttpServer: (server: HttpMcpServerEntity) -> Unit,
    onSetCaptureType: (DefaultCaptureType) -> Unit,
    onSelectNoteProvider: (IntegrationProvider) -> Unit,
    onSelectReminderProvider: (IntegrationProvider) -> Unit,
    onSelectListProvider: (IntegrationProvider) -> Unit,
    onNoteConnectStarted: (providerId: String) -> Unit,
    onReminderConnectStarted: (providerId: String) -> Unit,
    onListConnectStarted: (providerId: String) -> Unit,
    onReviewInstall: suspend (StorePlugin) -> InstallReview?,
    onInstallStorePlugin: suspend (StorePlugin, granted: Map<String, Boolean?>) -> Boolean,
    onAddIntegration: () -> Unit,
    onConfigurePlugin: (uuid: Uuid, title: String) -> Unit,
    onOpenPluginPage: (uuid: Uuid) -> Unit,
    onOpenBeeperContacts: () -> Unit,
    /** A null id creates a new place. */
    onEditGeofenceLocation: (uuid: Uuid?) -> Unit,
    onGetMoreActions: () -> Unit,
    /** Null where the user can't browse plugins yet, e.g. onboarding; a hint is shown instead. */
    onGetMorePlugins: (() -> Unit)?,
    onProvidersChanged: () -> Unit,
    showHeader: Boolean = true,
) {
    if (!agentReachable) {
        NoIndexAgentCard()
        return
    }

    val colors = IndexTheme.colors

    var sheet by remember { mutableStateOf<ActionsSheet?>(null) }
    var dialog by remember { mutableStateOf<ActionsDialog?>(null) }
    var editingServer by remember { mutableStateOf<HttpMcpServerEntity?>(null) }
    val installer = rememberStorePluginInstaller(onReviewInstall, onInstallStorePlugin, onConfigurePlugin)
    val closeConnectDialog = {
        dialog = null
        onProvidersChanged()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (showHeader) {
            SectionHeader(
                title = stringResource(Res.string.settings_actions_header),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 2.dp),
            )
            Text(
                stringResource(Res.string.settings_actions_description),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            )
        }
        CaptureSentence(
            template = stringResource(captureSentence(captureType)),
            typeLabel = stringResource(captureTypeLabel(captureType)),
            destinationLabel = captureDestinationLabel(captureType, noteProvider, reminderProvider),
            onTypeClick = { sheet = ActionsSheet.CaptureType },
            onDestinationClick = {
                sheet = when (captureType) {
                    DefaultCaptureType.Note -> ActionsSheet.NoteDestination
                    DefaultCaptureType.Reminder -> ActionsSheet.ReminderDestination
                }
            },
        )
        actions.forEach { action ->
            ActionRow(
                enabled = action.enabled,
                disabledReason = action.disabledReason,
                presentation = presentationFor(action),
                badge = contactsBadge(approvedContactCount)
                    .takeIf { action.name == MessagingServlet.name && approvedContactCount > 0 },
                destinationLabel = when (action.name) {
                    NoteServlet.NAME -> noteProvider.notesTitle
                    ReminderServlet.NAME -> reminderProvider.remindersTitle
                    ListServlet.NAME -> listProvider.listsTitle
                    else -> null
                },
                onDestinationClick = {
                    sheet = when (action.name) {
                        NoteServlet.NAME -> ActionsSheet.NoteDestination
                        ListServlet.NAME -> ActionsSheet.ListDestination
                        else -> ActionsSheet.ReminderDestination
                    }
                },
                onSettings = when {
                    action.disabledReason != null -> null
                    action.name == MessagingServlet.name -> onOpenBeeperContacts
                    action.name == CalendarServlet.NAME -> {
                        { dialog = ActionsDialog.TargetCalendar }
                    }
                    action.name == ReminderServlet.NAME && reminderProvider.storesItemsLocally -> {
                        { dialog = ActionsDialog.SavedPlaces }
                    }
                    else -> null
                },
                onConnect = actionConnectDialog(action)?.let { target -> { dialog = target } },
                onToggle = { onSetActionEnabled(action.name, it) },
            )
        }
        plugins.forEach { plugin ->
            PluginRow(
                plugin = plugin,
                onConfigure = if (plugin.pluginUuid in lockerPlugins) {
                    { onOpenPluginPage(plugin.pluginUuid) }
                } else {
                    null
                },
                onSetGrantEnabled = { grant, enabled ->
                    onSetPluginGrantEnabled(plugin.pluginUuid, grant, enabled)
                },
            )
        }
        httpMcpServers.forEach { server ->
            ActionRow(
                enabled = server.enabled && httpMcpDisabledReason == null,
                disabledReason = httpMcpDisabledReason,
                presentation = presentationFor(server),
                badge = null,
                destinationLabel = null,
                onDestinationClick = {},
                onSettings = { editingServer = server },
                onConnect = null,
                onToggle = { onSetHttpServerEnabled(server, it) },
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        // The Actions store is future expansion and ships disabled.
        // GetMoreActionsRow(onClick = onGetMoreActions)
        if (onGetMorePlugins != null) {
            SectionActionRow(
                icon = Icons.Default.Power,
                title = stringResource(Res.string.settings_actions_add_plugin),
                subtitle = stringResource(Res.string.settings_actions_add_plugin_browse),
                enabled = true,
                onClick = onGetMorePlugins,
            )
        } else {
            SectionActionRow(
                icon = Icons.Default.Power,
                title = stringResource(Res.string.settings_actions_add_plugin),
                subtitle = stringResource(Res.string.settings_actions_add_plugin_later),
                enabled = false,
                onClick = {},
            )
        }
        SectionActionRow(
            icon = Icons.Default.Dns,
            title = stringResource(Res.string.settings_actions_add_mcp_server),
            subtitle = httpMcpDisabledReason ?: stringResource(Res.string.settings_actions_add_mcp_server_subtitle),
            enabled = httpMcpDisabledReason == null,
            onClick = { dialog = ActionsDialog.AddServer },
        )
        // Sideloading is future expansion and ships disabled.
        // SectionActionRow(
        //     icon = Icons.Default.UploadFile,
        //     title = "Sideload an action (.pbw)",
        //     subtitle = "Install a plugin you built or downloaded",
        //     enabled = true,
        //     onClick = { dialog = ActionsDialog.Sideload },
        // )
    }

    when (sheet) {
        ActionsSheet.CaptureType -> CaptureTypeSheet(
            current = captureType,
            onSelect = {
                onSetCaptureType(it)
                sheet = null
            },
            onDismiss = { sheet = null },
        )
        ActionsSheet.NoteDestination -> DestinationSheet(
            icon = Icons.Default.EditNote,
            title = stringResource(Res.string.settings_actions_where_notes_save),
            items = noteDestinations,
            hasDialog = { noteConfigureDialog(it) != null },
            onGetMore = onGetMorePlugins,
            selected = noteProvider,
            onSelect = onSelectNoteProvider,
            onConnect = { provider ->
                onNoteConnectStarted(provider.id)
                when (provider) {
                    is PblPluginIntegrationProvider -> onConfigurePlugin(provider.pluginUuid, provider.title)
                    else -> noteConnectDialog(provider)?.let { dialog = it } ?: onAddIntegration()
                }
            },
            onInstall = { plugin ->
                onNoteConnectStarted(PblPluginIntegrationProvider.idFor(plugin.pluginUuid))
                installer.install(plugin)
            },
            onConfigure = { provider ->
                when (provider) {
                    is PblPluginIntegrationProvider -> onOpenPluginPage(provider.pluginUuid)
                    else -> noteConfigureDialog(provider)?.let { dialog = it }
                }
            },
            onDismiss = { sheet = null },
        )
        // Lists are kept by the reminder providers, so both pick from and connect the same ones.
        ActionsSheet.ReminderDestination, ActionsSheet.ListDestination -> {
            val lists = sheet == ActionsSheet.ListDestination
            val onConnectStarted = if (lists) onListConnectStarted else onReminderConnectStarted
            DestinationSheet(
                icon = if (lists) Icons.AutoMirrored.Filled.FormatListBulleted else Icons.Default.Checklist,
                title = stringResource(
                    if (lists) Res.string.settings_actions_where_lists_save else Res.string.settings_actions_where_reminders_save
                ),
                items = if (lists) listDestinations else reminderDestinations,
                onGetMore = onGetMorePlugins,
                selected = if (lists) listProvider else reminderProvider,
                onSelect = if (lists) onSelectListProvider else onSelectReminderProvider,
                onConnect = { provider ->
                    onConnectStarted(provider.id)
                    when (provider) {
                        is PblPluginIntegrationProvider -> onConfigurePlugin(provider.pluginUuid, provider.title)
                        else -> reminderConnectDialog(provider)?.let { dialog = it } ?: onAddIntegration()
                    }
                },
                onInstall = { plugin ->
                    onConnectStarted(PblPluginIntegrationProvider.idFor(plugin.pluginUuid))
                    installer.install(plugin)
                },
                onConfigure = { provider ->
                    if (provider is PblPluginIntegrationProvider) onOpenPluginPage(provider.pluginUuid)
                },
                onDismiss = { sheet = null },
            )
        }
        null -> {}
    }

    installer.Dialogs()

    editingServer?.let { server ->
        HttpServerEditDialog(
            initialServer = server,
            onDismiss = { editingServer = null },
            onConfirm = {
                onSaveHttpServer(it)
                editingServer = null
            },
            onDelete = {
                onDeleteHttpServer(server)
                editingServer = null
            },
        )
    }

    when (dialog) {
        ActionsDialog.AddServer -> HttpServerEditDialog(
            initialServer = null,
            onDismiss = { dialog = null },
            onConfirm = { server ->
                onSaveHttpServer(server)
                dialog = null
            },
            onDelete = null,
        )
        ActionsDialog.Sideload -> SideloadDialog(onDismiss = { dialog = null })
        ActionsDialog.Notion -> NotionDialog(onDismiss = closeConnectDialog)
        ActionsDialog.NotionPage -> NotionPageDialog(onDismiss = { dialog = null })
        ActionsDialog.GoogleTasks -> GTasksDialog(onDismiss = closeConnectDialog)
        ActionsDialog.Obsidian -> ObsidianDialog(onDismiss = closeConnectDialog)
        ActionsDialog.Tasker -> TaskerDialog(onDismiss = closeConnectDialog)
        ActionsDialog.PhoneCalendar -> PhoneCalendarDialog(
            onDismiss = { dialog = null },
            onConnected = { dialog = ActionsDialog.TargetCalendar },
        )
        ActionsDialog.TargetCalendar -> TargetCalendarDialog(onDismiss = { dialog = null })
        ActionsDialog.SavedPlaces -> GeofenceLocationsListDialog(
            onAdd = {
                dialog = null
                onEditGeofenceLocation(null)
            },
            onEdit = { uuid ->
                dialog = null
                onEditGeofenceLocation(uuid)
            },
            onDismissRequest = { dialog = null },
        )
        null -> {}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptureSentence(
    template: String,
    typeLabel: String,
    destinationLabel: String,
    onTypeClick: () -> Unit,
    onDestinationClick: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        var start = 0
        for (match in Regex("%([12])\\\$s").findAll(template)) {
            template.substring(start, match.range.first).trim().takeIf { it.isNotEmpty() }?.let { SentenceText(it) }
            if (match.groupValues[1] == "1") {
                SentenceLink(typeLabel, onTypeClick)
            } else {
                SentenceLink(destinationLabel, onDestinationClick)
            }
            start = match.range.last + 1
        }
        template.substring(start).trim().takeIf { it.isNotEmpty() }?.let { SentenceText(it) }
    }
}

@Composable
private fun SentenceText(text: String) {
    Text(text, fontSize = 13.sp, color = IndexTheme.colors.onSurfaceVariant)
}

@Composable
private fun SentenceLink(label: String, onClick: () -> Unit) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.primary,
        )
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ActionRow(
    enabled: Boolean,
    disabledReason: String?,
    presentation: ActionPresentation,
    badge: String?,
    destinationLabel: String?,
    onDestinationClick: () -> Unit,
    onSettings: (() -> Unit)?,
    onConnect: (() -> Unit)?,
    onToggle: (Boolean) -> Unit,
) {
    val interactive = disabledReason == null
    AgentRow(
        icon = presentation.icon,
        title = presentation.title,
        summary = disabledReason ?: presentation.example,
        interactive = interactive,
        // A connectable row is actionable, so it stays legible rather than dimmed.
        dimmed = !interactive && onConnect == null,
        onClick = { onToggle(!enabled) },
        badge = badge,
        onSettings = onSettings,
        extra = destinationLabel?.let { label ->
            { DestinationChip(label, interactive, onDestinationClick) }
        },
        trailing = {
            if (onConnect != null) {
                TextButton(onClick = onConnect) { Text(stringResource(UtilRes.string.connect)) }
            } else {
                Checkbox(
                    checked = enabled,
                    enabled = interactive,
                    onCheckedChange = { onToggle(it) },
                )
            }
        },
    )
}

@Composable
private fun DestinationChip(label: String, interactive: Boolean, onClick: () -> Unit) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .padding(top = 5.dp)
            .border(
                1.dp,
                colors.outlineVariant,
                RoundedCornerShape(100.dp),
            )
            .clickable(enabled = interactive, onClick = onClick)
            .padding(start = 10.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(Res.string.settings_actions_saves_to),
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
        )
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.primary,
        )
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PluginRow(
    plugin: IndexPlugin,
    onConfigure: (() -> Unit)?,
    onSetGrantEnabled: (PluginGrant, Boolean) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    var expanded by remember(plugin.pluginUuid) { mutableStateOf(false) }
    val interactive = plugin.grants.values.any { it.disabledReason == null }
    val toggle = {
        pluginToggleChanges(plugin).forEach { (grant, enabled) -> onSetGrantEnabled(grant, enabled) }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        AgentRow(
            icon = Icons.Default.Power,
            title = plugin.title,
            summary = pluginSummary(plugin),
            interactive = interactive,
            //TODO: expand settings on click when we're ready
            onClick = toggle,
            onSettings = onConfigure,
            trailing = {
                TriStateCheckbox(
                    state = pluginToggleState(plugin),
                    enabled = interactive,
                    onClick = toggle,
                )
            },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                plugin.grants.forEach { (grant, state) ->
                    PluginGrantRow(
                        label = stringResource(grantLabel(grant)),
                        supporting = state.disabledReason ?: state.names.joinToString(", "),
                        checked = state.enabled,
                        interactive = state.disabledReason == null,
                        onToggle = { onSetGrantEnabled(grant, it) },
                    )
                }
                if (onConfigure != null) {
                    ConfigurePluginLink(onClick = onConfigure)
                }
            }
        }
    }
}

/** One row of the actions list: a built-in action, an HTTP MCP server, or a plugin. */
@Composable
private fun AgentRow(
    icon: ImageVector,
    title: String,
    summary: String,
    interactive: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
    badge: String? = null,
    onSettings: (() -> Unit)? = null,
    dimmed: Boolean = !interactive,
    extra: (@Composable () -> Unit)? = null,
) {
    val colors = IndexTheme.colors
    val contentAlpha = if (dimmed) 0.38f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = interactive, onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(24.dp).alpha(contentAlpha),
        )
        Column(modifier = Modifier.weight(1f).alpha(contentAlpha)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(title, fontSize = 16.sp, color = colors.onSurface)
                if (badge != null) {
                    Text(
                        badge,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .border(
                                1.dp,
                                colors.outlineVariant,
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                summary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = colors.onSurfaceVariant,
            )
            extra?.invoke()
        }
        if (onSettings != null) {
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(UtilRes.string.settings))
            }
        }
        trailing()
    }
}

@Composable
private fun ConfigurePluginLink(onClick: () -> Unit) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 56.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(Res.string.settings_actions_configure_plugin),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colors.primary,
        )
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PluginGrantRow(
    label: String,
    supporting: String,
    checked: Boolean,
    interactive: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = interactive) { onToggle(!checked) }
            .alpha(if (interactive) 1f else 0.38f)
            .padding(start = 56.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = colors.onSurface)
            Text(
                supporting,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row {
            ToggleButton(
                checked = !checked,
                enabled = interactive,
                onCheckedChange = { onToggle(false) },
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                colors = IndexTheme.toggleButtonColors()
            ) {
                if (!checked) {
                    Icon(
                        Icons.Default.Close,
                        null,
                        Modifier.size(ToggleButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                }
                Text(stringResource(Res.string.settings_actions_denied))
            }
            ToggleButton(
                checked = checked,
                enabled = interactive,
                onCheckedChange = { onToggle(true) },
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                colors = IndexTheme.toggleButtonColors()
            ) {
                if (checked) {
                    Icon(
                        Icons.Default.Check,
                        null,
                        Modifier.size(ToggleButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                }
                Text(stringResource(Res.string.settings_actions_allowed))
            }
        }
    }
}

@Composable
private fun SectionActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = colors.onSurface)
            Text(
                subtitle,
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun NoIndexAgentCard() {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .dashedRoundedBorder(colors.outlineVariant, 12.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            boldPlaceholder(
                Res.string.settings_actions_no_agent,
                stringResource(Res.string.settings_gesture_dest_index_agent),
            ),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = colors.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureTypeSheet(
    current: DefaultCaptureType,
    onSelect: (DefaultCaptureType) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IndexTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.sheetSurface) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                Text(
                    stringResource(Res.string.settings_actions_capture_sheet_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Text(
                    stringResource(Res.string.settings_actions_capture_sheet_subtitle),
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            DefaultCaptureType.entries.forEach { type ->
                SheetOptionRow(
                    icon = when (type) {
                        DefaultCaptureType.Note -> Icons.Default.EditNote
                        DefaultCaptureType.Reminder -> Icons.Default.Checklist
                    },
                    title = { Text(stringResource(captureTypeTitle(type))) },
                    subtitle = when (type) {
                        DefaultCaptureType.Note -> stringResource(Res.string.settings_actions_capture_note_subtitle)
                        DefaultCaptureType.Reminder -> stringResource(Res.string.settings_actions_capture_reminder_subtitle)
                    },
                    selected = type == current,
                    onClick = { onSelect(type) },
                )
            }
        }
    }
}

@Composable
private fun SheetOptionRow(
    title: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .selectedSheetRowBackground(selected)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) colors.primary
                else colors.outline,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            withCompositionLocal(
                LocalTextStyle provides LocalTextStyle.current
                    .copy(
                        fontSize = 15.sp,
                        color = colors.onSurface
                    )
            ) {
                title()
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        trailing()
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DestOptionTitle(title: String, plugin: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title)
        Spacer(Modifier.width(4.dp))
        if (plugin) {
            Icon(
                Icons.Default.Power,
                contentDescription = stringResource(Res.string.settings_actions_plugin),
                modifier = Modifier.size(18.dp),
                tint = IndexTheme.colors.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationSheet(
    icon: ImageVector,
    title: String,
    items: List<DestinationItem>,
    onGetMore: (() -> Unit)?,
    selected: IntegrationProvider,
    onSelect: (IntegrationProvider) -> Unit,
    onConnect: (IntegrationProvider) -> Unit,
    onInstall: (StorePlugin) -> Unit,
    onDismiss: () -> Unit,
    onConfigure: (IntegrationProvider) -> Unit = {},
    /** Built-in providers whose settings open in a dialog rather than a locker page. */
    hasDialog: (IntegrationProvider) -> Boolean = { false },
) {
    val colors = IndexTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheetSurface,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
            }
            items.forEach { item ->
                val installed = item as? DestinationItem.Installed
                val configurable = item.isConfigurable(hasDialog)
                val connect = {
                    onDismiss()
                    when (item) {
                        is DestinationItem.Installed -> onConnect(item.provider)
                        is DestinationItem.InStore -> onInstall(item.plugin)
                    }
                }
                SheetOptionRow(
                    title = { DestOptionTitle(item.title, plugin = item.isPlugin) },
                    subtitle = item.rowSubtitle(configurable)?.let { stringResource(it) },
                    onLongClick = installed?.takeIf { configurable }?.let { { onDismiss(); onConfigure(it.provider) } },
                    selected = installed?.provider == selected,
                    onClick = {
                        if (installed != null && installed.connected) {
                            onSelect(installed.provider)
                            onDismiss()
                        } else {
                            connect()
                        }
                    },
                    trailing = {
                        if (!item.isConnected) {
                            TextButton(onClick = connect) { Text(stringResource(UtilRes.string.connect)) }
                        }
                    },
                )
            }
            if (onGetMore != null) {
                TextButton(
                    onClick = {
                        onDismiss()
                        onGetMore()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(Res.string.settings_actions_get_more))
                }
            } else {
                AddMoreLaterHint(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun AddMoreLaterHint(modifier: Modifier = Modifier) {
    Text(
        stringResource(Res.string.settings_actions_add_more_later),
        fontSize = 13.sp,
        color = IndexTheme.colors.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun SideloadDialog(onDismiss: () -> Unit) {
    val colors = IndexTheme.colors
    var picked by remember { mutableStateOf(false) }
    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text("Sideload an action") },
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(UtilRes.string.cancel)) }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Pick a .pbw that includes MCP tools. It runs in a JS sandbox and shows up in " +
                    "your Actions list, unchecked until you enable it.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = colors.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .dashedRoundedBorder(colors.outline, 12.dp)
                    .clickable { picked = true }
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.UploadFile,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    "Choose a .pbw file",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.primary,
                )
            }
            if (picked) {
                Text(
                    "Sideloading isn't available yet.",
                    fontSize = 13.sp,
                    color = colors.primary,
                )
            }
        }
    }
}

/** [format]'s single `%1$s` placeholder, filled with [bold] in bold. */
@Composable
internal fun boldPlaceholder(format: StringResource, bold: String): AnnotatedString {
    val text = stringResource(format, bold)
    val start = text.indexOf(bold)
    return buildAnnotatedString {
        append(text)
        if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + bold.length)
    }
}

@Composable
internal fun Modifier.selectedSheetRowBackground(selected: Boolean): Modifier = then(
    if (selected) {
        Modifier.background(IndexTheme.colors.redSurface, RoundedCornerShape(12.dp))
    } else {
        Modifier
    }
)

private fun Modifier.dashedRoundedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        ),
        cornerRadius = CornerRadius(radius.toPx()),
    )
}

@Composable
private fun GetMoreActionsRow(onClick: () -> Unit) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Default.Storefront,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            "Get more actions",
            fontSize = 16.sp,
            color = colors.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
