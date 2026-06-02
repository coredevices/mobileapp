package io.rebble.libpebblecommon.connection

import io.rebble.libpebblecommon.connection.endpointmanager.FirmwareUpdater
import io.rebble.libpebblecommon.connection.endpointmanager.InstalledLanguagePack
import io.rebble.libpebblecommon.connection.endpointmanager.LanguagePackInstallState
import io.rebble.libpebblecommon.connection.endpointmanager.musiccontrol.MusicTrack
import io.rebble.libpebblecommon.image.PebbleBitmap
import io.rebble.libpebblecommon.js.PKJSApp
import io.rebble.libpebblecommon.metadata.WatchColor
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import io.rebble.libpebblecommon.music.MusicAction
import io.rebble.libpebblecommon.music.PlaybackState
import io.rebble.libpebblecommon.music.RepeatType
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.services.WatchInfo
import io.rebble.libpebblecommon.services.appmessage.AppMessageData
import io.rebble.libpebblecommon.services.appmessage.AppMessageResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.files.Path
import kotlin.random.Random
import kotlin.time.Instant
import kotlin.uuid.Uuid

class FakeConnectedDevice(
    override val identifier: PebbleIdentifier,
    override val firmwareUpdateAvailable: FirmwareUpdateCheckState,
    override val firmwareUpdateState: FirmwareUpdater.FirmwareUpdateStatus,
    override val name: String,
    override val nickname: String?,
    override val color: WatchColor = run {
        val white = Random.nextBoolean()
        if (white) {
            WatchColor.Pebble2DuoWhite
        } else {
            WatchColor.Pebble2DuoBlack
        }
    },
    override val watchType: WatchHardwarePlatform = WatchHardwarePlatform.CORE_ASTERIX,
    override val lastConnected: Instant = Instant.DISTANT_PAST,
    override val serial: String = "XXXXXXXXXXXX",
    override val runningFwVersion: String = "v1.2.3-core",
    override val connectionFailureInfo: ConnectionFailureInfo?,
    override val usingBtClassic: Boolean = false,
    override val capabilities: Set<ProtocolCapsFlag> = emptySet()
) : ConnectedPebbleDevice {

    override fun forget() {}
    override fun setNickname(nickname: String?) {
    }

    override fun connect() {}

    override fun disconnect() {}

    override suspend fun sendPing(cookie: UInt): UInt = cookie

    override fun resetIntoPrf() {}

    override fun createCoreDump() {}
    override fun factoryReset() {}

    override suspend fun sendPPMessage(bytes: ByteArray) {}

    override suspend fun sendPPMessage(ppMessage: PebblePacket) {}

    override val inboundMessages: Flow<PebblePacket> = MutableSharedFlow()
    override val rawInboundMessages: Flow<ByteArray> = MutableSharedFlow()

    override fun sideloadFirmware(path: Path) {}

    override fun updateFirmware(update: FirmwareUpdateCheckResult.FoundUpdate) {}

    override fun checkforFirmwareUpdate() {}

    override suspend fun launchApp(uuid: Uuid) {}

    override suspend fun stopApp(uuid: Uuid) {}

    override val runningApp: StateFlow<Uuid?> = MutableStateFlow(null)
    override val watchInfo: WatchInfo = WatchInfo(
        runningFwVersion = FirmwareVersion.from(
            runningFwVersion,
            isRecovery = false,
            gitHash = "",
            timestamp = kotlin.time.Instant.DISTANT_PAST,
            isDualSlot = false,
            isSlot0 = false,
        )!!,
        recoveryFwVersion = FirmwareVersion.from(
            runningFwVersion,
            isRecovery = true,
            gitHash = "",
            timestamp = kotlin.time.Instant.DISTANT_PAST,
            isDualSlot = false,
            isSlot0 = false,
        )!!,
        platform = watchType,
        bootloaderTimestamp = kotlin.time.Instant.DISTANT_PAST,
        board = "board",
        serial = serial,
        btAddress = "11:22:33:44:55:66",
        resourceCrc = -9999999,
        resourceTimestamp = kotlin.time.Instant.DISTANT_PAST,
        language = "en-GB",
        languageVersion = 1,
        capabilities = emptySet(),
        isUnfaithful = false,
        healthInsightsVersion = null,
        javascriptVersion = null,
        color = color,
    )

    override suspend fun updateTime() {}
    override suspend fun updateTimeIfNeeded() {}

    override fun inboundAppMessages(appUuid: Uuid): Flow<AppMessageData> {
        return MutableSharedFlow()
    }

    override val transactionSequence: Iterator<UByte> = iterator { }

    override suspend fun sendAppMessage(appMessageData: AppMessageData): AppMessageResult =
        AppMessageResult.ACK(appMessageData.transactionId)

    override suspend fun sendAppMessageResult(appMessageResult: AppMessageResult) {}

    override suspend fun gatherLogs(): Path? = null

    override suspend fun getCoreDump(unread: Boolean): Path? = null

    override suspend fun updateTrack(track: MusicTrack) {}

    override suspend fun updatePlaybackState(
        state: PlaybackState,
        trackPosMs: UInt,
        playbackRatePct: UInt,
        shuffle: Boolean,
        repeatType: RepeatType
    ) {
    }

    override suspend fun updatePlayerInfo(packageId: String, name: String) {}

    override suspend fun updateVolumeInfo(volumePercent: UByte) {}

    override val musicActions: Flow<MusicAction> = MutableSharedFlow()
    override val updateRequestTrigger: Flow<Unit> = MutableSharedFlow()

    @Deprecated("Use more generic currentCompanionAppSession instead and cast if necessary")
    override val currentPKJSSession: StateFlow<PKJSApp?> = MutableStateFlow(null)
    override val currentCompanionAppSessions: StateFlow<List<CompanionApp>> = MutableStateFlow(emptyList())

    override suspend fun startDevConnection() {}
    override suspend fun stopDevConnection() {}
    override val devConnectionActive: StateFlow<Boolean> = MutableStateFlow(false)
    override val batteryLevel: Int? = 50
    override suspend fun takeScreenshot(): PebbleBitmap {
        // Return an orange square as a placeholder
        val width = 144
        val height = 168
        val pixels = IntArray(width * height) { 0xFFFA4A36.toInt() }
        return PebbleBitmap(width, height, pixels)
    }

    override fun installLanguagePack(path: Path, name: String) {
    }

    override fun installLanguagePack(url: String, name: String) {
    }

    override val languagePackInstallState: LanguagePackInstallState =
        LanguagePackInstallState.Idle()
    override val installedLanguagePack: InstalledLanguagePack? = null

    override suspend fun requestHealthData(fullSync: Boolean): Boolean = true
}
