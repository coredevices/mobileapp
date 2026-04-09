package coredevices.pebble.ui

import CommonRoutes
import NextBugReportContext
import PlatformShareLauncher
import PlatformUiContext
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.NotificationAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.Settings
import com.russhwolf.settings.serialization.decodeValue
import com.russhwolf.settings.serialization.encodeValue
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.devices
import coredevices.firestore.UsersDao
import coredevices.pebble.PebbleFeatures
import coredevices.pebble.account.PebbleAccount
import coredevices.pebble.firmware.FirmwareUpdateUiTracker
import coredevices.pebble.rememberLibPebble
import coredevices.pebble.services.LanguagePack
import coredevices.pebble.services.LanguagePackRepository
import coredevices.pebble.services.displayName
import coredevices.ui.ConfirmDialog
import coredevices.ui.CoreLinearProgressIndicator
import coredevices.ui.PebbleElevatedButton
import coredevices.util.CompanionDevice
import coredevices.util.CoreConfigFlow
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import coredevices.util.PermissionResult
import coredevices.util.rememberUiContext
import io.rebble.libpebblecommon.connection.ActiveDevice
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.BleDiscoveredPebbleDevice
import io.rebble.libpebblecommon.connection.CommonConnectedDevice
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.ConnectedPebbleDeviceInRecovery
import io.rebble.libpebblecommon.connection.ConnectingPebbleDevice
import io.rebble.libpebblecommon.connection.ConnectionFailureReason
import io.rebble.libpebblecommon.connection.DisconnectingPebbleDevice
import io.rebble.libpebblecommon.connection.DiscoveredPebbleDevice
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.connection.KnownPebbleDevice
import io.rebble.libpebblecommon.connection.PebbleDevice
import io.rebble.libpebblecommon.connection.bt.BluetoothState
import io.rebble.libpebblecommon.connection.color
import io.rebble.libpebblecommon.connection.endpointmanager.FirmwareUpdateErrorStarting
import io.rebble.libpebblecommon.connection.endpointmanager.FirmwareUpdater
import io.rebble.libpebblecommon.connection.endpointmanager.LanguagePackInstallState
import io.rebble.libpebblecommon.database.entity.buildTimelineNotification
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.services.blobdb.TimelineActionResult
import io.rebble.libpebblecommon.timeline.TimelineColor
import io.rebble.libpebblecommon.timeline.toPebbleColor
import io.rebble.libpebblecommon.util.getTempFilePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import theme.CoreAppColorScheme
import theme.coreOrange
import theme.currentColorScheme
import kotlin.time.Clock
import kotlin.uuid.Uuid

expect fun scanPermission(): Permission?

expect fun getIPAddress(): Pair<String?, String?>

private val logger = Logger.withTag("WatchesScreen")

