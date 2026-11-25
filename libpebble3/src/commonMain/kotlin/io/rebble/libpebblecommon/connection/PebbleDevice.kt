package io.rebble.libpebblecommon.connection

import io.rebble.libpebblecommon.connection.bt.ble.pebble.PebbleLeScanRecord
import io.rebble.libpebblecommon.connection.bt.ble.pebble.ReversePpogVersion
import io.rebble.libpebblecommon.connection.endpointmanager.FirmwareUpdateTrigger
import io.rebble.libpebblecommon.connection.endpointmanager.FirmwareUpdater
import io.rebble.libpebblecommon.connection.endpointmanager.InstalledLanguagePack
import io.rebble.libpebblecommon.connection.endpointmanager.LanguagePackInstallState
import io.rebble.libpebblecommon.connection.endpointmanager.WatchLanguagePack
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
import io.rebble.libpebblecommon.services.WatchInfo
import io.rebble.libpebblecommon.services.appmessage.AppMessageData
import io.rebble.libpebblecommon.services.appmessage.AppMessageResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.files.Path
import kotlin.time.Instant
import kotlin.uuid.Uuid


public interface ActiveDevice {
    public fun disconnect()
    public val usingBtClassic: Boolean
    public val reversePpogVersion: ReversePpogVersion?
}

public data class ConnectionFailureInfo internal constructor(
    val reason: ConnectionFailureReason,
    val times: Int,
)

// <T : Transport> ?
public sealed interface PebbleDevice {
    public val identifier: PebbleIdentifier
    public val name: String
    public val nickname: String?

    /**
     * Information about previous connection failures (until successfully connected).
     */
    public val connectionFailureInfo: ConnectionFailureInfo?

    public fun connect()
    public fun displayName(): String {
        val actualNickname = nickname
        if (actualNickname != null && actualNickname.isNotEmpty()) {
            return actualNickname
        } else {
            return name
        }
    }
}

public interface DiscoveredPebbleDevice : PebbleDevice

// We know a few more things about these, after a BLE scan but before connection
public interface BleDiscoveredPebbleDevice : DiscoveredPebbleDevice {
    public val pebbleScanRecord: PebbleLeScanRecord
    public val rssi: Int
}

// e.g. we have previously connected to it, and got all it's info (stored in the db)
public interface KnownPebbleDevice : PebbleDevice {
    public val runningFwVersion: String
    public val serial: String
    public val lastConnected: Instant
    public val watchType: WatchHardwarePlatform
    public val color: WatchColor?
    public val capabilities: Set<ProtocolCapsFlag>
    public fun forget()
    public fun setNickname(nickname: String?)
}

public interface DisconnectingPebbleDevice : PebbleDevice {
    public val color: WatchColor?
}
internal interface DisconnectingKnownPebbleDevice : DisconnectingPebbleDevice, KnownPebbleDevice

public interface ConnectingPebbleDevice : PebbleDevice, ActiveDevice {
    public val color: WatchColor?
    public val negotiating: Boolean
    /** Set while reconnecting after the watch rebooted for a firmware update. */
    public val rebootingForFirmwareUpdate: FirmwareUpdater.FirmwareUpdateStatus.WaitingForReboot?
}
internal interface ConnectingKnownPebbleDevice : ConnectingPebbleDevice, KnownPebbleDevice

public interface ConnectedWatchInfo {
    public val watchInfo: WatchInfo
}

public interface CommonConnectedDevice : KnownPebbleDevice,
    ActiveDevice,
    ConnectedPebble.Firmware,
    ConnectedWatchInfo,
    ConnectedPebble.Logs,
    ConnectedPebble.CoreDump,
    ConnectedPebble.Battery,
    ConnectedPebble.DevConnection

public interface ConnectedPebbleDeviceInRecovery : CommonConnectedDevice

public sealed interface ConnectedPebbleDevice :
    CommonConnectedDevice,
    KnownPebbleDevice,
    ConnectedPebble.Debug,
    ConnectedPebble.Messages,
    ConnectedPebble.AppRunState,
    ConnectedPebble.Time,
    ConnectedPebble.AppMessages,
    ConnectedPebble.Music,
    ConnectedPebble.PKJS,
    ConnectedPebble.CompanionAppControl,
    ConnectedPebble.Screenshot,
    ConnectedPebble.Language,
    ConnectedPebble.Health

/**
 * Put all specific functionality here, rather than directly in [ConnectedPebbleDevice].
 *
 * Eventually, implementations of these interfaces should all be what we're currently calling
 * "Endpoint Managers". For now, "Services" are OK.
 */
public object ConnectedPebble {
    public interface AppMessages {
        public val transactionSequence: Iterator<UByte>
        public suspend fun sendAppMessage(appMessageData: AppMessageData): AppMessageResult
        public suspend fun sendAppMessageResult(appMessageResult: AppMessageResult)
        public fun inboundAppMessages(appUuid: Uuid): Flow<AppMessageData>
    }

