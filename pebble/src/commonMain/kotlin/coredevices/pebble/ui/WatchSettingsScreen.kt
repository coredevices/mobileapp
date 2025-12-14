package coredevices.pebble.ui

import AppUpdateTracker
import CommonRoutes
import CoreAppVersion
import CoreRoute
import NextBugReportContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppSettingsAlt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import co.touchlab.kermit.Logger
import com.cactus.CactusSTT
import com.cactus.TranscriptionProvider
import com.cactus.VoiceModel
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.settings
import coredevices.CoreBackgroundSync
import coredevices.EnableExperimentalDevices
import coredevices.analytics.AnalyticsBackend
import coredevices.analytics.setUser
import coredevices.coreapp.util.AppUpdate
import coredevices.coreapp.util.AppUpdateState
import coredevices.pebble.PebbleFeatures
import coredevices.pebble.Platform
import coredevices.pebble.account.BootConfigProvider
import coredevices.pebble.account.FirestoreLocker
import coredevices.pebble.account.PebbleAccount
import coredevices.pebble.rememberLibPebble
import coredevices.pebble.services.RealPebbleWebServices
import coredevices.pebble.ui.SettingsKeys.KEY_ENABLE_FIREBASE_UPLOADS
import coredevices.pebble.ui.SettingsKeys.KEY_ENABLE_MEMFAULT_UPLOADS
import coredevices.pebble.ui.SettingsKeys.KEY_ENABLE_MIXPANEL_UPLOADS
import coredevices.pebble.weather.WeatherFetcher
import coredevices.ui.M3Dialog
import coredevices.ui.ModelDownloadDialog
import coredevices.ui.ModelType
import coredevices.ui.PebbleElevatedButton
import coredevices.util.CactusSTTMode
import coredevices.util.CommonBuildKonfig
import coredevices.util.CompanionDevice
import coredevices.util.CoreConfigFlow
import coredevices.util.CoreConfigHolder
import coredevices.util.PermissionRequester
import coredevices.util.deleteRecursive
import coredevices.util.getModelDirectories
import coredevices.util.rememberUiContext
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.crashlytics.crashlytics
import io.ktor.http.parseUrl
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.KnownPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.health.HealthSettings
import io.rebble.libpebblecommon.js.PKJSApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import theme.CoreAppTheme
import theme.ThemeProvider

enum class Section(val title: String) {
    Permissions("Permissions"),
    Updates("Updates"),
    Support("Support"),
    Default("Settings"),
    Health("Health"),
    Apps("Apps"),
    Weather("Weather"),
    Notifications("Notifications"),
    Watch("Watch"),
    Logging("Logging"),
    Analytics("Analytics"),
    Debug("Debug"),
}

data class SettingsItem(
    val title: String,
    val section: Section,
    val keywords: String = "",
    val show: () -> Boolean = { true },
    val item: @Composable () -> Unit,
)

private val ELEVATION = 2.dp

@Composable
fun settingsBadgeTotal(): Int {
    val permissionRequester: PermissionRequester = koinInject()
    val missingPermissions by permissionRequester.missingPermissions.collectAsState()
    val appUpdate: AppUpdate = koinInject()
    val updateState by appUpdate.updateAvailable.collectAsState()
    val updatesAvailable = when (updateState) {
        AppUpdateState.NoUpdateAvailable -> 0
        is AppUpdateState.UpdateAvailable -> 1
    }
    val appUpdateTracker: AppUpdateTracker = koinInject()
    val appWasUpdated by appUpdateTracker.appWasUpdated.collectAsState()
    val appUpdated = when (appWasUpdated) {
        true -> 1
        false -> 0
    }
    return missingPermissions.size + updatesAvailable + appUpdated
}

fun pebbleScreenContext(
    libPebble: LibPebble,
    coreConfigFlow: CoreConfigFlow,
    permissionRequester: PermissionRequester,
    companionDevice: CompanionDevice,
): String {
    val watches = libPebble.watches.value.sortedByDescending {
        when (it) {
            is KnownPebbleDevice -> it.lastConnected.epochSeconds
            else -> 0
        }
    }
    val lastConnectedWatch = watches.firstOrNull() as? KnownPebbleDevice
    val watchesWithoutCompanionDevicePermission = watches.mapNotNull {
        if (it is KnownPebbleDevice && !companionDevice.hasApprovedDevice(it.identifier)) {
            "[${it.identifier} / ${it.name}]"
        } else {
            null
        }
    }
    return buildString {
        appendLine("lastConnectedFirmwareVersion: ${lastConnectedWatch?.runningFwVersion}")
        appendLine("lastConnectedSerial: ${lastConnectedWatch?.serial}")
        appendLine("lastConnectedWatchType: ${lastConnectedWatch?.watchType}")
        appendLine("otherPebbleApps: ${libPebble.otherPebbleCompanionAppsInstalled().value}")
        appendLine("libPebbleConfig: ${libPebble.config.value}")
        appendLine("coreConfig: ${coreConfigFlow.value}")
        appendLine("missingPermissions: ${permissionRequester.missingPermissions.value}")
        appendLine("watchesWithoutCompanionDevicePermission: $watchesWithoutCompanionDevicePermission")
        appendLine(libPebble.watchesDebugState())
        appendLine("Watches (most recently connected first):")
        watches.forEachIndexed { index, watch ->
            appendLine("watch_$index $watch")
        }
    }
}

