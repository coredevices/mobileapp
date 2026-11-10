package io.rebble.libpebblecommon.connection.devconnection

import co.touchlab.kermit.Logger
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.core.writeFully
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.connection.endpointmanager.LastFirmwareUpdateStates
import io.rebble.libpebblecommon.connection.endpointmanager.FirmwareUpdateErrorStarting
import io.rebble.libpebblecommon.connection.endpointmanager.FirmwareUpdateTrigger
import io.rebble.libpebblecommon.connection.endpointmanager.FirmwareUpdater.FirmwareUpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.coroutines.IO

internal expect fun getTempPath(extension: String): Path

abstract class DevConnectionTransport(
    private val libPebble: LibPebble,
    private val lastStates: LastFirmwareUpdateStates,
) {
    companion object {
        private val logger = Logger.withTag("DevConnectionTransport")
    }
    /** @param identifier Identifier of the device to connect to
     * @param inboundPKJSLogs Flow of PKJS logs from the device
     * @param inboundDeviceMessages Flow of messages received from the device, or null if the device disconnected
     * @param outboundDeviceMessages Collector for messages to send to the device
     */
    abstract suspend fun start(
        identifier: PebbleIdentifier,
        inboundPKJSLogs: Flow<String>,
        inboundDeviceMessages: Flow<ByteArray>,
        outboundDeviceMessages: suspend (ByteArray) -> Unit
    )
    abstract suspend fun stop()

    private suspend fun writeTempFile(payload: ByteArray, extension: String): Path {
        val path = getTempPath(extension)
        withContext(Dispatchers.IO) {
            SystemFileSystem.sink(path).buffered().use {
                it.writeFully(payload)
            }
        }
        return path
    }

    protected suspend fun installPBW(payload: ByteArray): Boolean {
        try {
            return libPebble.sideloadApp(writeTempFile(payload, "pbw"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Failed to save/install bundle: ${e.message}" }
            return false
        }
    }

    /** Installs a firmware bundle (pbz) with the firmware updater, reporting to this client. */
    protected suspend fun installFirmware(
        identifier: PebbleIdentifier,
        payload: ByteArray,
        log: suspend (String) -> Unit,
        sendStatus: suspend (Boolean) -> Unit,
    ) {
        suspend fun fail(reason: String) {
            log("Firmware install failed: $reason")
            sendStatus(false)
        }
        val watch = libPebble.watches.value.firstOrNull { it.identifier == identifier } as? ConnectedPebble.Firmware
        if (watch == null) return fail("the watch is not connected")
        if (watch.firmwareUpdateState !is FirmwareUpdateStatus.NotInProgress) {
            return fail("a firmware update is already in progress")
        }
        val path = try {
            writeTempFile(payload, "pbz")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Failed to save firmware: ${e.message}" }
            return fail("couldn't save the bundle")
        }
        lastStates.clear(identifier)
        watch.sideloadFirmware(path, FirmwareUpdateTrigger.DevConnection)
        reportUntilFinished(identifier, log, sendStatus)
    }

    /**
     * Reports a firmware install a client asked for that hasn't been reported yet, to a client that
     * connects mid-install (e.g. after the watch rebooted into recovery) or after it finished.
     */
    protected suspend fun reportFirmwareInstall(
        identifier: PebbleIdentifier,
        log: suspend (String) -> Unit,
        sendStatus: suspend (Boolean) -> Unit,
    ) {
        if (lastStates.state(identifier).first()?.trigger != FirmwareUpdateTrigger.DevConnection) return
        reportUntilFinished(identifier, log, sendStatus)
    }

    private suspend fun reportUntilFinished(
        identifier: PebbleIdentifier,
        log: suspend (String) -> Unit,
        sendStatus: suspend (Boolean) -> Unit,
    ) {
        var reportedPercent = -1
        var reportedPrf = false
        val end = lastStates.state(identifier).transformLatest { state ->
            if (state is FirmwareUpdateStatus.InProgress) {
                state.progress.collect {
                    val percent = (it * 100).toInt() / 10 * 10
                    if (percent > reportedPercent) {
                        reportedPercent = percent
                        log("Firmware install: $percent%")
                    }
                }
            } else {
                emit(state)
            }
        }.first { state ->
            when (state) {
                is FirmwareUpdateStatus.WaitingForReboot -> {
                    if (state.viaPrf && !reportedPrf) {
                        reportedPrf = true
                        log("Rebooting the watch into recovery to install the firmware")
                    }
                    !state.viaPrf
                }
                is FirmwareUpdateStatus.NotInProgress -> true
                is FirmwareUpdateStatus.Active, null -> false
            }
        }
        when (end) {
            is FirmwareUpdateStatus.NotInProgress.ErrorStarting -> log(
                "Firmware install failed: " + when (end.error) {
                    FirmwareUpdateErrorStarting.ErrorDownloading -> "the firmware couldn't be downloaded"
                    FirmwareUpdateErrorStarting.ErrorParsingPbz -> "the bundle has no firmware this watch can install"
                }
            )
            is FirmwareUpdateStatus.NotInProgress.Idle ->
                log("Firmware install failed: ${end.lastFailure?.let { it.message ?: it.toString() }}")
            else -> Unit
        }
        lastStates.clear(identifier)
        sendStatus(end is FirmwareUpdateStatus.WaitingForReboot)
    }
}