@Composable
fun WatchesScreen(navBarNav: NavBarNav, topBarParams: TopBarParams) {
    val libPebble = rememberLibPebble()
    val isScanningBle by libPebble.isScanningBle.collectAsState()
    val scope = rememberCoroutineScope()
    val permissionRequester: PermissionRequester = koinInject()
    val requiredScanPermission = remember { scanPermission() }
    val bluetoothEnabled by libPebble.bluetoothEnabled.collectAsState()
    var addFabExpanded by remember { mutableStateOf(false) }

    fun scan(uiContext: PlatformUiContext) {
        scope.launch {
            if (requiredScanPermission != null && permissionRequester.missingPermissions.value.contains(
                    requiredScanPermission
                )
            ) {
                val result =
                    permissionRequester.requestPermission(requiredScanPermission, uiContext)
                if (result != PermissionResult.Granted) {
                    logger.w { "Failed to grant scan permission" }
                    return@launch
                }
            }
            libPebble.startBleScan()
        }
    }

    Scaffold(
        floatingActionButton = {
            if (!bluetoothEnabled.enabled()) {
                FloatingActionButton(
                    onClick = { topBarParams.showSnackbar("Enable Bluetooth to connect a Pebble") }
                ) {
                    Icon(Icons.Filled.BluetoothDisabled, "Bluetooth is disabled")
                }
            } else if (isScanningBle) {
                FloatingActionButton(
                    onClick = { libPebble.stopBleScan() }
                ) {
                    Icon(Icons.Filled.Stop, "Stop Scanning")
                }
            } else {
                FloatingActionButtonMenu(
//                    modifier = Modifier.padding(16.dp),
                    expanded = addFabExpanded,
                    button = {
                        ToggleFloatingActionButton(
                            checked = addFabExpanded,
                            onCheckedChange = { addFabExpanded = !addFabExpanded },
                        ) {
                            Icon(
                                imageVector = if (addFabExpanded) Icons.Filled.Close else Icons.Filled.Add,
                                contentDescription = "Add a Pebble",
                                tint = Color.White,
                            )
                        }
                    },
                ) {
                    val uiContext = rememberUiContext()
                    // TODO bt classic / index go here
                    FloatingActionButtonMenuItem(
                        onClick = {
                            addFabExpanded = false
                            if (uiContext != null) {
                                scan(uiContext)
                            }
                        },
                        icon = { Icon(Icons.Default.Watch, contentDescription = "Watch") },
                        text = { Text("Add Watch") },
                    )
                }
            }
        },
    ) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            val firmwareUpdateUiTracker = koinInject<FirmwareUpdateUiTracker>()
            val otherPebbleAppsInstalledFlow =
                remember { libPebble.otherPebbleCompanionAppsInstalled() }
            val otherPebbleAppsInstalled by otherPebbleAppsInstalledFlow.collectAsState()
            val pebbleFeatures = koinInject<PebbleFeatures>()
            val coreConfigFlow = koinInject<CoreConfigFlow>()
            val coreConfig by coreConfigFlow.flow.collectAsState()
            val showOtherPebbleAppsWarningAndPreventConnection =
                otherPebbleAppsInstalled.isNotEmpty() && !coreConfig.ignoreOtherPebbleApps
            val companionDevice: CompanionDevice = koinInject()
            val companionDevicePreviouslyCrashed =
                remember { companionDevice.cdmPreviouslyCrashed() }

            val title = stringResource(Res.string.devices)
            val listState = rememberLazyListState()

            LaunchedEffect(Unit) {
                topBarParams.searchAvailable(null)
                topBarParams.title(title)
                topBarParams.actions {}
                launch {
                    topBarParams.scrollToTop.collect {
                        listState.animateScrollToItem(0)
                    }
                }
                if (libPebble.watches.value.isEmpty()) {
                    addFabExpanded = true
                }

                if (firmwareUpdateUiTracker.shouldUiUpdateCheck()) {
                    firmwareUpdateUiTracker.didFirmwareUpdateCheckFromUi()
                    libPebble.checkForFirmwareUpdates()
                }
            }

            val watchesFlow = remember {
                libPebble.watches
                    .map { it.sortedWith(PebbleDeviceComparator) }
            }
            val watches by watchesFlow.collectAsState(
                initial = libPebble.watches.value.sortedWith(
                    PebbleDeviceComparator
                )
            )

            Column {
                if (!bluetoothEnabled.enabled()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "Enable bluetooth to connect to your watch.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(15.dp)
                        )
                    }
                }
                if (pebbleFeatures.supportsDetectingOtherPebbleApps() && showOtherPebbleAppsWarningAndPreventConnection) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        val otherAppNames = otherPebbleAppsInstalled.joinToString { it.name }
                        Text(
                            text = "One or more other PebbleOS companions apps are installed. Please " +
                                    "uninstall them ($otherAppNames) to avoid connectivity problems.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(15.dp)
                        )
                    }
                }
                if (companionDevicePreviouslyCrashed) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "If the app crashes every time you press Connect, try checking" +
                                    " \"Disable Companion Device Manager\" in Settings",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(15.dp)
                        )
                    }
                }
                if (isScanningBle) {
                    Text(
                        text = "Scanning for watches...",
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(5.dp)
                    )
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(5.dp)
                    )
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 6.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "Remember to unpair any other phones from your watch before connecting (Settings/Bluetooth)",
                            modifier = Modifier.padding(15.dp).align(Alignment.CenterHorizontally),
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    if (watches.isEmpty()) {
                        if (!pebbleFeatures.supportsDetectingOtherPebbleApps()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = "If you have any other Pebble apps installed on your phone, please uninstall them - " +
                                            "connection to the watch will not work while they are installed.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(15.dp)
                                )
                            }
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    state = listState,
                ) {
                    itemsIndexed(
                        items = watches,
                        key = { _, watch -> watch.identifier.asString }
                    ) { index, watch ->
                        WatchItem(
                            watch = watch,
                            bluetoothState = bluetoothEnabled,
                            allowedToConnect = !showOtherPebbleAppsWarningAndPreventConnection,
                            navBarNav = navBarNav,
                        )
                        if (index < watches.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun WatchesPreview() {
    PreviewWrapper {
        WatchesScreen(
            navBarNav = NoOpNavBarNav,
            topBarParams = WrapperTopBarParams,
        )
    }
}

@Composable
private fun NicknameDialog(watch: KnownPebbleDevice, onDismissRequest: () -> Unit) {
    var nickname by remember { mutableStateOf(watch.nickname) }
    val focusRequester = remember { FocusRequester() }

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(nickname ?: watch.name))
    }

    // 1. Initial "Select All" logic when dialog opens
    LaunchedEffect(Unit) {
        textFieldValue = textFieldValue.copy(
            selection = TextRange(0, textFieldValue.text.length)
        )
        focusRequester.requestFocus()
    }

    // 2. Focus logic when nickname is cleared (via the button)
    LaunchedEffect(nickname) {
        // Only request focus if we aren't at the very start (Unit handle that)
        // This ensures the keyboard pops back up if the user hits "Cancel"
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Rename") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = textFieldValue,
                        onValueChange = {
                            if (it.text.length <= 25) {
                                textFieldValue = it
                                nickname = if (it.text == watch.name) null else it.text
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester), // Bind focus requester
                        singleLine = true,
                    )
                    if (nickname != null) {
                        IconButton(
                            onClick = {
                                nickname = null
                                textFieldValue = TextFieldValue(watch.name)
                            },
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = "Remove Nickname")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    watch.setNickname(nickname)
                    onDismissRequest()
                },
                enabled = nickname != watch.nickname
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Back")
            }
        }
    )
}