private val logger = Logger.withTag("WatchSettingsScreen")

sealed interface RequestedSTTMode {
    val mode: CactusSTTMode

    object Disabled : RequestedSTTMode {
        override val mode = CactusSTTMode.Disabled
    }

    data class Enabled(
        override val mode: CactusSTTMode,
        val modelName: String
    ) : RequestedSTTMode
}

@Composable
fun WatchSettingsScreen(navBarNav: NavBarNav, topBarParams: TopBarParams, experimentalRoute: CoreRoute?) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val libPebble = rememberLibPebble()
        val libPebbleConfig by libPebble.config.collectAsState()
        val coreConfigHolder: CoreConfigHolder = koinInject()
        val coreConfig by coreConfigHolder.config.collectAsState()
        val themeProvider: ThemeProvider = koinInject()
        val settings: Settings = koinInject()
        val currentTheme by themeProvider.theme.collectAsState()
        val pebbleFeatures = koinInject<PebbleFeatures>()
        val pebbleAccount = koinInject<PebbleAccount>()
        val bootConfig = koinInject<BootConfigProvider>()
        val loggedIn by pebbleAccount.loggedIn.collectAsState()
        val user by Firebase.auth.authStateChanged.map {
            it?.email
        }.distinctUntilChanged()
            .collectAsState(Firebase.auth.currentUser)
        val firestoreLocker = koinInject<FirestoreLocker>()
        val scope = rememberCoroutineScope()
        val appContext = koinInject<AppContext>()
        val appVersion = koinInject<CoreAppVersion>()
        val platform = koinInject<Platform>()
        val nextBugReportContext: NextBugReportContext = koinInject()
        val title = stringResource(Res.string.settings)
        val appUpdate: AppUpdate = koinInject()
        val updateState by appUpdate.updateAvailable.collectAsState()
        val (showCopyTokenDialog, setShowCopyTokenDialog) = remember { mutableStateOf(false) }
        val coreBackgroundSync: CoreBackgroundSync = koinInject()
        if (showCopyTokenDialog) {
            PKJSCopyTokenDialog(onDismissRequest = { setShowCopyTokenDialog(false) })
        }
        var showBtClassicInfoDialog by remember { mutableStateOf(false) }
        var showLockerImportDialog by remember { mutableStateOf(false) }
        var debugOptionsEnabled by remember { mutableStateOf(settings.showDebugOptions()) }
        var speechRecognitionEnabled by mutableStateOf(
            CactusSTTMode.fromId(settings.getInt(SettingsKeys.KEY_CACTUS_MODE, 0))
        )
        var showSpeechRecognitionModelDialog by remember { mutableStateOf<RequestedSTTMode?>(null) }
        var showSpeechRecognitionModeDialog by remember { mutableStateOf(false) }
        if (showSpeechRecognitionModelDialog != null) {
            check(showSpeechRecognitionModelDialog is RequestedSTTMode.Enabled)
            val modelName =
                (showSpeechRecognitionModelDialog!! as RequestedSTTMode.Enabled).modelName
            ModelDownloadDialog(
                onDismissRequest = { success ->
                    if (success) {
                        settings[SettingsKeys.KEY_CACTUS_MODE] =
                            showSpeechRecognitionModelDialog!!.mode.id
                        if (showSpeechRecognitionModelDialog is RequestedSTTMode.Enabled) {
                            settings[SettingsKeys.KEY_CACTUS_STT_MODEL] = modelName
                        }
                        speechRecognitionEnabled = showSpeechRecognitionModelDialog!!.mode
                    }
                    showSpeechRecognitionModelDialog = null
                },
                models = setOf(ModelType.STT(modelName = modelName))
            )
        }
        if (showSpeechRecognitionModeDialog) {
            val mode = CactusSTTMode.fromId(settings.getInt(SettingsKeys.KEY_CACTUS_MODE, 0))
            val model = settings.getString(
                SettingsKeys.KEY_CACTUS_STT_MODEL,
                CommonBuildKonfig.CACTUS_DEFAULT_STT_MODEL
            )
            STTModeDialog(
                onModeSelected = {
                    showSpeechRecognitionModeDialog = false
                    if (it != RequestedSTTMode.Disabled) {
                        showSpeechRecognitionModelDialog = it
                    } else {
                        speechRecognitionEnabled = it.mode
                        settings[SettingsKeys.KEY_CACTUS_MODE] = it.mode
                    }
                },
                onDismissRequest = { showSpeechRecognitionModeDialog = false },
                selectedMode = when (mode) {
                    CactusSTTMode.Disabled -> RequestedSTTMode.Disabled
                    else -> RequestedSTTMode.Enabled(mode, model)
                },
                debugOptionsEnabled
            )
        }
        if (showBtClassicInfoDialog) {
            M3Dialog(
                onDismissRequest = { showBtClassicInfoDialog = false },
                title = { Text("Prefer BT Classic") },
                buttons = {
                    OutlinedButton(
                        onClick = { showBtClassicInfoDialog = false }
                    ) {
                        Text("Ok")
                    }
                },
            ) {
                Box(Modifier.heightIn(max = 400.dp)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = """When enabled, the app will first connect (and pair) to your watch over
BLE, then disconnect and reconnect using Bluetooth Classic - this will
prompt your to pair your watch again (BLE and BT Classic use separate
 pairings).

If you are already connected over BLE, you will need to disconnect/reconnect
for this to take effect.

The watch will show "No LE" on the bluetooth settings screen once 
connected over BT Classic while BLE is also paired (and will show "LE only"
if you choose to go back to BLE while classic is still paired).

This should improve connection reliability, but if it does not work,
please disable the option.""".trimIndent(),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        if (showLockerImportDialog) {
            val isRebble = remember {
                parseUrl(bootConfig.getUrl() ?: "")?.host?.endsWith("rebble.io") == true
            }
            val webServices = koinInject<RealPebbleWebServices>()
            LockerImportDialog(
                onDismissRequest = { showLockerImportDialog = false },
                onImportFromPebbleAccount = { progressUpdate ->
                    try {
                        firestoreLocker.importPebbleLocker(webServices, "https://appstore-api.rebble.io/api").collect {
                            progressUpdate(it.first.toFloat() / it.second.toFloat())
                        }
                        progressUpdate(-1f)
                        libPebble.requestLockerSync().await()
                        coreConfigHolder.update(
                            coreConfig.copy(
                                useNativeAppStore = true
                            )
                        )
                    } catch (e: Exception) {
                        logger.e(e) { "Error importing locker from pebble account" }
                    }
                    showLockerImportDialog = false
                },
                onStartFresh = {
                    coreConfigHolder.update(
                        coreConfig.copy(
                            useNativeAppStore = true
                        )
                    )
                    showLockerImportDialog = false
                },
                isRebble = isRebble
            )
        }

        LaunchedEffect(Unit) {
            topBarParams.searchAvailable(true)
            topBarParams.actions {
            }
            topBarParams.title(title)
            topBarParams.canGoBack(false)
        }
        var themeDropdownExpanded by remember { mutableStateOf(false) }
        val permissionRequester: PermissionRequester = koinInject()
        val missingPermissions by permissionRequester.missingPermissions.collectAsState()
        val uiContext = rememberUiContext()
        val analyticsBackend: AnalyticsBackend = koinInject()
        val enableFirebase = mutableStateOf(settings.getBoolean(KEY_ENABLE_FIREBASE_UPLOADS, true))
        val enableMemfault = mutableStateOf(settings.getBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true))
        val enableMixpanel = mutableStateOf(settings.getBoolean(KEY_ENABLE_MIXPANEL_UPLOADS, true))
        val companionDevice: CompanionDevice = koinInject()
        val coreConfigFlow: CoreConfigFlow = koinInject()
        val enableExperimentalDevices: EnableExperimentalDevices = koinInject()
        val experimentalDevices by enableExperimentalDevices.enabled.collectAsState()
        val appUpdateTracker: AppUpdateTracker = koinInject()
        val showChangelogBadge = remember { appUpdateTracker.appWasUpdated.value }
        LaunchedEffect(Unit) {
            appUpdateTracker.acknowledgeCurrentVersion()
        }
        val healthSettings by libPebble.healthSettings.collectAsState(HealthSettings())
        val weatherFetcher: WeatherFetcher = koinInject()

        val settingsItems = remember(
            libPebbleConfig,
            debugOptionsEnabled,
            missingPermissions,
            updateState,
            enableFirebase,
            enableMemfault,
            enableMixpanel,
            coreConfig,
            experimentalDevices
        ) {
            listOf(
                basicSettingsActionItem(
                    title = "App Update Available",
                    description = "Please update the Pebble App!",
                    section = Section.Updates,
                    action = {
                        val update = updateState as? AppUpdateState.UpdateAvailable
                        if (uiContext != null && update != null) {
                            appUpdate.startUpdateFlow(uiContext, update.update)
                        }
                    },
                    badge = when (updateState) {
                        AppUpdateState.NoUpdateAvailable -> null
                        is AppUpdateState.UpdateAvailable -> "1"
                    },
                    show = { updateState is AppUpdateState.UpdateAvailable },
                ),
                basicSettingsActionItem(
                    title = "Permissions",
                    description = if (missingPermissions.isEmpty()) {
                        "All permissions granted!"
                    } else if (missingPermissions.size == 1) {
                        "${missingPermissions.first()} permission missing!"
                    } else {
                        "${missingPermissions.size} permissions missing!"
                    },
                    section = Section.Permissions,
                    action = if (missingPermissions.isNotEmpty()) {
                        {
                            navBarNav.navigateTo(PebbleNavBarRoutes.PermissionsRoute)
                        }
                    } else null,
                    badge = if (missingPermissions.isEmpty()) null else "${missingPermissions.size}",
                ),
                SettingsItem(
                    title = "App Version",
                    section = Section.Updates,
                    item = {
                        ListItem(
                            headlineContent = {
                                Text("App Version: ${appVersion.version}")
                            },
                            shadowElevation = ELEVATION,
                        )
                    },
                ),
                basicSettingsActionItem(
                    title = "What's new in the app",
                    section = Section.Updates,
                    action = {
                        navBarNav.navigateTo(CommonRoutes.RoadmapChangelogRoute)
                    },
                    badge = if (showChangelogBadge) "1" else null
                ),
                basicSettingsActionItem(
                    title = "What’s new in PebbleOS",
                    section = Section.Updates,
                    action = {
                        navBarNav.navigateTo(CommonRoutes.PebbleOsChangelogRoute)
                    },
                ),
                basicSettingsActionItem(
                    title = "Getting Started & Troubleshooting",
                    section = Section.Support,
                    action = {
                        navBarNav.navigateTo(CommonRoutes.TroubleshootingRoute)
                    },
                ),
                basicSettingsActionItem(
                    title = "New Bug Report",
                    description = "Please report a bug if anything went wrong!",
                    section = Section.Support,
                    action = {
                        nextBugReportContext.nextContext =
                            pebbleScreenContext(
                                libPebble,
                                coreConfigFlow,
                                permissionRequester,
                                companionDevice
                            )
                        navBarNav.navigateTo(
                            CommonRoutes.BugReport(
                                pebble = true,
                            )
                        )
                    },
                ),
                basicSettingsActionItem(
                    title = "View My Bug Reports",
                    section = Section.Support,
                    action = {
                        navBarNav.navigateTo(CommonRoutes.ViewMyBugReportsRoute)
                    },
                ),
                basicSettingsActionItem(
                    title = "Configure Appstore Sources",
                    section = Section.Default,
                    action = { navBarNav.navigateTo(PebbleRoutes.AppstoreSettingsRoute) },
                    show = { coreConfig.useNativeAppStore },
                ),
                basicSettingsActionItem(
                    title = "Experimental Feed",
                    section = Section.Default,
                    action = { navBarNav.navigateTo(experimentalRoute!!) },
                    show = { experimentalRoute != null },
                ),
                SettingsItem(
                    title = TITLE_APP_THEME,
                    section = Section.Default,
                    keywords = "dark light system",
                    item = {
                        ListItem(
                            leadingContent = {
                                Text(TITLE_APP_THEME)
                            },
                            headlineContent = {
                                ExposedDropdownMenuBox(
                                    expanded = themeDropdownExpanded,
                                    onExpandedChange = {
                                        themeDropdownExpanded = !themeDropdownExpanded
                                    }
                                ) {
                                    TextField(
                                        value = stringResource(currentTheme.resource),
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeDropdownExpanded)
                                        },
                                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    )

                                    ExposedDropdownMenu(
                                        expanded = themeDropdownExpanded,
                                        onDismissRequest = { themeDropdownExpanded = false }
                                    ) {
                                        CoreAppTheme.entries.forEach { appTheme ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(appTheme.resource)) },
                                                onClick = {
                                                    themeProvider.setTheme(appTheme)
                                                    themeDropdownExpanded = false
                                                },
                                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.clickable { themeDropdownExpanded = true },
                            shadowElevation = ELEVATION,
                        )
                    },
                ),
                basicSettingsToggleItem(
                    title = "Dump notifications to logs",
                    description = "Detailed notification logging, to diagnose processing/deduplication issues",
                    section = Section.Logging,
                    checked = libPebbleConfig.notificationConfig.dumpNotificationContent,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    dumpNotificationContent = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsNotificationLogging() },
                ),
                basicSettingsToggleItem(
                    title = "Obfuscate sensitive content in logs",
                    description = "Remove any personal information (notification content, calendar events, app names, etc) from logs before they are written",
                    section = Section.Logging,
                    checked = libPebbleConfig.notificationConfig.obfuscateContent,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    obfuscateContent = it
                                )
                            )
                        )
                    },
                ),
                basicSettingsToggleItem(
                    title = "Always send notifications",
                    description = "Send notifications to the watch even when the phone screen is on",
                    section = Section.Notifications,
                    checked = libPebbleConfig.notificationConfig.alwaysSendNotifications,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    alwaysSendNotifications = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsNotificationFiltering() },
                ),
                basicSettingsToggleItem(
                    title = "Respect Phone Do Not Disturb",
                    description = "Notifications won't be sent to watch if phone is in Do Not Disturb mode (unless configured for that app/person in phone settings)",
                    section = Section.Notifications,
                    checked = libPebbleConfig.notificationConfig.respectDoNotDisturb,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    respectDoNotDisturb = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsNotificationFiltering() },
                ),
                SettingsItem(
                    title = "Vibration Pattern",
                    section = Section.Notifications,
                    show = { pebbleFeatures.supportsVibePatterns() },
                    item = {
                        SelectVibePatternOrNone(
                            currentPattern = libPebbleConfig.notificationConfig.overrideDefaultVibePattern,
                            onChangePattern = { pattern ->
                                libPebble.updateConfig(
                                    libPebbleConfig.copy(
                                        notificationConfig = libPebbleConfig.notificationConfig.copy(
                                            overrideDefaultVibePattern = pattern?.name
                                        )
                                    )
                                )
                            },
                            subtext = "Override the default on the watch",
                        )
                    }
                ),
                basicSettingsToggleItem(
                    title = "Use vibration patterns from OS",
                    description = "If there is a vibration pattern defined by the app which created a notification, use it on the watch (unless overridden)",
                    section = Section.Notifications,
                    checked = libPebbleConfig.notificationConfig.useAndroidVibePatterns,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    useAndroidVibePatterns = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsVibePatterns() },
                ),
                basicSettingsToggleItem(
                    title = "Send local-only notifications to watch",
                    description = "Android recommends not forwarding notifications marked as local-only to external devices - check to override this",
                    section = Section.Notifications,
                    checked = libPebbleConfig.notificationConfig.sendLocalOnlyNotifications,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    sendLocalOnlyNotifications = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsNotificationFiltering() },
                ),
                basicSettingsToggleItem(
                    title = "Enable showsUserInterface actions",
                    description = "Include notification actions which are marked as opening a user interface on the phone",
                    section = Section.Notifications,
                    checked = libPebbleConfig.notificationConfig.addShowsUserInterfaceActions,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                notificationConfig = libPebbleConfig.notificationConfig.copy(
                                    addShowsUserInterfaceActions = it
                                )
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsNotificationFiltering() },
                ),
                basicSettingsToggleItem(
                    title = "Prefer BT Classic",
                    description = "Connect using Bluetooth Classic to watches which support it (Pebble, Pebble Steel, Pebble Time/Steel/Round). This may improve connection reliability, but is currently experimental.",
                    section = Section.Watch,
                    checked = libPebbleConfig.watchConfig.preferBtClassicV2,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    preferBtClassicV2 = it
                                )
                            )
                        )
                        if (it) {
                            showBtClassicInfoDialog = true
                        }
                    },
                    show = { pebbleFeatures.supportsBtClassic() },
                ),
                basicSettingsToggleItem(
                    title = "Disable Companion Device Manager",
                    description = "Don't use Android's Companion Device Manager to connect. Only use this option if the app crashes every time you press 'connect' and you cannot get past this step. This will disable certain features (including Notification Channels), and certain permissions will need to be granted manually.",
                    section = Section.Watch,
                    checked = coreConfig.disableCompanionDeviceManager,
                    onCheckChanged = {
                        coreConfigHolder.update(
                            coreConfig.copy(
                                disableCompanionDeviceManager = it,
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsCompanionDeviceManager() },
                ),

                basicSettingsToggleItem(
                    title = "Ignore Missing PRF",
                    description = "Ignore missing PRF when connecting to development watches",
                    section = Section.Watch,
                    checked = libPebbleConfig.watchConfig.ignoreMissingPrf,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    ignoreMissingPrf = it
                                )
                            )
                        )
                    },
                    show = { debugOptionsEnabled },
                ),
                basicSettingsToggleItem(
                    title = "Use reversed PPoG",
                    section = Section.Default,
                    checked = libPebbleConfig.bleConfig.reversedPPoG,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                bleConfig = libPebbleConfig.bleConfig.copy(
                                    reversedPPoG = it
                                )
                            )
                        )
                    },
                    show = { false },
                ),
                basicSettingsToggleItem(
                    title = "Enable Health",
                    section = Section.Health,
                    checked = healthSettings.trackingEnabled,
                    onCheckChanged = {
                        libPebble.updateHealthSettings(
                            healthSettings.copy(
                                trackingEnabled = it
                            )
                        )
                    },
                ),
                basicSettingsToggleItem(
                    title = "Activity Insights",
                    section = Section.Health,
                    checked = healthSettings.activityInsightsEnabled,
                    onCheckChanged = {
                        libPebble.updateHealthSettings(
                            healthSettings.copy(
                                activityInsightsEnabled = it
                            )
                        )
                    },
                ),
                basicSettingsToggleItem(
                    title = "Sleep Insights",
                    section = Section.Health,
                    checked = healthSettings.sleepInsightsEnabled,
                    onCheckChanged = {
                        libPebble.updateHealthSettings(
                            healthSettings.copy(
                                sleepInsightsEnabled = it
                            )
                        )
                    },
                ),
                basicSettingsToggleItem(
                    title = "Weather Pins",
                    section = Section.Weather,
                    checked = coreConfig.weatherPins,
                    onCheckChanged = {
                        coreConfigHolder.update(
                            coreConfig.copy(
                                weatherPins = it,
                            )
                        )
                        GlobalScope.launch { weatherFetcher.fetchWeather() }
                    },
                    show = { experimentalDevices },
                ),
                basicSettingsToggleItem(
                    title = "Use LAN developer connection",
                    description = "Allow connecting to developer connection over LAN, this is not secure and should only be used on trusted networks",
                    section = Section.Default,
                    checked = libPebbleConfig.watchConfig.lanDevConnection,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    lanDevConnection = it
                                )
                            )
                        )
                    },
                ),
                basicSettingsToggleItem(
                    title = "PKJS Debugger",
                    description = "Allow connection via the ${if (platform == Platform.Android) "Chrome" else "Safari"} remote inspector to debug PKJS apps. Restart watchapp after changing.",
                    section = Section.Debug,
                    checked = libPebbleConfig.watchConfig.pkjsInspectable,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    pkjsInspectable = it
                                )
                            )
                        )
                    },
                    show = { debugOptionsEnabled },
                ),
                basicSettingsToggleItem(
                    title = "Enable speech recognition (preview)",
                    description = "Enable text replies/input via the watch microphone, using a local model. Requires a download",
                    section = Section.Default,
                    checked = speechRecognitionEnabled != CactusSTTMode.Disabled,
                    onCheckChanged = {
                        if (it) {
                            showSpeechRecognitionModeDialog = true
                        } else {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    getModelDirectories().forEach {
                                        deleteRecursive(Path(it))
                                    }
                                }
                                settings[SettingsKeys.KEY_CACTUS_MODE] = CactusSTTMode.Disabled.id
                                speechRecognitionEnabled = CactusSTTMode.Disabled
                            }
                        }
                    },
                ),
                basicSettingsToggleItem(
                    title = "Use Native App Store",
                    description = "Preview",
                    section = Section.Default,
                    checked = coreConfig.useNativeAppStore,
                    onCheckChanged = {
                        if (!coreConfig.useNativeAppStore) {
                            scope.launch {
                                if (firestoreLocker.isLockerEmpty() && loggedIn != null) {
                                    logger.i { "Showing locker import dialog" }
                                    showLockerImportDialog = true
                                } else {
                                    logger.i { "Skipping locker import dialog" }
                                    coreConfigHolder.update(
                                        coreConfig.copy(
                                            useNativeAppStore = true,
                                        )
                                    )
                                    libPebble.requestLockerSync()
                                    topBarParams.showSnackbar("Please wait while your locker syncs in the background")
                                }
                            }
                        } else {
                            coreConfigHolder.update(
                                coreConfig.copy(
                                    useNativeAppStore = false,
                                )
                            )
                            libPebble.requestLockerSync()
                            topBarParams.showSnackbar("Please wait while your locker syncs in the background")
                        }
                    },
                    show = { experimentalDevices },
                ),
                basicSettingsToggleItem(
                    title = "Ignore other Pebble apps",
                    description = "Allow connection even when there are other Pebble apps installed on this phone. Warning: this will likely make the connection unreliable if you are using BLE! We don't recommend enabling this",
                    section = Section.Default,
                    checked = coreConfig.ignoreOtherPebbleApps,
                    onCheckChanged = {
                        coreConfigHolder.update(
                            coreConfig.copy(
                                ignoreOtherPebbleApps = !coreConfig.ignoreOtherPebbleApps,
                            )
                        )
                    },
                    show = { pebbleFeatures.supportsDetectingOtherPebbleApps() },
                ),
                basicSettingsToggleItem(
                    title = "Verbose connection logging",
                    description = "Detailed connectivity state machine logging (please don't enable this unless we ask you to)",
                    section = Section.Logging,
                    checked = libPebbleConfig.watchConfig.verboseWatchManagerLogging,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    verboseWatchManagerLogging = it
                                )
                            )
                        )
                    },
                    show = { debugOptionsEnabled },
                ),
                basicSettingsToggleItem(
                    title = "Verbose PPoG logging",
                    description = "Detailed Pebble Protocol over GATT logging (please don't enable this unless we ask you to)",
                    section = Section.Logging,
                    checked = libPebbleConfig.bleConfig.verbosePpogLogging,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                bleConfig = libPebbleConfig.bleConfig.copy(
                                    verbosePpogLogging = it
                                )
                            )
                        )
                    },
                    show = { debugOptionsEnabled },
                ),
                basicSettingsToggleItem(
                    title = "Collect app crashes",
                    description = "This allows us to fix crashes in the mobile app - otherwise we don't know how often they are happening, or how to fix them",
                    section = Section.Analytics,
                    checked = enableFirebase.value,
                    onCheckChanged = {
                        enableFirebase.value = it
                        settings.set(KEY_ENABLE_FIREBASE_UPLOADS, it)
                        Firebase.crashlytics.setCrashlyticsCollectionEnabled(it)
                    },
                ),
                basicSettingsToggleItem(
                    title = "Collect watch analytics",
                    description = "Only for Core Devices watches. This allows us to measure metrics e.g. battery life, and debug watch crashes (otherwise we do not know whether they are regressions in reliability or performance)",
                    section = Section.Analytics,
                    checked = enableMemfault.value,
                    onCheckChanged = {
                        enableMemfault.value = it
                        settings.set(KEY_ENABLE_MEMFAULT_UPLOADS, it)
                    },
                ),
                basicSettingsToggleItem(
                    title = "Collect app analytics",
                    description = "This allows us to track metrics e.g. connectivity, so that we can track different types of error and improve reliability",
                    section = Section.Analytics,
                    checked = enableMixpanel.value,
                    onCheckChanged = {
                        enableMixpanel.value = it
                        settings.set(KEY_ENABLE_MIXPANEL_UPLOADS, it)
                        analyticsBackend.setEnabled(it)
                    },
                ),
                basicSettingsActionItem(
                    title = "Post test notification",
                    description = "Create a test notification, with actions",
                    section = Section.Debug,
                    action = { postTestNotification(appContext) },
                    show = { pebbleFeatures.supportsPostTestNotification() },
                ),
                basicSettingsActionItem(
                    title = "Force JSCore GC",
                    description = "",
                    section = Section.Debug,
                    action = {
                        libPebble.watches.value.filterIsInstance<ConnectedPebble.CompanionAppControl>().forEach {
                            (it.currentCompanionAppSession.value as? PKJSApp)?.debugForceGC()
                        }
                    },
                    show = { debugOptionsEnabled },
                ),
                basicSettingsToggleItem(
                    title = "Show debug options",
                    description = "Show some extra debug options around the app - not useful for most users",
                    section = Section.Debug,
                    checked = debugOptionsEnabled,
                    onCheckChanged = {
                        settings.set(SHOW_DEBUG_OPTIONS, it)
                        debugOptionsEnabled = it
                    },
                ),
                basicSettingsToggleItem(
                    title = "Disable FW update notifications",
                    description = "Ignore notifications for users who sideload their own firmware",
                    section = Section.Debug,
                    checked = coreConfig.disableFirmwareUpdateNotifications,
                    onCheckChanged = {
                        coreConfigHolder.update(
                            coreConfig.copy(
                                disableFirmwareUpdateNotifications = it
                            )
                        )
                    },
                    show = { debugOptionsEnabled },
                ),
                basicSettingsActionItem(
                    title = "Do background sync",
                    description = "Sync firmware updates, locker, etc manually now (happened regularly automatically)",
                    section = Section.Debug,
                    action = {
                        GlobalScope.launch {
                            coreBackgroundSync.doBackgroundSync()
                        }
                    },
                    show = { debugOptionsEnabled },
                ),
                SettingsItem(
                    title = TITLE_PKJS_TOKEN,
                    section = Section.Debug,
                    item = {
                        ListItem(
                            headlineContent = {
                                Text(TITLE_PKJS_TOKEN)
                            },
                            supportingContent = {
                                if (loggedIn == null) {
                                    Text("Not logged in")
                                }
                            },
                            modifier = Modifier.clickable(enabled = loggedIn != null) {
                                setShowCopyTokenDialog(true)
                            },
                            shadowElevation = ELEVATION,
                        )
                    },
                    show = { debugOptionsEnabled },
                ),
                basicSettingsActionItem(
                    title = "Sign Out",
                    description = "Sign out of your Google account",
                    section = Section.Default,
                    action = {
                        scope.launch {
                            try {
                                Firebase.auth.signOut()
                                analyticsBackend.setUser(email = null)
                                logger.d { "User signed out" }
                            } catch (e: Exception) {
                                logger.e(e) { "Failed to sign out" }
                            }
                        }
                    },
                    show = { user != null },
                ),
                basicSettingsToggleItem(
                    title = "Emulate Timeline Webservice",
                    description = "Intercept calls to Timeline webservice, instead inserting pins locally, immediately",
                    section = Section.Apps,
                    checked = libPebbleConfig.watchConfig.emulateRemoteTimeline,
                    onCheckChanged = {
                        libPebble.updateConfig(
                            libPebbleConfig.copy(
                                watchConfig = libPebbleConfig.watchConfig.copy(
                                    emulateRemoteTimeline = it
                                )
                            )
                        )
                    },
                ),
            )
        }
        val filteredItems by remember(settingsItems, topBarParams.searchState.query, user) {
            derivedStateOf {
                val query = topBarParams.searchState.query
                if (query.isEmpty()) {
                    settingsItems.filter { item -> item.show() }
                } else {
                    settingsItems.filter {
                        (it.title.contains(query, ignoreCase = true) ||
                                it.keywords.contains(query, ignoreCase = true)) && it.show()
                    }
                }
            }
        }

        LazyColumn {
            filteredItems.groupBy { it.section }.entries.sortedBy { it.key.ordinal }
                .forEach { (section, items) ->
                    stickyHeader {
                        Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                            Text(
                                section.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    items(
                        items = items,
                        key = { it.title },
                    ) { item ->
                        item.item()
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                    }
                }
        }
    }
}

