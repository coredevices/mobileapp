package io.rebble.libpebblecommon.connection.endpointmanager

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.WatchConfigFlow
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.connection.endpointmanager.FirmwareUpdater.FirmwareUpdateStatus
import io.rebble.libpebblecommon.connection.endpointmanager.putbytes.PutBytesSession
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.disk.pbz.PbzFirmware
import io.rebble.libpebblecommon.disk.pbz.findManifestFor
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import io.rebble.libpebblecommon.metadata.pbz.manifest.PbzManifest
import io.rebble.libpebblecommon.metadata.pbz.manifest.PbzManifestWrapper
import io.rebble.libpebblecommon.packets.ObjectType
import io.rebble.libpebblecommon.packets.SystemMessage
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.services.PutBytesService
import io.rebble.libpebblecommon.services.SystemService
import io.rebble.libpebblecommon.util.crc32
import io.rebble.libpebblecommon.web.FirmwareDownloader
import io.rebble.libpebblecommon.web.FirmwareUpdateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.RawSource
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlin.time.Instant

sealed class FirmwareUpdateException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class SafetyCheckFailed(message: String) : FirmwareUpdateException(message)
    class TransferFailed(message: String, cause: Throwable?, val bytesTransferred: UInt) :
        FirmwareUpdateException(message, cause)
}

/** What started a firmware update. */
enum class FirmwareUpdateTrigger {
    AutoUpdate,
    Ui,
    Sideload,
    DevConnection,
}

/** One firmware install: the update, its downloaded or sideloaded bundle, and what started it. */
data class FirmwareInstall(
    val update: FirmwareUpdateCheckResult.FoundUpdate,
    val path: Path,
    val trigger: FirmwareUpdateTrigger,
)

enum class FirmwareUpdateErrorStarting {
    ErrorDownloading,
    ErrorParsingPbz,
}

interface FirmwareUpdater : ConnectedPebble.FirmwareUpdate {
    val firmwareUpdateState: StateFlow<FirmwareUpdateStatus>
    fun init(
        watchPlatform: WatchHardwarePlatform,
        runningSlot: Int?,
        supportsResume: Boolean,
        runningFwVersion: FirmwareVersion,
    )

    sealed class FirmwareUpdateStatus {
        /** What started the update; null on an [NotInProgress.Idle] with no update behind it. */
        abstract val trigger: FirmwareUpdateTrigger?

        sealed class NotInProgress : FirmwareUpdateStatus() {
            data class Idle(
                val lastFailure: Exception? = null,
                override val trigger: FirmwareUpdateTrigger? = null,
            ) : NotInProgress()
            data class ErrorStarting(
                val error: FirmwareUpdateErrorStarting,
                override val trigger: FirmwareUpdateTrigger,
            ) : NotInProgress()
        }

        sealed class Active : FirmwareUpdateStatus() {
            abstract val update: FirmwareUpdateCheckResult.FoundUpdate
            abstract override val trigger: FirmwareUpdateTrigger
        }

        data class WaitingToStart(
            override val update: FirmwareUpdateCheckResult.FoundUpdate,
            override val trigger: FirmwareUpdateTrigger,
        ) : Active()
        data class InProgress(
            override val update: FirmwareUpdateCheckResult.FoundUpdate,
            val progress: StateFlow<Float>,
            override val trigger: FirmwareUpdateTrigger,
        ) : Active()

        /**
         * Won't be in this state for long (we'll be disconnected very soon, at which point no-one
         * is looking at this state). [viaPrf]: rebooting into recovery, which installs the update.
         */
        data class WaitingForReboot(
            override val update: FirmwareUpdateCheckResult.FoundUpdate,
            override val trigger: FirmwareUpdateTrigger,
            val viaPrf: Boolean = false,
        ) : Active()
    }
}

private data class FwupProperties(
    val watchPlatform: WatchHardwarePlatform,
    val updateToSlot: Int?,
    val supportsResume: Boolean,
    val runningFwVersion: FirmwareVersion,
)

/**
 * A firmware install that continues on the watch's next connection. In-memory only: an app restart
 * loses it, but a manual retry still resumes (the watch owns the transfer state).
 */
class PendingFirmwareInstalls {
    enum class Reason {
        /** A disconnection interrupted it: auto-resumed. */
        Interrupted,