object PebbleDeviceComparator : Comparator<PebbleDevice> {
    private fun getStateRank(device: PebbleDevice): Int {
        return when (device) {
            is ConnectedPebbleDevice -> 0
            is ConnectedPebbleDeviceInRecovery -> 1
            is ConnectingPebbleDevice -> 3
            is DisconnectingPebbleDevice -> 2
            is KnownPebbleDevice -> 4
            is DiscoveredPebbleDevice -> 5
            else -> Int.MAX_VALUE
        }
    }

    override fun compare(a: PebbleDevice, b: PebbleDevice): Int {
        val rankA = getStateRank(a)
        val rankB = getStateRank(b)
        return if (rankA != rankB) rankA.compareTo(rankB) else {
            // Sort by last connected if available
            val lastConnectedA =
                (a as? KnownPebbleDevice)?.lastConnected?.epochSeconds ?: Long.MIN_VALUE
            val lastConnectedB =
                (b as? KnownPebbleDevice)?.lastConnected?.epochSeconds ?: Long.MIN_VALUE
            if (lastConnectedA != lastConnectedB) {
                lastConnectedB.compareTo(lastConnectedA)
            } else {
                a.name.compareTo(b.name)
            }
        }
    }
}

fun FirmwareUpdateErrorStarting.message(): String = when (this) {
    FirmwareUpdateErrorStarting.ErrorDownloading -> "Failed to download firmware"
    FirmwareUpdateErrorStarting.ErrorParsingPbz -> "Failed to parse manifest"
}

@Composable
fun PebbleDevice.stateText(
    firmwareUpdateState: FirmwareUpdater.FirmwareUpdateStatus,
    languagePackInstallState: LanguagePackInstallState,
): String {
    val installingState = when (firmwareUpdateState) {
        is FirmwareUpdater.FirmwareUpdateStatus.InProgress -> {
            val progress by firmwareUpdateState.progress.collectAsState()
            " - Updating to PebbleOS ${firmwareUpdateState.update.version.stringVersion} (${(progress * 100).toInt()}%)"
        }

        is FirmwareUpdater.FirmwareUpdateStatus.NotInProgress.ErrorStarting -> " - Error starting update: ${firmwareUpdateState.error.message()}"
        is FirmwareUpdater.FirmwareUpdateStatus.NotInProgress.Idle -> when (languagePackInstallState) {
            is LanguagePackInstallState.Installing -> " - installing language pack: ${languagePackInstallState.language}"
            is LanguagePackInstallState.Idle -> ""
            is LanguagePackInstallState.Downloading -> " - downloading language pack: ${languagePackInstallState.language}"
        }
        is FirmwareUpdater.FirmwareUpdateStatus.WaitingForReboot -> " - Rebooting watch to finish update to ${firmwareUpdateState.update.version.stringVersion}"
        is FirmwareUpdater.FirmwareUpdateStatus.WaitingToStart -> " - Updating to PebbleOS ${firmwareUpdateState.update.version.stringVersion}"
    }
    val btClassicText = when {
        this is ActiveDevice && usingBtClassic -> " (BT Classic)"
        else -> ""
    }
    val stateText = when (this) {
        is ConnectedPebbleDevice -> "Connected$installingState"
        is ConnectedPebbleDeviceInRecovery -> "Connected (Factory)$installingState"
        is ConnectingPebbleDevice -> {
            when {
                rebootingAfterFirmwareUpdate -> if (negotiating) {
                    "Rebooting after update - Negotiating"
                } else {
                    "Rebooting after update - Waiting"
                }

                negotiating -> "Negotiating"
                else -> "Connecting"
            }
        }

        is KnownPebbleDevice, is DiscoveredPebbleDevice -> "Disconnected"
        is DisconnectingPebbleDevice -> "Disconnecting"
        else -> "Unknown ($this)"
    }
    return "$stateText$btClassicText"
}