    public interface Debug {
        public suspend fun sendPing(cookie: UInt): UInt
        public fun resetIntoPrf()
        public fun createCoreDump()
        public fun factoryReset()
    }

    public interface DevConnection {
        /** [forceLan] ignores the lanDevConnection config and always uses the LAN server. */
        public suspend fun startDevConnection(forceLan: Boolean = false)
        public suspend fun stopDevConnection()
        public val devConnectionActive: StateFlow<Boolean>
        public val devConnectionError: StateFlow<String?>
    }

    public interface Screenshot {
        public suspend fun takeScreenshot(): PebbleBitmap?
    }

    public interface Logs {
        public suspend fun gatherLogs(): Path?
    }

    public interface Messages {
        public suspend fun sendPPMessage(bytes: ByteArray)
        public suspend fun sendPPMessage(ppMessage: PebblePacket)
        public val inboundMessages: Flow<PebblePacket>
        public val rawInboundMessages: Flow<ByteArray>
    }

    public interface FirmwareUpdate {
        public fun sideloadFirmware(path: Path, trigger: FirmwareUpdateTrigger)
        public fun updateFirmware(update: FirmwareUpdateCheckResult.FoundUpdate, trigger: FirmwareUpdateTrigger)
        public fun checkforFirmwareUpdate(force: Boolean)
    }

    public interface FirmwareStatus {
        public val firmwareUpdateState: FirmwareUpdater.FirmwareUpdateStatus
        public val firmwareUpdateAvailable: FirmwareUpdateCheckState
    }

    public interface Firmware : FirmwareUpdate, FirmwareStatus

    public interface Battery {
        public val batteryLevel: Int?
    }

    public interface AppRunState {
        public suspend fun launchApp(uuid: Uuid)
        public suspend fun stopApp(uuid: Uuid)
        public val runningApp: StateFlow<Uuid?>
        /** The app that ran before the one running now (or last), since connecting. */
        public val previousApp: StateFlow<Uuid?>
    }

    public interface PKJS {
        @Deprecated("Use more generic currentCompanionAppSession instead and cast if necessary")
        public val currentPKJSSession: StateFlow<PKJSApp?>
    }

    public interface CompanionAppControl {
        public val currentCompanionAppSessions: StateFlow<List<CompanionApp>>
    }

    public interface Time {
        public suspend fun updateTime()
        public suspend fun updateTimeIfNeeded()
    }

    public interface CoreDump {
        public suspend fun getCoreDump(unread: Boolean): Path?
    }

    public interface Music {
        public suspend fun updateTrack(track: MusicTrack)
        public suspend fun updatePlaybackState(
            state: PlaybackState,
            trackPosMs: UInt,
            playbackRatePct: UInt,
            shuffle: Boolean,
            repeatType: RepeatType,
            skipSeeksWithinTrack: Boolean,
        )
        public suspend fun updatePlayerInfo(packageId: String, name: String)
        public suspend fun updateVolumeInfo(volumePercent: UByte)
        public val musicActions: Flow<MusicAction>
        public val updateRequestTrigger: Flow<Unit>
    }

    public interface LanguageInstall {
        public suspend fun setActiveLanguage(pack: WatchLanguagePack?): Boolean
        public suspend fun listLanguagePacks(): List<WatchLanguagePack>
        public suspend fun removeLanguagePack(pack: WatchLanguagePack): Boolean
        public fun installLanguagePack(path: Path, name: String, activate: Boolean = true)
        public fun installLanguagePack(url: String, name: String, activate: Boolean = true)
    }

    public interface LanguageState {
        public val languagePackInstallState: LanguagePackInstallState
        public val installedLanguagePack: InstalledLanguagePack?
    }

    public interface Language : LanguageInstall, LanguageState

    public interface Health {
        public suspend fun requestHealthData(fullSync: Boolean): Boolean
    }

    internal class Services internal constructor(
        internal val debug: Debug,
        internal val appRunState: AppRunState,
        internal val firmware: FirmwareUpdater,
        internal val messages: Messages,
        internal val time: Time,
        internal val appMessages: AppMessages,
        internal val logs: Logs,
        internal val coreDump: CoreDump,
        internal val music: Music,
        internal val pkjs: PKJS,
        internal val companionAppControl: CompanionAppControl,
        internal val devConnection: DevConnection,
        internal val screenshot: Screenshot,
        internal val language: LanguageInstall,
        internal val health: Health,
    )

    internal class PrfServices internal constructor(
        internal val firmware: FirmwareUpdater,
        internal val logs: Logs,
        internal val coreDump: CoreDump,
        internal val devConnection: DevConnection
    )
}

public fun PebbleDevice.color(): WatchColor? = when (this) {
    is KnownPebbleDevice -> color
    is ConnectingPebbleDevice -> color
    is DisconnectingPebbleDevice -> color
    is BleDiscoveredPebbleDevice -> pebbleScanRecord.watchColor()
    else -> null
}

internal fun PebbleLeScanRecord.watchColor(): WatchColor? =
    extendedInfo?.color?.let { WatchColor.fromProtocolNumber(it.toInt()) }