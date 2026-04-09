package coredevices.ring.ui.screens

import BugReportButton
import CoreNav
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import coredevices.haversine.KMPHaversineSatellite
import coredevices.haversine.KMPHaversineSatelliteManager
import coredevices.haversine.SatelliteStatus
import coredevices.haversine.TransferStatus
import coredevices.haversine.removeDCBias
import coredevices.util.AudioEncoding
import coredevices.indexai.data.entity.RingTransferInfo
import coredevices.indexai.data.entity.createFromTimestamps
import coredevices.ring.data.entity.room.RingDebugTransfer
import coredevices.ring.database.room.dao.RingDebugTransferDao
import coredevices.ring.service.RingBackgroundManager
import coredevices.ring.util.AudioPlayer
import coredevices.ring.util.CompanionRegisterResult
import coredevices.ring.util.RingCompanionDeviceManager
import coredevices.ring.util.rememberPermissionRequestLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.Source
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.seconds

private val HAVERSINE_HW_VERSION = Pair(11, 0)
private val logger = Logger.withTag("RingDebug")
internal expect val BT_PERMISSIONS: List<String>
internal expect suspend fun storeRecording(
    samples: ShortArray,
    sampleRate: Int,
    collectionIdx: Int
): String

internal expect fun openRecording(path: String): Source

@Composable
expect fun rememberHaversineSatelliteManager(hwVersion: Pair<Int, Int>): KMPHaversineSatelliteManager