private fun PebbleDevice.isActive(): Boolean = when (this) {
    is ConnectedPebbleDevice, is ConnectingPebbleDevice, is ConnectedPebbleDeviceInRecovery -> true
    else -> false
}

expect fun postTestNotification(appContext: AppContext)

expect fun ImageBitmap.toPngBytes(): ByteArray

@Composable
fun WatchItem(
    watch: PebbleDevice,
    bluetoothState: BluetoothState,
    allowedToConnect: Boolean,
    navBarNav: NavBarNav,
) {
    ListItem(
        headlineContent = {
            WatchHeader(watch)
        },
        supportingContent = {
            Column {
                WatchDetails(
                    watch = watch,
                    bluetoothState = bluetoothState,
                    allowedToConnect = allowedToConnect,
                    navBarNav = navBarNav,
                )
            }
        },
    )
}

@Composable
fun WatchMenu(watch: PebbleDevice, navBarNav: NavBarNav) {
    var showMenu by remember { mutableStateOf(false) }
    var debugMenuExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showScreenshotDialog by remember { mutableStateOf(false) }
    var showForgetDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    val showConfirmCoreDumpDialog = remember { mutableStateOf(false) }
    val showConfirmResetIntoPrfDialog = remember { mutableStateOf(false) }
    val showConfirmFactoryResetDialog = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentColorScheme = currentColorScheme()

    Box {
        IconButton(onClick = { showMenu = !showMenu }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.widthIn(min = 250.dp).then(
                if (currentColorScheme == CoreAppColorScheme.Grey) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(4.dp)
                    )
                } else Modifier
            ),
            containerColor = if (currentColorScheme == CoreAppColorScheme.Grey) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MenuDefaults.containerColor
            },
        ) {
            val firmwareVersion = when {
                watch is KnownPebbleDevice -> watch.runningFwVersion
                watch is BleDiscoveredPebbleDevice -> {
                    val extInfo = watch.pebbleScanRecord.extendedInfo
                    if (extInfo != null) {
                        "v${extInfo.major}.${extInfo.minor}.${extInfo.patch}"
                    } else null
                }

                else -> null
            }
            if (firmwareVersion != null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "PebbleOS $firmwareVersion",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            if (watch is ConnectedPebble.Firmware) {
                if (watch.firmwareUpdateAvailable.checkingForUpdates) {
                    val infiniteTransition =
                        rememberInfiniteTransition(label = "checkUpdateRotation")
                    val rotate by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "rotate"
                    )

                    DropdownMenuItem(
                        text = { Text("Checking for updates...") },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Autorenew,
                                contentDescription = null,
                                modifier = Modifier.graphicsLayer { rotationZ = rotate },
                            )
                        },
                        enabled = false,
                        onClick = {}
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Check for Updates") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Autorenew, contentDescription = null)
                        },
                        onClick = {
                            watch.checkforFirmwareUpdate()
                        }
                    )
                }
            }

            HorizontalDivider()

            if (watch is ConnectedPebbleDevice) {
                val languagePackInstalled = watch.languagePackInstalled()
                val languageText = when  {
                    languagePackInstalled.isNullOrBlank() -> "Language"
                    else -> "Language: $languagePackInstalled"
                }
                DropdownMenuItem(
                    text = { Text(languageText) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Language,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        showMenu = false
                        showLanguageDialog = true
                    }
                )

                DropdownMenuItem(
                    text = { Text("Screenshot") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Image,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        showMenu = false
                        showScreenshotDialog = true
                    }
                )
                HorizontalDivider()
            }

            if (watch is KnownPebbleDevice) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.DriveFileRenameOutline,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        showMenu = false
                        showRenameDialog = true
                    }
                )

                DropdownMenuItem(
                    text = { Text("Remove") },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        showForgetDialog = true
                    }
                )

                HorizontalDivider()
            }

            val libPebble = rememberLibPebble()
            if (watch is ConnectedPebble.DevConnection) {
                val config by libPebble.config.collectAsState()
                val usersDao: UsersDao = koinInject()
                val user by usersDao.user.collectAsState(null)
                val active by watch.devConnectionActive.collectAsState()
                val canUseDevConnection = user?.isAnonymousUser == false || config.watchConfig.lanDevConnection
                DropdownMenuItem(
                    text = { Text("Dev Connection") },
                    leadingIcon = { Icon(Icons.Outlined.DeveloperBoard, contentDescription = null) },
                    trailingIcon = { Switch(
                        checked = active,
                        onCheckedChange = null,
                    ) },
                    enabled = canUseDevConnection,
                    onClick = {
                        scope.launch {
                            if (active) {
                                watch.stopDevConnection()
                            } else {
                                watch.startDevConnection()
                            }
                        }
                    }
                )

                if (active) {
                    if (config.watchConfig.lanDevConnection) {
                        val (v4, v6) = remember { getIPAddress() }
                        v4?.let {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = "IPv4: $v4",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        v6?.let {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = "IPv6: $v6",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = "Connected to CloudPebble",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                HorizontalDivider()
            }

            val settings: Settings = koinInject()
            val showDebugOptions = settings.showDebugOptions()
            if (showDebugOptions && watch is CommonConnectedDevice) {
                Box {
                    DropdownMenuItem(
                        text = { Text("Debug") },
                        leadingIcon = { Icon(Icons.Default.Terminal, null) },
                        trailingIcon = { Icon(Icons.Default.ChevronRight, null) },
                        onClick = { debugMenuExpanded = true }
                    )

                    val debugMenuTheme = MaterialTheme.colorScheme.copy(
                        surface = MaterialTheme.colorScheme.primary,
                        onSurface = MaterialTheme.colorScheme.onPrimary
                    )
                    MaterialTheme(colorScheme = debugMenuTheme) {
                        DropdownMenu(
                            expanded = debugMenuExpanded,
                            onDismissRequest = { debugMenuExpanded = false },
                            containerColor = MaterialTheme.colorScheme.primary,
                        ) {
                            DropdownMenuItem(
                                text = { Text("Firmware Update Debug") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.SystemUpdateAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    debugMenuExpanded = false
                                    navBarNav.navigateTo(
                                        PebbleRoutes.FirmwareSideloadRoute(
                                            watch.identifier.asString,
                                        )
                                    )
                                }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f))

                            if (watch is ConnectedPebbleDevice) {
                                DropdownMenuItem(
                                    text = { Text("Ping Watch") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.NetworkPing,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        debugMenuExpanded = false
                                        scope.launch { watch.sendPing(1234u) }
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Write Notification") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.NotificationAdd,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        debugMenuExpanded = false
                                        scope.launch { showNotificationDialog = true }
                                    }
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f))

                                DropdownMenuItem(
                                    text = { Text("Create a Core Dump") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        debugMenuExpanded = false
                                        showConfirmCoreDumpDialog.value = true
                                    }
                                )

                                if (watch.watchInfo.recoveryFwVersion != null) {
                                    DropdownMenuItem(
                                        text = { Text("Reset into PRF") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            debugMenuExpanded = false
                                            showConfirmResetIntoPrfDialog.value = true
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Factory reset") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            debugMenuExpanded = false
                                            showConfirmFactoryResetDialog.value = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()
            }

            val serial = when (watch) {
                is KnownPebbleDevice -> watch.serial
                is BleDiscoveredPebbleDevice -> watch.pebbleScanRecord.serialNumber
                else -> null
            }
            if (serial != null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Serial: $serial",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }

    if (showRenameDialog && watch is KnownPebbleDevice) {
        NicknameDialog(watch, onDismissRequest = { showRenameDialog = false })
    }
    if (showScreenshotDialog && watch is ConnectedPebble.Screenshot) {
        ScreenshotDialog(watch, onDismissRequest = { showScreenshotDialog = false }, navBarNav)
    }
    if (showLanguageDialog && watch is ConnectedPebbleDevice) {
        LanguageDialog(watch, onDismissRequest = { showLanguageDialog = false })
    }
    if (showNotificationDialog && watch is ConnectedPebbleDevice) {
        NotificationDialog(
            onDismiss = { showNotificationDialog = false },
        )
    }
    if (watch is ConnectedPebbleDevice) {
        ConfirmDialog(
            show = showConfirmCoreDumpDialog,
            title = "Create a Core Dump?",
            text = "This will capture the current state of the watch - then the watch will reset. Send a bug report after reconnection to send us the core dump. Only use this if we asked you to!",
            onConfirm = {
                watch.createCoreDump()
            },
            confirmText = "OK",
        )
        ConfirmDialog(
            show = showConfirmResetIntoPrfDialog,
            title = "Reset into PRF?",
            text = "This will reset the watch into recovery mode. Not for general public use.",
            onConfirm = {
                if (watch.watchInfo.recoveryFwVersion != null) {
                    watch.resetIntoPrf()
                }
            },
            confirmText = "OK",
        )
        ConfirmDialog(
            show = showConfirmFactoryResetDialog,
            title = "Factory reset?",
            text = "This will wipe the watch completely",
            onConfirm = {
                if (watch.watchInfo.recoveryFwVersion != null) {
                    watch.factoryReset()
                }
            },
            confirmText = "OK",
        )
    }
    if (showForgetDialog && watch is KnownPebbleDevice) {
        AlertDialog(
            onDismissRequest = { showForgetDialog = false },
            title = { Text("Remove ${watch.displayName()}?") },
            text = { Text("Are you sure?") },
            confirmButton = {
                TextButton(onClick = {
                    logger.d { "forget: ${watch.identifier}" }
                    watch.forget()
                    showForgetDialog = false
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showForgetDialog = false
                }) { Text("Cancel") }
            }
        )
    }
}

@Serializable
data class TestNotificationContent(
    val title: String = "Test Notification",
    val body: String = "This is a test notification",
    val icon: TimelineIcon? = TimelineIcon.GenericSms,
    val color: TimelineColor? = TimelineColor.Orange,
)

fun ConnectedPebbleDevice.languagePackInstalled(): String? {
    val languagePackInstalledAtConnectionTime = installedLanguagePack
    val languagePackRecentlyInstalledSinceConnection = (languagePackInstallState as? LanguagePackInstallState.Idle)?.successfullyInstalledLanguage
    return languagePackRecentlyInstalledSinceConnection ?: languagePackInstalledAtConnectionTime?.let { "${it.isoLocal} (v${it.version})" }
}

private const val CUSTOM_NOTIFICATION_CONTENT_KEY = "custom_notification_content"

@OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
@Composable
private fun NotificationDialog(
    onDismiss: () -> Unit,
) {
    val settings: Settings = koinInject()
    var content by remember { mutableStateOf(settings.decodeValue(CUSTOM_NOTIFICATION_CONTENT_KEY, TestNotificationContent())) }
    val scope = rememberCoroutineScope()
    val libPebble = rememberLibPebble()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Notification") },
        text = {
            Column {
                TextField(
                    value = content.title,
                    onValueChange = { content = content.copy(title = it) },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                TextField(
                    value = content.body,
                    onValueChange = { content = content.copy(body = it) },
                    label = { Text("Body") },
                    modifier = Modifier.fillMaxWidth()
                )
                SelectColorOrNone(
                    currentColorName = content.color?.name,
                    onChangeColor = { color ->
                        content = content.copy(color = color)
                    },
                )
                SelectIconOrNone(
                    currentIcon = TimelineIcon.fromCode(content.icon?.code),
                    onChangeIcon = { icon ->
                        content = content.copy(icon = icon)
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.encodeValue(CUSTOM_NOTIFICATION_CONTENT_KEY, content)
                onDismiss()
                scope.launch {
                    val testActionId: UByte = 0u
                    val notif = buildTimelineNotification(
                        timestamp = Clock.System.now(),
                        parentId = Uuid.NIL,
                    ) {
                        layout = TimelineItem.Layout.GenericNotification
                        attributes {
                            title { content.title }
                            body { content.body }
                            content.icon?.let { tinyIcon { it } }
                            content.color?.let { backgroundColor { it.toPebbleColor() } }
                        }
                        actions {
                            action(TimelineItem.Action.Type.Generic) {
                                attributes {
                                    title { "Test" }
                                }
                            }
                        }
                    }
                    libPebble.sendNotification(
                        notif, mapOf(
                            testActionId to { _ ->
                                TimelineActionResult(
                                    success = true,
                                    icon = TimelineIcon.GenericConfirmation,
                                    title = "Test Success"
                                )
                            }
                        ))
                }
            }) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LanguageDialog(watch: ConnectedPebbleDevice, onDismissRequest: () -> Unit) {
    val languagePackRepository: LanguagePackRepository = koinInject()
    val languagePacks by produceState<List<LanguagePack>>(emptyList()) {
        value = languagePackRepository.languagePacksForWatch(watch)
    }
    var selectedLanguagePack: LanguagePack? by remember { mutableStateOf(null) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Language Packs") },
        text = {
            LazyColumn {
                items(languagePacks, key = { it.id }) { lp ->
                    val isSelected = selectedLanguagePack == lp
                    Text(
                        text = lp.displayName(),
                        modifier = Modifier.clickable {
                            selectedLanguagePack = lp
                        }.border(
                            width = 2.dp,
                            color = if (isSelected) coreOrange else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ).padding(9.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val lp = selectedLanguagePack
                    if (lp == null) {
                        logger.e { "why is selectedLanguagePack null?" }
                    } else {
                        watch.installLanguagePack(lp.file, lp.displayName())
                    }
                    onDismissRequest()
                },
                enabled = selectedLanguagePack != null,
            ) { Text("Install") }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismissRequest()
            }) { Text("Cancel") }
        }
    )
}

@Composable
fun ScreenshotDialog(watch: ConnectedPebble.Screenshot, onDismissRequest: () -> Unit, navBarNav: NavBarNav) {
    var screenshot by remember { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()
    val nextBugReportContext: NextBugReportContext = koinInject()
    val appContext: AppContext = koinInject()

    suspend fun takeScreenshot() {
        screenshot = null
        screenshot = watch.takeScreenshot()
        logger.v { "screenshot = $screenshot" }
    }

    LaunchedEffect(Unit) {
        takeScreenshot()
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Screenshot") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val screenshot = screenshot
                if (screenshot == null) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    IconButton(onClick = { scope.launch { takeScreenshot() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }

                    ElevatedCard(
                        shape = CutCornerShape(0.dp),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 8.dp,
                        ),
                        modifier = Modifier.background(Color.Transparent).padding(bottom = 8.dp),
                    ) {
                        val height = 140.dp
                        val width = height / screenshot.height * screenshot.width
                        Image(
                            bitmap = screenshot,
                            contentDescription = "Screenshot",
                            modifier = Modifier.height(height).width(width),
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        PebbleElevatedButton(
                            text = "Bug Report",
                            onClick = {
                                nextBugReportContext.nextContext = null
                                scope.launch {
                                    val tempScreenshotFile = withContext(Dispatchers.Default) {
                                        val file = getTempFilePath(
                                            appContext,
                                            "watch-screenshot.png"
                                        )
                                        SystemFileSystem.sink(file, append = false).buffered().use { sink ->
                                            sink.write(screenshot.toPngBytes())
                                        }
                                        file
                                    }
                                    navBarNav.navigateTo(
                                        CommonRoutes.BugReport(
                                            pebble = true,
                                            screenshotPath = tempScreenshotFile.toString(),
                                        )
                                    )
                                }
                            },
                            icon = Icons.Default.BugReport,
                            primaryColor = false,
                            modifier = Modifier.padding(5.dp),
                        )

                        val platformShareLauncher = koinInject<PlatformShareLauncher>()
                        PebbleElevatedButton(
                            text = "Share",
                            onClick = {
                                platformShareLauncher.shareImage(
                                    screenshot,
                                    "pebble_screenshot.png"
                                )
                            },
                            icon = Icons.Default.Share,
                            primaryColor = false,
                            modifier = Modifier.padding(5.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Back")
            }
        },
    )
}


@Composable
fun WatchHeader(watch: PebbleDevice) {
    Row {
        Text(
            text = watch.displayName(),
            fontSize = 23.sp,
            fontWeight = when {
                watch.isActive() -> FontWeight.Bold
                else -> FontWeight.Normal
            },
        )
    }
}

@Composable
fun WatchDetails(
    watch: PebbleDevice,
    bluetoothState: BluetoothState,
    allowedToConnect: Boolean,
    navBarNav: NavBarNav,
) {
    val firmwareUpdateState = remember(watch) {
        if (watch is ConnectedPebble.Firmware) {
            watch.firmwareUpdateState
        } else {
            FirmwareUpdater.FirmwareUpdateStatus.NotInProgress.Idle()
        }
    }
    val firmwareUpdateInProgress =
        firmwareUpdateState !is FirmwareUpdater.FirmwareUpdateStatus.NotInProgress
    val languagePackInstallState = (watch as? ConnectedPebble.LanguageState)?.languagePackInstallState ?: LanguagePackInstallState.Idle()
    Row {
        Text(
            text = watch.stateText(firmwareUpdateState, languagePackInstallState),
            fontWeight = when {
                watch.isActive() -> FontWeight.Bold
                else -> FontWeight.Normal
            },
            fontSize = 16.sp,
            modifier = Modifier.padding(vertical = 3.dp),
        )
    }
    if (firmwareUpdateInProgress) {
        if (firmwareUpdateState is FirmwareUpdater.FirmwareUpdateStatus.InProgress) {
            val progress by firmwareUpdateState.progress.collectAsState()
            CoreLinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
            )
        }
    } else if (languagePackInstallState is LanguagePackInstallState.Installing) {
        val progress by languagePackInstallState.progress.collectAsState()
        CoreLinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        )
    }
    val watchColorText = remember(watch) {
        watch.color()?.uiDescription
    }
    FlowRow(verticalArrangement = Arrangement.Center) {
        if (watch is ConnectedPebbleDevice) {
            val devConnectionActive by watch.devConnectionActive.collectAsState()
            if (devConnectionActive) {
                Icon(
                    imageVector = Icons.Outlined.DeveloperBoard,
                    contentDescription = "Developer connection active",
                    modifier = Modifier.size(18.dp).padding(end = 5.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (watchColorText != null) {
            Text(
                text = watchColorText,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(end = 5.dp).align(Alignment.CenterVertically),
            )
        }
        if (watch is ConnectedPebble.Battery) {
            val batteryLevel = watch.batteryLevel
            if (batteryLevel != null) {
                // Use a Row to keep Icon and Text together,
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 5.dp)
                ) {
                    Icon(
                        imageVector = batteryLevel.batteryIcon(),
                        contentDescription = "Battery",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$batteryLevel%",
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(5.dp))
    val firmwareUpdateAvailable =
        (watch as? ConnectedPebble.Firmware)?.firmwareUpdateAvailable?.result
    val firmwareUpdater = watch as? ConnectedPebble.Firmware
    val scope = rememberCoroutineScope()
    val companionDevice: CompanionDevice = koinInject()
    val pebbleAccount = koinInject<PebbleAccount>()
    val loggedIn by pebbleAccount.loggedIn.collectAsState()
    val uriHandler = LocalUriHandler.current

    if (firmwareUpdateAvailable is FirmwareUpdateCheckResult.FoundUpdate && firmwareUpdater != null && !firmwareUpdateInProgress) {
        var showFirmwareUpdateConfirmDialog by remember { mutableStateOf(false) }
        if (showFirmwareUpdateConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showFirmwareUpdateConfirmDialog = false },
                title = { Text("Install PebbleOS ${firmwareUpdateAvailable.version.stringVersion}") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(firmwareUpdateAvailable.notes)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showFirmwareUpdateConfirmDialog = false
                        firmwareUpdater.updateFirmware(firmwareUpdateAvailable)
                    }) { Text("Install") }
                },
                dismissButton = { TextButton(onClick = { showFirmwareUpdateConfirmDialog = false }) { Text("Cancel") } }
            )
        }
        PebbleElevatedButton(
            text = "Update PebbleOS to ${firmwareUpdateAvailable.version.stringVersion}",
            onClick = {
                logger.d { "Starting firmware update from watches screen" }
                showFirmwareUpdateConfirmDialog = true
            },
            enabled = bluetoothState.enabled(),
            icon = Icons.Default.SystemUpdateAlt,
            contentDescription = "Update PebbleOS",
            primaryColor = true,
            modifier = Modifier.padding(vertical = 5.dp),
        )
    } else if (firmwareUpdateAvailable is FirmwareUpdateCheckResult.UpdateCheckFailed) {
        Card(
            modifier = Modifier
//                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                text = firmwareUpdateAvailable.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(15.dp)
            )
        }
    }
    if (watch !is CommonConnectedDevice && watch.connectionFailureInfo?.reason == ConnectionFailureReason.CreateBondFailed) {
        var showPairingErrorDialog by remember { mutableStateOf(false) }
        if (showPairingErrorDialog) {
            AlertDialog(
                onDismissRequest = { showPairingErrorDialog = false },
                title = { Text("Error Pairing") },
                text = { Text("Please go to system bluetooth settings, and unpair this device") },
                confirmButton = {
                    TextButton(onClick = { showPairingErrorDialog = false }) { Text("OK") }
                }
            )
        }
        PebbleElevatedButton(
            text = "Error Pairing",
            icon = Icons.Default.Error,
            onClick = {
                showPairingErrorDialog = true
            },
            primaryColor = true,
            modifier = Modifier.padding(vertical = 5.dp),
        )
    }
    Row {
        Box(modifier = Modifier.weight(1f)) {
            if (watch is ActiveDevice) {
                PebbleElevatedButton(
                    text = "Disconnect",
                    onClick = { watch.disconnect() },
                    enabled = bluetoothState.enabled() && !firmwareUpdateInProgress,
                    primaryColor = false,
                    modifier = Modifier.padding(vertical = 5.dp),
                )
            } else if (watch !is DisconnectingPebbleDevice) {
                val uiContext = rememberUiContext()
                if (uiContext != null) {
                    PebbleElevatedButton(
                        text = "Connect",
                        onClick = {
                            scope.launch {
                                companionDevice.registerDevice(watch.identifier, uiContext)
                                watch.connect()
                            }
                        },
                        enabled = bluetoothState.enabled() && allowedToConnect,
                        primaryColor = false,
                        modifier = Modifier.padding(vertical = 5.dp),
                    )
                }
            }
        }
        WatchMenu(watch, navBarNav)
    }
}

fun Int.batteryIcon() = when {
    this >= 98 -> Icons.Default.BatteryFull
    this >= 82 -> Icons.Default.Battery6Bar
    this >= 67 -> Icons.Default.Battery5Bar
    this >= 51 -> Icons.Default.Battery4Bar
    this >= 36 -> Icons.Default.Battery3Bar
    this >= 20 -> Icons.Default.Battery2Bar
    this >= 5 -> Icons.Default.Battery1Bar
    else -> Icons.Default.Battery0Bar
}