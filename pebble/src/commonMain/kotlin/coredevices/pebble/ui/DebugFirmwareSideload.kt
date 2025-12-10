package coredevices.pebble.ui

import CoreNav
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.back
import coredevices.pebble.rememberLibPebble
import coredevices.ui.CoreLinearProgressIndicator
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.ConnectedPebbleDeviceInRecovery
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.connection.endpointmanager.FirmwareUpdater
import io.rebble.libpebblecommon.connection.forDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import rememberOpenDocumentLauncher
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

expect fun getTempFwPath(appContext: AppContext): Path

private sealed class UiFirmwareUpdateStatus {
    data object Idle : UiFirmwareUpdateStatus()
    data object Starting : UiFirmwareUpdateStatus()
    data class InProgress(val progress: StateFlow<Float>) : UiFirmwareUpdateStatus()
    data object WaitingForReboot : UiFirmwareUpdateStatus()
    data class Error(val exception: Exception, val message: String) : UiFirmwareUpdateStatus()

    companion object Companion {
        fun fromFirmwareUpdate(status: FirmwareUpdater.FirmwareUpdateStatus?): UiFirmwareUpdateStatus {
            return when (status) {
                null -> Idle
                is FirmwareUpdater.FirmwareUpdateStatus.WaitingToStart -> Starting
                is FirmwareUpdater.FirmwareUpdateStatus.InProgress -> InProgress(status.progress)
                is FirmwareUpdater.FirmwareUpdateStatus.WaitingForReboot -> WaitingForReboot
                is FirmwareUpdater.FirmwareUpdateStatus.NotInProgress.ErrorStarting -> Error(
                    IllegalStateException("Couldn't start: ${status.error.message()}"),
                    "Couldn't start: ${status.error.message()}"
                )

                is FirmwareUpdater.FirmwareUpdateStatus.NotInProgress.Idle -> Idle
            }
        }
    }
}

private val logger = Logger.withTag("WatchFirmwareSideload")