fun basicSettingsActionItem(
    title: String,
    section: Section,
    action: (() -> Unit)?,
    description: String? = null,
    keywords: String = "",
    show: () -> Boolean = { true },
    badge: String? = null,
) = SettingsItem(
    title = title,
    section = section,
    keywords = keywords,
    show = show,
    item = {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (badge != null) {
                        Badge {
                            Text(text = badge)
                        }
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    if (action != null) {
                        PebbleElevatedButton(
                            onClick = { action() },
                            text = title,
                            primaryColor = false,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    } else {
                        Text(title)
                    }
                }
            },
            supportingContent = {
                if (description != null) {
                    Text(description, fontSize = 12.sp)
                }
            },
            shadowElevation = ELEVATION,
        )
    }
)

fun basicSettingsToggleItem(
    title: String,
    section: Section,
    checked: Boolean,
    onCheckChanged: (Boolean) -> Unit,
    description: String? = null,
    keywords: String = "",
    show: () -> Boolean = { true },
) = SettingsItem(
    title = title,
    section = section,
    keywords = keywords,
    show = show,
    item = {
        ListItem(
            headlineContent = {
                Text(title)
            },
            leadingContent = {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckChanged,
                )
            },
            supportingContent = {
                if (description != null) {
                    Text(description, fontSize = 11.sp)
                }
            },
            shadowElevation = ELEVATION,
        )
    },
)