@Composable
fun RingDebug(coreNav: CoreNav) {
    val scope = rememberCoroutineScope()
    val ringBackgroundManager: RingBackgroundManager = koinInject()
    val ringDebugTransferDao = koinInject<RingDebugTransferDao>()
    val ringCDM = koinInject<RingCompanionDeviceManager>(parameters = { parametersOf(scope) })

    val haversineSatelliteManager = rememberHaversineSatelliteManager(HAVERSINE_HW_VERSION)
    val player = remember { AudioPlayer() }

    val (transferState, setTransferState) = remember { mutableStateOf<TransferStatus?>(null) }
    val ringSyncRunning by ringBackgroundManager.isRunning.collectAsState()
    val transfers by ringDebugTransferDao.getAllFlow()
        .map { it.sortedByDescending { it.transferCompleteTimestamp } }.collectAsState(emptyList())
    val (isScanning, setIsScanning) = remember { mutableStateOf(false) }
    val (stateText, setStateText) = remember { mutableStateOf("Idle") }
    val associations by ringCDM.associations.collectAsState()
    val (connectedSatellite, setConnectedSatellite) = remember {
        mutableStateOf<KMPHaversineSatellite?>(
            null
        )
    }
    val (targetSatellite, setTargetSatellite) = remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val (scannerJob, setScannerJob) = remember { mutableStateOf<Job?>(null) }

    val permissionsLauncher = rememberPermissionRequestLauncher { perms ->
        if (perms.values.all { it }) {
            scope.launch {
                scannerJob?.cancelAndJoin()
                setScannerJob(scope.launch {
                    logger.i("Calling startScanning()")
                    haversineSatelliteManager.startScanning()
                        .onStart { setIsScanning(true) }
                        .onCompletion { setIsScanning(false) }
                        .collect {
                            when (it) {
                                is SatelliteStatus.Transferring -> {
                                    if (it.transferStatus is TransferStatus.TransferComplete) {
                                        removeDCBias((it.transferStatus as TransferStatus.TransferComplete).samples)
                                    }
                                    setTransferState(it.transferStatus)
                                }
                                else -> {}
                            }
                            setConnectedSatellite(it.satellite)
                        }
                    logger.i("Scanning exited")
                })
            }
        } else {
            logger.w { "Permissions not granted: $perms" }
            setStateText("No permissions")
        }
    }
    LaunchedEffect(transferState) {
        val text = when (transferState) {
            null -> "Idle"
            is TransferStatus.TransferStarted -> "Transfer started"
            is TransferStatus.TransferTypeDetermined -> "Transfer type determined"
            is TransferStatus.IrrecoverableDataDetected -> "Irrecoverable data detected"
            is TransferStatus.TransferComplete -> "Transfer Complete"
            is TransferStatus.TransferInProgress -> "Transfer in progress: idx ${transferState.collectionStartIndex}+${transferState.currentCollectionIndex}"
            is TransferStatus.TransferFailed -> "Transfer Failed ${transferState.exception?.let { it::class.simpleName }}"
        }
        setStateText(text)
        transferState.let {
            when (it) {
                is TransferStatus.TransferComplete -> {
                    logger.d { "Transfer state: $it idx ${it.collectionIndex}" }
                    scope.launch(Dispatchers.IO) {
                        try {
                            logger.d { "Storing transfer" }
                            val path =
                                storeRecording(it.samples, it.sampleRate.toInt(), it.collectionIndex)
                            logger.d { "Stored at $path, inserting into db" }
                            val storedId = ringDebugTransferDao.insert(
                                RingDebugTransfer(
                                    id = 0,
                                    satelliteName = it.satellite!!.name,
                                    satelliteId = it.satellite!!.id,
                                    satelliteFirmwareVersion = it.satellite!!.state.filterNotNull()
                                        .first().firmwareVersion,
                                    satelliteLastAdvertisementTimestamp = it.buttonReleaseTimestamp!!,
                                    collectionIndex = it.collectionIndex,
                                    collectionStartCount = it.collectionStartCount,
                                    buttonSequence = it.buttonSequence,
                                    sampleCount = it.samples.size,
                                    sampleRate = it.sampleRate,
                                    buttonReleaseTimestamp = it.buttonReleaseTimestamp,
                                    transferCompleteTimestamp = it.transferCompleteTimestamp,
                                    storedPath = path
                                )
                            )
                            logger.d { "Inserted with id $storedId" }
                        } catch (e: Exception) {
                            logger.e(e) { "Error storing transfer" }
                            setStateText("Error storing transfer")
                        }
                    }
                }

                is TransferStatus.TransferFailed -> {
                    logger.d { "Transfer state: $it: ${it.exception}" }
                }

                else -> {
                    logger.d { "Transfer state: $it" }
                }
            }
        }
    }

    fun showSnackbar(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun targetSatellite(id: String?) {
        scope.launch {
            setTargetSatellite(id)
            if (id != null) {
                permissionsLauncher(BT_PERMISSIONS)
            } else {
                setConnectedSatellite(null)
                scannerJob?.cancel()
                setScannerJob(null)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ring Debug") },
                actions = {
                    BugReportButton(
                        coreNav,
                        pebble = false,
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        LazyColumn(
            modifier = Modifier.padding(it).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { Text(stateText, style = MaterialTheme.typography.titleMedium) }
            if (ringSyncRunning) {
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    Text(
                        "Stop feed service before beginning",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
            item {
                Row {
                    Button(
                        enabled = targetSatellite == null && !isScanning,
                        onClick = {
                            scope.launch {
                                if (ringSyncRunning) {
                                    ringBackgroundManager.stopBackground()
                                    showSnackbar("Stopped feed service")
                                } else {
                                    scannerJob?.cancelAndJoin()
                                    ringBackgroundManager.startBackground()
                                    showSnackbar("Started feed service")
                                }
                            }
                        }
                    ) {
                        if (ringSyncRunning) {
                            Text("Stop feed service")
                        } else {
                            Text("Start feed service")
                        }
                    }
                    Button(
                        enabled = !isScanning && !ringSyncRunning,
                        onClick = {
                            scope.launch {
                                val result = ringCDM.openPairingPicker()
                                logger.d { "Pair picker result: $result" }
                                when (result) {
                                    is CompanionRegisterResult.Failure -> {
                                        showSnackbar("Pair error: ${result.error}")
                                    }

                                    is CompanionRegisterResult.Success -> {
                                        result.id?.let {
                                            targetSatellite(it)
                                        }
                                        showSnackbar("Registered, press ring button to pair")
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Pair new ring")
                    }
                    FilledTonalButton(onClick = {
                        scope.launch {
                            ringCDM.unregisterAll()
                            targetSatellite(null)
                            setConnectedSatellite(null)
                            showSnackbar("Unregistered all rings, unpair in settings")
                        }
                    }) {
                        Text("Unregister all")
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
            item {
                Text("Paired devices:")
            }
            items(associations.size) { i ->
                val association = associations[i]
                ListItem(
                    modifier = Modifier.clickable(enabled = !ringSyncRunning) {
                        if (targetSatellite == association.id) {
                            targetSatellite(null)
                            showSnackbar("Un-targeting ${association.name}")
                        } else {
                            targetSatellite(association.id)
                            showSnackbar("Targeting ${association.name}")
                        }
                    },
                    leadingContent = {
                        Checkbox(targetSatellite == association.id, onCheckedChange = null)
                    },
                    headlineContent = { Text(association.name) },
                    supportingContent = {
                        if (connectedSatellite !== null && connectedSatellite.id == association.id) {
                            val state by connectedSatellite.state.collectAsState(null)
                            state?.let {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (it.isNearby) {
                                        AssistChip({}, { Text("Nearby") })
                                    } else {
                                        val lastSeen = connectedSatellite.lastAdvertisement
                                            ?.timestamp
                                            ?.toLocalDateTime(TimeZone.currentSystemDefault())
                                            ?.time
                                            .toString()
                                        AssistChip({}, { Text("Last seen $lastSeen") })
                                    }
                                    if (it.isInFailsafeMode) {
                                        AssistChip({}, { Text("Failsafe") })
                                    }
                                    AssistChip(
                                        {},
                                        { Text("Count: ${it.truncatedCollectionCount}") })
                                    AssistChip({}, { Text("v${it.firmwareVersion}") })
                                }
                            }
                        }
                    },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
            item {
                Text("Transfers:")
            }
            items(transfers.size, { i -> transfers[i].id }) { i ->
                val transfer = transfers[i]
                val transferInfo = RingTransferInfo.createFromTimestamps(
                    transfer.collectionStartCount.toInt(),
                    transfer.collectionIndex,
                    transfer.buttonReleaseTimestamp?.let { it - (transfer.sampleCount / transfer.sampleRate.toDouble()).seconds },
                    transfer.buttonReleaseTimestamp,
                    transfer.satelliteLastAdvertisementTimestamp,
                    transfer.transferCompleteTimestamp
                )
                ListItem(
                    leadingContent = {
                        Text(
                            "Index\n${transfer.collectionIndex}",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.clickable {
                        val source = openRecording(transfer.storedPath)
                        source.skip(44) // Skip WAV header
                        val sampleRate = transfer.sampleRate
                        logger.d { "Playing ${transfer.transferCompleteTimestamp} sample rate $sampleRate" }
                        player.playRaw(source, sampleRate, AudioEncoding.PCM_16BIT)
                    },
                    headlineContent = {
                        Text(
                            transfer.transferCompleteTimestamp.toLocalDateTime(
                                TimeZone.currentSystemDefault()
                            ).toString()
                        )
                    },
                    supportingContent = {
                        Text(buildString {
                            appendLine(
                                "Button press: ${
                                    transferInfo.buttonPressed?.let {
                                        Instant.fromEpochMilliseconds(
                                            it
                                        )
                                    } ?: "No data"
                                }")
                            appendLine(
                                "Button release: ${
                                    transferInfo.buttonReleased?.let {
                                        Instant.fromEpochMilliseconds(
                                            it
                                        )
                                    } ?: "No data"
                                }")
                            appendLine(
                                "Advertisement received: ${
                                    Instant.fromEpochMilliseconds(
                                        transferInfo.advertisementReceived
                                    )
                                }"
                            )
                            appendLine(
                                "Transfer complete: ${
                                    Instant.fromEpochMilliseconds(
                                        transferInfo.transferCompleted ?: 0L
                                    )
                                }"
                            )
                            appendLine("Collection start count: ${transfer.collectionStartCount}")
                            transferInfo.buttonReleaseAdvertisementLatencyMs?.let {
                                appendLine("Release-Adv Latency: $it ms")
                            }
                            transfer.buttonSequence?.let {
                                appendLine("Button sequence: $it")
                            }
                        })
                    }
                )
            }
        }
    }
}