@Composable
fun DebugFirmwareSideload(watchIdentifier: String, coreNav: CoreNav) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val libPebble = rememberLibPebble()
        val appContext = koinInject<AppContext>()
        val scope = rememberCoroutineScope()
        val watchFlow = remember(watchIdentifier) {
            libPebble.watches
                .map { watches ->
                    watches.firstOrNull { watch ->
                        watch.identifier.asString == watchIdentifier
                    }
                }
        }
        val watch by watchFlow.collectAsState(null)
        val snackbarHostState = remember { SnackbarHostState() }
        logger.d("watch = $watch")
        val availableUpdate = (watch as? ConnectedPebble.Firmware)?.firmwareUpdateAvailable
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = coreNav::goBack,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(Res.string.back)
                            )
                        }
                    },
                    title = { Text("Debug Firmware Update") }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            val (updateState, setUpdateState) = remember {
                mutableStateOf<UiFirmwareUpdateStatus>(
                    UiFirmwareUpdateStatus.Idle
                )
            }
            val loop = remember { mutableStateOf(false) }
            val loopPrf = remember { mutableStateOf(false) }
            val loopCount = remember { mutableStateOf(0) }
            val (updateJob, setUpdateJob) = remember { mutableStateOf<Job?>(null) }
            fun doFirmwareUpdate(doUpdate: suspend ConnectedPebble.Firmware.() -> Unit) {
                setUpdateJob(scope.launch {
                    try {
                        val identifier = watch?.identifier
                        if (identifier == null) {
                            logger.d("couldn't start FWUP for $watch")
                            return@launch
                        }
                        while (isActive) {
                            val watchToUpdate =
                                libPebble.connectedWatchFor<ConnectedPebble.Firmware>(identifier = identifier)
                            watchToUpdate.doUpdate()
                            libPebble.watches.forDevice(identifier.asString)
                                .takeWhile { it is ConnectedPebble.Firmware }
                                .filterIsInstance<ConnectedPebble.Firmware>()
                                .collect {
                                    setUpdateState(UiFirmwareUpdateStatus.fromFirmwareUpdate(it.firmwareUpdateState))
                                }
                            setUpdateState(UiFirmwareUpdateStatus.WaitingForReboot)
                            delay(3000)
                            val reconnectedWatch =
                                libPebble.connectedWatchFor<ConnectedPebbleDevice>(identifier = identifier)
                            launch {
                                snackbarHostState.showSnackbar("Update complete")
                            }
                            loopCount.value++
                            if (!loop.value) break // stop if not looping
                            if (loopPrf.value) {
                                reconnectedWatch.resetIntoPrf()
                                delay(3000)
                                libPebble.connectedWatchFor<ConnectedPebbleDeviceInRecovery>(
                                    identifier = identifier
                                )
                            }
                        }
                        setUpdateState(UiFirmwareUpdateStatus.Idle)
                    } catch (e: Exception) {
                        logger.e(e) { "Error during firmware update: ${e.message}" }
                        setUpdateState(
                            UiFirmwareUpdateStatus.Error(
                                e,
                                e.message ?: "Unknown error"
                            )
                        )
                    }
                })
            }

            val launchInstallFirmwareDialog = rememberOpenDocumentLauncher {
                it?.firstOrNull()?.let { file ->
                    val tempFwPath = getTempFwPath(appContext)
                    SystemFileSystem.sink(tempFwPath).use { sink ->
                        file.source.use { source ->
                            source.transferTo(sink)
                        }
                    }
                    doFirmwareUpdate {
                        logger.d { "doFirmwareUpdate: $tempFwPath" }
                        sideloadFirmware(tempFwPath)
                    }
                }
            }
            Column(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (updateState) {
                    is UiFirmwareUpdateStatus.Idle -> {
                        Text(
                            "Idle",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row {
                            Button(onClick = {
                                launchInstallFirmwareDialog(listOf("*/*"))
                            }, modifier = Modifier.padding(5.dp)) {
                                Text("Sideload FW")
                            }
                            Button(
                                onClick = {
                                    if (availableUpdate is FirmwareUpdateCheckResult.FoundUpdate) {
                                        setUpdateState(UiFirmwareUpdateStatus.Starting)
                                        doFirmwareUpdate {
                                            logger.d { "doFirmwareUpdate: $availableUpdate" }
                                            updateFirmware(availableUpdate)
                                        }
                                    } else {
                                        logger.d { "availableUpdate is null" }
                                    }
                                },
                                enabled = availableUpdate is FirmwareUpdateCheckResult.FoundUpdate,
                                modifier = Modifier.padding(5.dp)
                            ) {
                                val text = when {
                                    availableUpdate  is FirmwareUpdateCheckResult.FoundUpdate -> "Update FW to ${availableUpdate.version.stringVersion}"
                                    else -> "No FW available"
                                }
                                Text(text)
                            }
                        }
                    }

                    is UiFirmwareUpdateStatus.Starting -> {
                        Text(
                            "Starting",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    is UiFirmwareUpdateStatus.InProgress -> {
                        val progress by updateState.progress.collectAsState()
                        Text(
                            "In progress: ${(progress * 100).roundToInt()}%",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                        CoreLinearProgressIndicator(
                            progress = { progress }
                        )
                    }

                    is UiFirmwareUpdateStatus.WaitingForReboot -> {
                        Text(
                            "Waiting for reboot",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    is UiFirmwareUpdateStatus.Error -> {
                        Text(
                            "Error:\n${updateState.exception::class.simpleName}: ${updateState.message}",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Button(onClick = { launchInstallFirmwareDialog(listOf("*/*")) }) {
                            Text("Install firmware")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(loop.value, {
                        loop.value = it
                        loopCount.value = 0
                    })
                    Text(
                        "Loop Test",
                        modifier = Modifier
                            .clickable {
                                loop.value = !loop.value
                                loopCount.value = 0
                            }
                            .align(Alignment.CenterVertically),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(loopPrf.value, {
                        loopPrf.value = it
                        loopCount.value = 0
                    })
                    Text(
                        "Loop From PRF",
                        modifier = Modifier
                            .clickable {
                                loopPrf.value = !loopPrf.value
                                loopCount.value = 0
                            }
                            .align(Alignment.CenterVertically),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (updateJob?.isActive == true) {
                    Button(
                        onClick = {
                            updateJob.cancel()
                            setUpdateJob(null)
                            setUpdateState(UiFirmwareUpdateStatus.Idle)
                        }
                    ) {
                        Text("Cancel update")
                    }
                }
                if (loop.value) {
                    Text("Loops completed: ${loopCount.value}")
                }
            }
        }
    }
}

private suspend inline fun <reified T> LibPebble.connectedWatchFor(identifier: PebbleIdentifier): T =
    withTimeout(60.seconds) {
        watches
            .forDevice(identifier.asString)
            .filterIsInstance<T>()
            .first()
    }