private const val TITLE_PKJS_TOKEN = "Copy PKJS account token"
private const val TITLE_APP_THEME = "App Theme"
private const val SHOW_DEBUG_OPTIONS = "showDebugOptions"

fun Settings.showDebugOptions() = getBoolean(SHOW_DEBUG_OPTIONS, false)

@Composable
fun PKJSCopyTokenDialog(onDismissRequest: () -> Unit) {
    val libPebble = rememberLibPebble()
    val lockerEntriesFlow = remember { libPebble.getAllLockerBasicInfo() }
    val lockerEntries by lockerEntriesFlow.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    Dialog(
        onDismissRequest = onDismissRequest,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(
                    "Copy Account Token",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    items(lockerEntries.size) {
                        val lockerEntry = lockerEntries[it]
                        Column {
                            ListItem(
                                headlineContent = {
                                    Text(lockerEntry.title)
                                },
                                supportingContent = {
                                    Text(lockerEntry.developerName)
                                },
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        val token =
                                            libPebble.getAccountToken(lockerEntry.id)
                                        if (token != null) {
                                            launch {
                                                clipboard.setClipEntry(makeTokenClipEntry(token))
                                            }
                                        } else {
                                            logger.e { "Failed to get account token for ${lockerEntry.id}" }
                                        }
                                        onDismissRequest()
                                    }
                                },
                                shadowElevation = ELEVATION,
                            )
                        }
                    }
                }
            }
        }
    }
}