        /** Waiting for the watch to reboot into PRF, which installs it. */
        ViaPrf,

        /** Installed directly: the next connection shows whether the watch booted it. */
        AwaitingBoot,

        /** Installed directly, but the watch kept booting its previous build: not tried directly again. */
        DidNotBoot,
    }

    data class Pending(val install: FirmwareInstall, val reason: Reason)

    private val pending = MutableStateFlow<Map<PebbleIdentifier, Pending>>(emptyMap())

    fun record(identifier: PebbleIdentifier, install: FirmwareInstall, reason: Reason = Reason.Interrupted) =
        pending.update { it + (identifier to Pending(install, reason)) }

    fun clear(identifier: PebbleIdentifier) = pending.update { it - identifier }

    fun get(identifier: PebbleIdentifier): Pending? = pending.value[identifier]
}

private data class ResumeOffsets(
    val firmware: UInt,
    val resources: UInt,
) {
    val total: UInt get() = firmware + resources
}

class RealFirmwareUpdater(
    private val identifier: PebbleIdentifier,
    private val systemService: SystemService,
    private val putBytesSession: PutBytesSession,
    private val firmwareDownloader: FirmwareDownloader,
    private val connectionCoroutineScope: ConnectionCoroutineScope,
    private val firmwareUpdateManager: FirmwareUpdateManager,
    private val pendingInstalls: PendingFirmwareInstalls,
    private val lastStates: LastFirmwareUpdateStates,
    private val watchConfig: WatchConfigFlow,
) : FirmwareUpdater {
    private val logger = Logger.withTag("FWUpdate-$identifier")
    private var props: FwupProperties? = null
    private val _firmwareUpdateState =
        MutableStateFlow<FirmwareUpdateStatus>(FirmwareUpdateStatus.NotInProgress.Idle())
    override val firmwareUpdateState: StateFlow<FirmwareUpdateStatus> =
        _firmwareUpdateState.asStateFlow()

    override fun init(
        watchPlatform: WatchHardwarePlatform,
        runningSlot: Int?,
        supportsResume: Boolean,
        runningFwVersion: FirmwareVersion,
    ) {
        val updateToSlot = when (runningSlot) {
            0 -> 1
            1 -> 0
            else -> null
        }
        props = FwupProperties(watchPlatform, updateToSlot, supportsResume, runningFwVersion)
        connectionCoroutineScope.launch {
            _firmwareUpdateState.collect {
                if (it != FirmwareUpdateStatus.NotInProgress.Idle()) lastStates.update(identifier, it)
            }
        }
        if (!continuePending(runningFwVersion)) maybeAutoResume(runningFwVersion)
    }

    /** Picks up an install that was waiting for this connection; false if it leaves room to auto-resume. */
    private fun continuePending(runningFwVersion: FirmwareVersion): Boolean {
        val pending = pendingInstalls.get(identifier)
        when (pending?.reason) {
            PendingFirmwareInstalls.Reason.ViaPrf -> {
                pendingInstalls.clear(identifier)
                if (runningFwVersion.isRecovery) {
                    continueInPrf(pending.install)
                } else {
                    _firmwareUpdateState.value = FirmwareUpdateStatus.NotInProgress.Idle(
                        IllegalStateException("The watch didn't reboot into recovery to install the update"),
                        pending.install.trigger,
                    )
                }
                return true
            }
            PendingFirmwareInstalls.Reason.AwaitingBoot -> {
                val target = pending.install.update.version
                if (!runningFwVersion.isRecovery && !runningFwVersion.isSameBuildAs(target)) {
                    logger.w { "Watch didn't boot ${target.stringVersion}; won't install it directly again" }
                    pendingInstalls.record(identifier, pending.install, PendingFirmwareInstalls.Reason.DidNotBoot)
                } else {
                    pendingInstalls.clear(identifier)
                }
            }
            PendingFirmwareInstalls.Reason.Interrupted, PendingFirmwareInstalls.Reason.DidNotBoot, null -> Unit
        }
        return false
    }

    private fun routeFor(pbz: PbzFirmware, fwupProps: FwupProperties): InstallRoute? = try {
        installRoute(pbz.manifests, fwupProps.runningFwVersion, fwupProps.updateToSlot) {
            installsDirectly(fwupProps.runningFwVersion, it, pendingInstalls.get(identifier))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.w(e) { "Failed to read firmware bundle: ${e.message}" }
        null
    }

    private fun continueInPrf(install: FirmwareInstall) {
        connectionCoroutineScope.launch {
            val fwupProps = props ?: return@launch
            if (!tryStartUpdateMutex(install.update, install.trigger)) return@launch
            val route = routeFor(PbzFirmware(install.path), fwupProps)
            if (route !is InstallRoute.Direct) {
                _firmwareUpdateState.value = FirmwareUpdateStatus.NotInProgress.ErrorStarting(
                    FirmwareUpdateErrorStarting.ErrorParsingPbz, install.trigger)
                return@launch
            }
            logger.i { "Installing ${install.update.version.stringVersion} from PRF" }
            pendingInstalls.record(identifier, install)
            beginFirmwareUpdate(route.manifest, install, fwupProps)
        }
    }

    /**
     * Resume an update which a disconnection interrupted - but only if both sides agree: the watch
     * isn't already running it, and the watch reports partial transfer state (which must also
     * CRC-match the pbz before anything is sent - see [beginFirmwareUpdate]).
     */
    private fun maybeAutoResume(runningFwVersion: FirmwareVersion) {
        val fwupProps = props ?: return
        val interrupted = pendingInstalls.get(identifier)
            ?.takeIf { it.reason == PendingFirmwareInstalls.Reason.Interrupted }?.install ?: return
        if (!watchConfig.value.autoResumeFirmwareUpdate || !fwupProps.supportsResume) return
        if (runningFwVersion >= interrupted.update.version) {
            logger.d { "Not auto-resuming: watch already running ${runningFwVersion.stringVersion}" }
            pendingInstalls.clear(identifier)
            return
        }
        connectionCoroutineScope.launch {
            val status = systemService.requestFirmwareUpdateStatus()
            val watchResumable = status != null &&
                    (status.firmwareBytesWritten.get() > 0u || status.resourcesBytesWritten.get() > 0u)
            if (!watchResumable) {
                logger.d { "Not auto-resuming: watch has no resumable update state" }
                pendingInstalls.clear(identifier)
                return@launch
            }
            logger.i { "Auto-resuming interrupted firmware update to ${interrupted.update.version.stringVersion}" }
            startUpdate(interrupted.update, interrupted.trigger, requireResume = true)
        }
    }

    private fun performSafetyChecks(manifest: PbzManifestWrapper, fwupProps: FwupProperties) {
        val watchPlatform = fwupProps.watchPlatform
        val firmware = manifest.manifest.firmware
        val resources = manifest.manifest.resources
        val isRecoveryFirmware = firmware.type == "recovery"
        when {
            firmware.type != "normal" && !isRecoveryFirmware ->
                throw FirmwareUpdateException.SafetyCheckFailed("Invalid firmware type: ${firmware.type}")

            firmware.crc <= 0L ->
                throw FirmwareUpdateException.SafetyCheckFailed("Invalid firmware CRC: ${firmware.crc}")

            firmware.size <= 0 ->
                throw FirmwareUpdateException.SafetyCheckFailed("Invalid firmware size: ${firmware.size}")

            resources != null && resources.size <= 0 ->
                throw FirmwareUpdateException.SafetyCheckFailed("Invalid resources size: ${resources.size}")

            resources != null && resources.crc <= 0L ->
                throw FirmwareUpdateException.SafetyCheckFailed("Invalid resources CRC: ${resources.crc}")

            watchPlatform != firmware.hwRev ->
                throw FirmwareUpdateException.SafetyCheckFailed("Firmware board does not match watch board: ${firmware.hwRev} != $watchPlatform")

            fwupProps.updateToSlot != null && fwupProps.updateToSlot != firmware.slot && !isRecoveryFirmware ->
                throw FirmwareUpdateException.SafetyCheckFailed("Firmware slot (${firmware.slot}) does not match watch slot: (${fwupProps.updateToSlot})")
        }
        checkCrc("Firmware", manifest.getFirmware(), firmware.size, firmware.crc)
        resources?.let { checkCrc("Resources", manifest.getResources()!!, it.size, it.crc) }
    }

    private fun checkCrc(name: String, source: RawSource, size: Long, expectedCrc: Long) {
        val actual = source.buffered().use { it.crc32(size) }
        if (actual != expectedCrc.toUInt()) {
            throw FirmwareUpdateException.SafetyCheckFailed(
                "$name CRC does not match manifest: expected $expectedCrc, got $actual"
            )
        }
    }

    private suspend fun sendFirmwareParts(
        manifest: PbzManifestWrapper,
        resume: ResumeOffsets,
        install: FirmwareInstall,
    ) {
        val (update, _, trigger) = install
        var totalSent = resume.total
        val firmware = manifest.manifest.firmware
        val resources = manifest.manifest.resources
        var firmwareCookie: UInt? = null
        val progessFlow = MutableStateFlow(0.0f)
        try {
            sendFirmware(manifest, resume.firmware).collect {
                when (it) {
                    is PutBytesSession.SessionState.Open -> {
                        logger.d { "PutBytes session opened for firmware" }
                        _firmwareUpdateState.value =
                            FirmwareUpdateStatus.InProgress(update, progessFlow, trigger)
                    }

                    is PutBytesSession.SessionState.Sending -> {
                        totalSent = resume.firmware + it.totalSent
                        val progress =
                            (totalSent.toFloat() / firmware.size) / 2.0f
                        logger.i { "Firmware update progress: $progress (putbytes cookie: ${it.cookie})" }
                        progessFlow.emit(progress)
                    }

                    is PutBytesSession.SessionState.Finished -> {
                        firmwareCookie = it.cookie
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                logger.d { "Firmware transfer cancelled" }
                throw e
            } else {
                throw FirmwareUpdateException.TransferFailed(
                    "Failed to transfer firmware",
                    e,
                    totalSent
                )
            }
        }
        logger.d { "Completed firmware transfer" }
        var resourcesCookie: UInt? = null
        resources?.let { res ->
            try {
                sendResources(manifest, resume.resources).collect {
                    when (it) {
                        is PutBytesSession.SessionState.Open -> {
                            logger.d { "PutBytes session opened for resources" }
                            progessFlow.emit(0.5f)
                        }

                        is PutBytesSession.SessionState.Sending -> {
                            val resourcesSent = resume.resources + it.totalSent
                            totalSent = firmware.size.toUInt() + resourcesSent
                            val progress =
                                0.5f + ((resourcesSent.toFloat() / res.size.toFloat()) / 2.0f)
                            logger.i { "Resources update progress: $progress (putbytes cookie: ${it.cookie})" }
                            progessFlow.emit(progress)
                        }

                        is PutBytesSession.SessionState.Finished -> {
                            resourcesCookie = it.cookie
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw FirmwareUpdateException.TransferFailed(
                    "Failed to transfer resources",
                    e,
                    totalSent
                )
            }
            logger.d { "Completed resources transfer" }
        } ?: logger.d { "No resources to send, resource PutBytes skipped" }

        // Install both right at the end after all transfers are complete (i.e. don't install
        // one without both having been successfully transferred).
        firmwareCookie?.let { putBytesSession.sendInstall(it) }
        resourcesCookie?.let { putBytesSession.sendInstall(it) }
    }

    private suspend fun tryStartUpdateMutex(
        update: FirmwareUpdateCheckResult.FoundUpdate,
        trigger: FirmwareUpdateTrigger,
    ): Boolean {
        startMutex.withLock {
            if (_firmwareUpdateState.value !is FirmwareUpdateStatus.NotInProgress) {
                logger.w { "Firmware update already in progress!" }
                return false
            }
            _firmwareUpdateState.value = FirmwareUpdateStatus.WaitingToStart(update, trigger)
            return true
        }
    }

    override fun sideloadFirmware(path: Path, trigger: FirmwareUpdateTrigger) {
        connectionCoroutineScope.launch {
            val fwupProps = props
            if (fwupProps == null) {
                throw FirmwareUpdateException.SafetyCheckFailed("FirmwareUpdater not initialized")
            }
            val route = routeFor(PbzFirmware(path), fwupProps)
            val updateToVersion = route?.manifest?.manifest?.asFirmwareVersion()
            if (updateToVersion == null) {
                logger.w { "No firmware this watch can install in $path" }
                _firmwareUpdateState.value = FirmwareUpdateStatus.NotInProgress.ErrorStarting(
                    FirmwareUpdateErrorStarting.ErrorParsingPbz, trigger)
                return@launch
            }
            val update = FirmwareUpdateCheckResult.FoundUpdate(
                version = updateToVersion,
                url = "",
                notes = "Sideloaded",
                installsDirectly = route is InstallRoute.Direct,
            )
            logger.d { "sideloadFirmware path: $path" }
            if (!tryStartUpdateMutex(update, trigger)) {
                return@launch
            }
            install(route, FirmwareInstall(update, path, trigger), fwupProps)
        }
    }

    override fun updateFirmware(update: FirmwareUpdateCheckResult.FoundUpdate, trigger: FirmwareUpdateTrigger) {
        startUpdate(update, trigger, requireResume = false)
    }

    private fun startUpdate(
        update: FirmwareUpdateCheckResult.FoundUpdate,
        trigger: FirmwareUpdateTrigger,
        requireResume: Boolean,
    ) {
        connectionCoroutineScope.launch {
            logger.d { "updateFirmware: $update" }
            val fwupProps = props
            if (fwupProps == null) {
                throw FirmwareUpdateException.SafetyCheckFailed("FirmwareUpdater not initialized")
            }
            if (!tryStartUpdateMutex(update, trigger)) {
                return@launch
            }
            val path = reusableDownloadedPbz(update) ?: run {
                val path = firmwareDownloader.downloadFirmware(update.url, "pbz")
                if (path == null) {
                    _firmwareUpdateState.value = FirmwareUpdateStatus.NotInProgress.ErrorStarting(
                        FirmwareUpdateErrorStarting.ErrorDownloading, trigger)
                    return@launch
                }
                try {
                    PbzFirmware(path).manifests
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(e) { "Failed to parse firmware: ${e.message}" }
                    _firmwareUpdateState.value = FirmwareUpdateStatus.NotInProgress.ErrorStarting(
                        FirmwareUpdateErrorStarting.ErrorParsingPbz, trigger)
                    return@launch
                }
                // Remember the attempt (only once the file is downloaded and parseable, so a
                // failed download doesn't trigger a retry on the next connection). Cleared when
                // the update completes; anything interrupting it can auto-resume on reconnect.
                pendingInstalls.record(identifier, FirmwareInstall(update, path, trigger))
                path
            }
            val route = routeFor(PbzFirmware(path), fwupProps)
            if (route == null) {
                _firmwareUpdateState.value = FirmwareUpdateStatus.NotInProgress.ErrorStarting(
                    FirmwareUpdateErrorStarting.ErrorParsingPbz, trigger)
                return@launch
            }
            if (requireResume && route !is InstallRoute.Direct) {
                logger.i { "Not auto-resuming: the update now needs PRF" }
                pendingInstalls.clear(identifier)
                _firmwareUpdateState.value = FirmwareUpdateStatus.NotInProgress.Idle()
                return@launch
            }
            install(route, FirmwareInstall(update, path, trigger), fwupProps, requireResume)
        }
    }

    private suspend fun install(
        route: InstallRoute,
        install: FirmwareInstall,
        fwupProps: FwupProperties,
        requireResume: Boolean = false,
    ) = when (route) {
        is InstallRoute.Direct -> beginFirmwareUpdate(route.manifest, install, fwupProps, requireResume)
        is InstallRoute.ViaPrf -> rebootIntoPrfToInstall(install)
    }

    /**
     * The pbz from an earlier attempt at the same build, if it's still on disk and really contains
     * the expected version - so a resume or retry doesn't re-download it.
     */
    private fun reusableDownloadedPbz(update: FirmwareUpdateCheckResult.FoundUpdate): Path? {
        // By build: each update check returns a newly signed URL for the same update.
        val recorded = pendingInstalls.get(identifier)?.install
            ?.takeIf { it.update.version.isSameBuildAs(update.version) } ?: return null
        return try {
            val versions = PbzFirmware(recorded.path).manifests.map { it.manifest.asFirmwareVersion() }
            // Not equals(): the update-check version has a placeholder timestamp
            if (versions.isEmpty() || versions.any { it == null || !it.sameVersionNumberAs(update.version) }) {
                logger.w { "Previously-downloaded pbz isn't ${update.version.stringVersion} (contains ${versions.map { it?.stringVersion }}); re-downloading" }
                return null
            }
            logger.d { "Reusing already-downloaded pbz: ${recorded.path}" }
            recorded.path
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Previously-downloaded pbz unusable; re-downloading" }
            null
        }
    }

    /** Nothing is transferred yet: [continueInPrf] installs it once the watch reconnects in PRF. */
    private fun rebootIntoPrfToInstall(install: FirmwareInstall) {
        logger.i { "Installing ${install.update.version.stringVersion} from PRF: rebooting watch into PRF" }
        pendingInstalls.record(identifier, install, PendingFirmwareInstalls.Reason.ViaPrf)
        _firmwareUpdateState.value = FirmwareUpdateStatus.WaitingForReboot(install.update, install.trigger, viaPrf = true)
        systemService.resetIntoPrf()
    }

    override fun checkforFirmwareUpdate(force: Boolean) {
        firmwareUpdateManager.checkForUpdates(force)
    }

    private val startMutex = Mutex()

    private suspend fun beginFirmwareUpdate(
        manifest: PbzManifestWrapper,
        install: FirmwareInstall,
        fwupProps: FwupProperties,
        requireResume: Boolean = false,
    ) {
        logger.d { "beginFirmwareUpdate" }
        val (update, _, trigger) = install
        try {
            val totalBytes = manifest.manifest.firmware.size + (manifest.manifest.resources?.size ?: 0)
            logger.d { "Loading firmware for slot ${fwupProps.updateToSlot}" }
            require(totalBytes > 0) { "Firmware size is 0" }
            performSafetyChecks(manifest, fwupProps)
            val resume = if (fwupProps.supportsResume) {
                determineResumeOffsets(manifest)
            } else {
                ResumeOffsets(0u, 0u)
            }
            if (requireResume && resume.total == 0u) {
                logger.i { "Not auto-resuming: watch state doesn't match this update" }
                pendingInstalls.clear(identifier)
                _firmwareUpdateState.value = FirmwareUpdateStatus.NotInProgress.Idle()
                return
            }
            val result = systemService.sendFirmwareUpdateStart(
                bytesAlreadyTransferred = resume.total,
                bytesToSend = totalBytes.toUInt() - resume.total,
            )
            if (result != SystemMessage.FirmwareUpdateStartStatus.Started) {
                error("Failed to start firmware update: $result")
            }
            sendFirmwareParts(manifest, resume, install)
            logger.d { "Firmware update completed, waiting for reboot" }
            val running = fwupProps.runningFwVersion
            if (running.isDualSlot && !running.isRecovery) {
                pendingInstalls.record(identifier, install, PendingFirmwareInstalls.Reason.AwaitingBoot)
            } else {
                pendingInstalls.clear(identifier)
            }
            _firmwareUpdateState.value = FirmwareUpdateStatus.WaitingForReboot(update, trigger)
            systemService.sendFirmwareUpdateComplete()
            return
        } catch (e: IllegalArgumentException) {
            logger.e(e) { "Firmware update failed: ${e.message}" }
           _firmwareUpdateState.value = FirmwareUpdateStatus.NotInProgress.Idle(e, trigger)
        } catch (e: PutBytesService.PutBytesException) {
            logger.e(e) { "Firmware update failed: ${e.message}" }
           _firmwareUpdateState.value = FirmwareUpdateStatus.NotInProgress.Idle(e, trigger)
        } catch (e: FirmwareUpdateException) {
            logger.e(e) { "Firmware update failed: ${e.message}" }
           _firmwareUpdateState.value = FirmwareUpdateStatus.NotInProgress.Idle(e, trigger)
        } catch (e: CancellationException) {
           _firmwareUpdateState.value =
              FirmwareUpdateStatus.NotInProgress.ErrorStarting(FirmwareUpdateErrorStarting.ErrorDownloading, trigger)
           throw e
        } catch (e: IllegalStateException) {
            logger.e(e) { "Firmware update failed: ${e.message}" }
           _firmwareUpdateState.value = FirmwareUpdateStatus.NotInProgress.Idle(e, trigger)
        } catch (e: Exception) {
            logger.e(e) { "Firmware update failed (unknown): ${e.message}" }
           _firmwareUpdateState.value = FirmwareUpdateStatus.NotInProgress.Idle(e, trigger)
        }
    }

    private suspend fun determineResumeOffsets(manifest: PbzManifestWrapper): ResumeOffsets {
        val status = systemService.requestFirmwareUpdateStatus()
        if (status == null) {
            logger.d { "No firmware update status from watch; starting from scratch" }
            return ResumeOffsets(0u, 0u)
        }
        val firmwareOffset = validatedResumeOffset(
            bytesWritten = status.firmwareBytesWritten.get(),
            reportedCrc = status.firmwareCrc.get(),
            objectSize = manifest.manifest.firmware.size.toUInt(),
        ) { manifest.getFirmware().buffered() }
        // Only resume resources if firmware is also resuming (a genuinely interrupted transfer) -
        // the resources region can hold a stale-but-valid pack from a previous completed update,
        // and "resuming" off that is confusing.
        val resourcesOffset = if (firmwareOffset == 0u) {
            0u
        } else {
            manifest.manifest.resources?.let { res ->
                validatedResumeOffset(
                    bytesWritten = status.resourcesBytesWritten.get(),
                    reportedCrc = status.resourcesCrc.get(),
                    objectSize = res.size.toUInt(),
                ) { manifest.getResources()!!.buffered() }
            } ?: 0u
        }
        logger.i { "Resume offsets: firmware=$firmwareOffset resources=$resourcesOffset (watch reported $status)" }
        return ResumeOffsets(firmwareOffset, resourcesOffset)
    }

    private fun sendFirmware(
        manifest: PbzManifestWrapper,
        skip: UInt = 0u,
    ): Flow<PutBytesSession.SessionState> {
        val firmware = manifest.manifest.firmware
        val source = manifest.getFirmware().buffered()
        if (skip > 0u) {
            source.skip(skip.toLong())
        }
        return putBytesSession.beginSession(
            size = firmware.size.toUInt(),
            type = when (firmware.type) {
                "normal" -> ObjectType.FIRMWARE
                "recovery" -> ObjectType.RECOVERY
                else -> error("Unknown firmware type: ${firmware.type}")
            },
            bank = 0u,
            filename = "",
            source = source,
            sendInstall = false,
            resumeOffset = skip,
            objectCrc = firmware.crc.toUInt(),
        ).onCompletion { source.close() } // Can't do use block because of the flow
    }

    private fun sendResources(
        manifest: PbzManifestWrapper,
        skip: UInt = 0u,
    ): Flow<PutBytesSession.SessionState> {
        val resources = manifest.manifest.resources
            ?: throw IllegalArgumentException("Resources not found in firmware manifest")
        require(resources.size > 0) { "Resources size is 0" }
        val source = manifest.getResources()!!.buffered()
        if (skip > 0u) {
            source.skip(skip.toLong())
        }
        return putBytesSession.beginSession(
            size = resources.size.toUInt(),
            type = ObjectType.SYSTEM_RESOURCE,
            bank = 0u,
            filename = "",
            source = source,
            sendInstall = false,
            resumeOffset = skip,
            objectCrc = resources.crc.toUInt(),
        ).onCompletion { source.close() }
    }
}

/**
 * The offset to resume an object transfer from: the watch-reported offset if our local copy of the
 * object matches the watch-reported CRC up to that offset, otherwise zero (restart from scratch).
 */
internal fun validatedResumeOffset(
    bytesWritten: UInt,
    reportedCrc: UInt,
    objectSize: UInt,
    source: () -> Source,
): UInt {
    // The watch derives this by scanning its flash bank for the last written byte, and deliberately
    // reports one less than it holds, so it can never say an object is complete: objectSize - 1 is
    // a fully-written bank (e.g. left behind by a previous completed update), not a partial one.
    if (bytesWritten == 0u || (bytesWritten + 1u) >= objectSize) return 0u
    val localCrc = source().let { src ->
        try {
            src.crc32(bytesWritten.toLong())
        } finally {
            src.close()
        }
    }
    return if (localCrc == reportedCrc) bytesWritten else 0u
}

private fun FirmwareVersion.sameVersionNumberAs(other: FirmwareVersion): Boolean =
    major == other.major && minor == other.minor && patch == other.patch

fun PbzManifest.asFirmwareVersion(): FirmwareVersion? {
    val versionTag = firmware.versionTag
    if (versionTag == null) {
        Logger.w { "Firmware version tag is null" }
        return null
    }
    return FirmwareVersion.from(
        tag = versionTag,
        isRecovery = firmware.type == "recovery",
        gitHash = "",
        timestamp = Instant.fromEpochMilliseconds(firmware.timestamp),
        isDualSlot = firmware.slot != null,
        isSlot0 = firmware.slot == 0,
    )
}
