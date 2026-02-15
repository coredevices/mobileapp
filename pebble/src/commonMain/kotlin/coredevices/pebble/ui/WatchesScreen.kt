package coredevices.pebble.ui

import PlatformUiContext
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.devices
import coredevices.pebble.PebbleFeatures
import coredevices.pebble.account.PebbleAccount
import coredevices.pebble.firmware.FirmwareUpdateUiTracker
import coredevices.pebble.firmware.isCoreDevice
import coredevices.pebble.rememberLibPebble
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
import io.rebble.libpebblecommon.connection.DisconnectingPebbleDevice
import io.rebble.libpebblecommon.connection.DiscoveredPebbleDevice
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.connection.KnownPebbleDevice
import io.rebble.libpebblecommon.connection.PebbleDevice
import io.rebble.libpebblecommon.connection.bt.BluetoothState
import io.rebble.libpebblecommon.connection.endpointmanager.FirmwareUpdateErrorStarting
import io.rebble.libpebblecommon.connection.endpointmanager.FirmwareUpdater
import io.rebble.libpebblecommon.connection.endpointmanager.LanguagePackInstallState
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

expect fun scanPermission(): Permission?

expect fun getIPAddress(): Pair<String?, String?>

private val logger = Logger.withTag("WatchesScreen")