expect fun getPlatformSTTLanguages(): List<Pair<String, String>>

@Composable
fun STTModeDialog(
    onModeSelected: (RequestedSTTMode) -> Unit,
    onDismissRequest: () -> Unit,
    selectedMode: RequestedSTTMode,
    showModelSelection: Boolean = false,
) {
    var targetMode by remember { mutableStateOf(selectedMode.mode) }
    var targetModel by remember {
        val selected = (selectedMode as? RequestedSTTMode.Enabled)?.modelName
        mutableStateOf<String>(selected ?: CommonBuildKonfig.CACTUS_DEFAULT_STT_MODEL)
    }
    var showModelDropdown by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<VoiceModel>?>(null) }
    if (showModelSelection) {
        LaunchedEffect(availableModels) {
            if (availableModels == null) {
                availableModels = withContext(Dispatchers.IO) {
                    val stt = CactusSTT()
                    listOf(TranscriptionProvider.WHISPER).flatMap {
                        stt.getVoiceModels(it)
                    }
                }
            }
        }
    }
    M3Dialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Default.AppSettingsAlt, contentDescription = null) },
        title = { Text("Select speech recognition mode") },
        buttons = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text("Cancel")
            }
            TextButton(
                onClick = {
                    onModeSelected(RequestedSTTMode.Enabled(targetMode, targetModel))
                }
            ) {
                Text("OK")
            }
        }
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(CactusSTTMode.Disabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            targetMode = CactusSTTMode.Disabled
                        }
                ) {
                    RadioButton(
                        selected = targetMode == CactusSTTMode.Disabled,
                        onClick = {
                            targetMode = CactusSTTMode.Disabled
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Disabled (no speech recognition)")
                }
            }
            item(CactusSTTMode.Local) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            targetMode = CactusSTTMode.Local
                        }
                ) {
                    RadioButton(
                        selected = targetMode == CactusSTTMode.Local,
                        onClick = {
                            targetMode = CactusSTTMode.Local
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("On-device")
                        Text(
                            "Speech recognition is performed on the device, using a downloaded model. (Lower accuracy)",
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            item(CactusSTTMode.RemoteFirst) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            targetMode = CactusSTTMode.RemoteFirst
                        }
                ) {
                    RadioButton(
                        selected = targetMode == CactusSTTMode.RemoteFirst,
                        onClick = {
                            targetMode = CactusSTTMode.RemoteFirst
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Remote first")
                        Text(
                            "Speech recognition is performed using an online service, with a local fallback.",
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            if (showModelSelection) {
                if (availableModels == null) {
                    item {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    }
                } else {
                    item {
                        Spacer(Modifier.height(16.dp))
                    }
                    item {
                        ExposedDropdownMenuBox(
                            expanded = showModelDropdown,
                            onExpandedChange = { showModelDropdown = it }
                        ) {
                            TextField(
                                value = targetModel,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showModelDropdown)
                                },
                                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )

                            ExposedDropdownMenu(
                                expanded = showModelDropdown,
                                onDismissRequest = { showModelDropdown = false }
                            ) {
                                availableModels?.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text("${model.slug}: ${model.language}") },
                                        onClick = {
                                            targetModel = model.slug
                                            showModelDropdown = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun STTLanguageDialog(
    onLanguageSelected: (String?) -> Unit,
    onDismissRequest: () -> Unit,
    selectedLanguage: String?,
) {
    var targetLanguage by remember { mutableStateOf(selectedLanguage) }
    M3Dialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Default.Language, contentDescription = null) },
        title = { Text("Select language") },
        buttons = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text("Cancel")
            }
            TextButton(
                onClick = {
                    onLanguageSelected(targetLanguage)
                }
            ) {
                Text("OK")
            }
        }
    ) {
        val supportedLanguages = remember { getPlatformSTTLanguages() }
        LazyColumn {
            items(supportedLanguages.size) {
                val (code, name) = supportedLanguages[it]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            targetLanguage = if (targetLanguage == code) {
                                null
                            } else {
                                code
                            }
                        }
                        .padding(16.dp)
                ) {
                    Checkbox(
                        checked = targetLanguage == code,
                        onCheckedChange = {
                            targetLanguage = if (it) {
                                code
                            } else {
                                null
                            }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(name)
                }
            }
        }
    }
}


@Preview
@Composable
fun STTModeDialogPreview() {
    PreviewWrapper {
        STTModeDialog(
            onModeSelected = {},
            onDismissRequest = {},
            selectedMode = RequestedSTTMode.Enabled(
                mode = CactusSTTMode.Local,
                modelName = "en-us-vosk-small",
            ),
            showModelSelection = true,
        )
    }
}

expect fun makeTokenClipEntry(token: String): ClipEntry

object SettingsKeys {
    const val KEY_ENABLE_MEMFAULT_UPLOADS = "enable_memfault_uploads"
    const val KEY_ENABLE_FIREBASE_UPLOADS = "enable_firebase_uploads"
    const val KEY_ENABLE_MIXPANEL_UPLOADS = "enable_mixpanel_uploads"
    const val KEY_CACTUS_MODE = "cactus_mode"
    const val KEY_CACTUS_STT_MODEL = "cactus_stt_model"
}