@Composable
fun WatchesScreen(navBarNav: NavBarNav, topBarParams: TopBarParams) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val libPebble = rememberLibPebble()
        val scope = rememberCoroutineScope()
        val firmwareUpdateUiTracker = koinInject<FirmwareUpdateUiTracker>()
        val bluetoothEnabled by libPebble.bluetoothEnabled.collectAsState()
        val otherPebbleAppsInstalledFlow =
            remember { libPebble.otherPebbleCompanionAppsInstalled() }
        val otherPebbleAppsInstalled by otherPebbleAppsInstalledFlow.collectAsState()
        val isScanningBle by libPebble.isScanningBle.collectAsState()
        val permissionRequester: PermissionRequester = koinInject()
        val requiredScanPermission = remember { scanPermission() }
        val pebbleFeatures = koinInject<PebbleFeatures>()
        val coreConfigFlow = koinInject<CoreConfigFlow>()
        val coreConfig by coreConfigFlow.flow.collectAsState()
        val showOtherPebbleAppsWarningAndPreventConnection = otherPebbleAppsInstalled.isNotEmpty() && !coreConfig.ignoreOtherPebbleApps
        val companionDevice: CompanionDevice = koinInject()
        val companionDevicePreviouslyCrashed = remember { companionDevice.cdmPreviouslyCrashed() }

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

        val title = stringResource(Res.string.devices)
        val listState = rememberLazyListState()

        LaunchedEffect(Unit) {
            topBarParams.searchAvailable(null)
            topBarParams.actions {
                if (isScanningBle) {
                    TopBarIconButtonWithToolTip(
                        onClick = { libPebble.stopBleScan() },
                        enabled = bluetoothEnabled.enabled(),
                        icon = Icons.Filled.Stop,
                        description = "Stop Scanning",
                    )
                } else {
                    val uiContext = rememberUiContext()
                    if (uiContext != null) {
                        TopBarIconButtonWithToolTip(
                            onClick = { scan(uiContext) },
                            enabled = bluetoothEnabled.enabled(),
                            icon = Icons.Filled.Add,
                            description = "Scan For Watches",
                        )
                    }
                }
            }
            topBarParams.title(title)
            launch {
                topBarParams.scrollToTop.collect {
                    listState.animateScrollToItem(0)
                }
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
                    ElevatedCard(
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 6.dp
                        ),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                            .padding(12.dp),
                    ) {
                        Text(
                            text = "Press + to add a watch",
                            modifier = Modifier
                                .padding(22.dp),
                            textAlign = TextAlign.Center,
                        )
                    }

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
                items(
                    items = watches,
                    key = { it.identifier.asString }
                ) { watch ->
                    WatchItem(
                        watch = watch,
                        bluetoothState = bluetoothEnabled,
                        allowedToConnect = !showOtherPebbleAppsWarningAndPreventConnection,
                        navBarNav = navBarNav,
                    )
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
        is ConnectedPebbleDeviceInRecovery -> "Connected (Recovery)$installingState"
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

@Composable
fun WatchItem(
    watch: PebbleDevice,
    bluetoothState: BluetoothState,
    allowedToConnect: Boolean,
    navBarNav: NavBarNav,
) {
    ListItem(
        modifier = Modifier.clickable {
            navBarNav.navigateTo(PebbleNavBarRoutes.WatchRoute(watch.identifier.asString))
        }.padding(top = 0.dp, bottom = 5.dp, start = 0.dp, end = 0.dp),
        headlineContent = {
            WatchHeader(watch)
        },
        supportingContent = {
            Column {
                WatchDetails(
                    watch = watch,
                    bluetoothState = bluetoothState,
                    allowedToConnect = allowedToConnect,
                    showForget = false,
                    onForget = {},
                )
            }
        },
        trailingContent = {
            Icon(
                Icons.Default.MoreHoriz,
                "Details",
                modifier = Modifier.padding(start = 4.dp, end = 5.dp)
            )
        },
        tonalElevation = 0.2.dp,
        shadowElevation = 4.dp,
    )
}

@Composable
fun WatchHeader(watch: PebbleDevice) {
    val watchColor = remember(watch) {
        when (watch) {
            is KnownPebbleDevice -> watch.color?.color
            else -> null
        }
    }
    Row {
        if (watchColor != null) {
            Box(
                modifier = Modifier.background(watchColor)
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.primary)
                    .height(20.dp)
                    .width(20.dp)
                    .align(Alignment.CenterVertically),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
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
    showForget: Boolean,
    onForget: () -> Unit,
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
    val firmwareVersion = when {
        watch is KnownPebbleDevice -> watch.runningFwVersion
        watch is BleDiscoveredPebbleDevice -> {
            val extInfo = watch.pebbleScanRecord.extendedInfo
            if (extInfo != null) {
                "${extInfo.major}.${extInfo.minor}.${extInfo.patch}"
            } else null
        }

        else -> null
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (firmwareVersion != null) {
            Text(
                text = firmwareVersion,
                fontSize = 12.sp,
                lineHeight = 14.sp,
            )
        }
        if (watch is ConnectedPebble.Battery) {
            val batteryLevel = watch.batteryLevel
            if (batteryLevel != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = batteryLevel.batteryIcon(),
                    contentDescription = "Battery",
                    modifier = Modifier.height(18.dp).align(Alignment.CenterVertically),
                )
                Text(
                    text = "${watch.batteryLevel}%",
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(5.dp))
    val firmwareUpdateAvailable =
        (watch as? ConnectedPebble.Firmware)?.firmwareUpdateAvailable
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
    } else if (loggedIn == null && watch is CommonConnectedDevice && !watch.watchType.isCoreDevice()) {
        PebbleElevatedButton(
            text = "Login to Rebble to check for PebbleOS updates",
            onClick = {
                uriHandler.openUri(REBBLE_LOGIN_URI)
            },
            primaryColor = true,
            modifier = Modifier.padding(5.dp),
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
    FlowRow {
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
        if (showForget) {
            if (watch is KnownPebbleDevice) {
                var showForgetDialog by remember { mutableStateOf(false) }

                Spacer(Modifier.width(10.dp))
                PebbleElevatedButton(
                    onClick = {
                        showForgetDialog = true
                    },
                    enabled = bluetoothState.enabled(),
                    text = "Forget",
                    primaryColor = true,
                    modifier = Modifier.padding(vertical = 5.dp),
                )

                if (showForgetDialog) {
                    AlertDialog(
                        onDismissRequest = { showForgetDialog = false },
                        title = { Text("Forget ${watch.displayName()}?") },
                        text = { Text("Are you sure?") },
                        confirmButton = {
                            TextButton(onClick = {
                                logger.d { "forget: ${watch.identifier}" }
                                watch.forget()
                                onForget()
                                showForgetDialog = false
                            }) { Text("Forget") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showForgetDialog = false
                            }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
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