package coredevices.pebble.firmware

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.util.CoreConfigFlow
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.CommonConnectedDevice
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.connection.endpointmanager.FirmwareUpdater.FirmwareUpdateStatus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

interface FirmwareUpdateUiTracker {
    fun didFirmwareUpdateCheckFromUi()
    fun shouldUiUpdateCheck(): Boolean
    fun onConnectedWatch(watch: CommonConnectedDevice)
    fun updateWatchNow(libPebble: LibPebble, identifier: String)
}

class RealFirmwareUpdateUiTracker(
    private val settings: Settings,
    private val clock: Clock,
    private val appContext: AppContext,
    private val coreConfigFlow: CoreConfigFlow,
) : FirmwareUpdateUiTracker {
    private val logger = Logger.withTag("FirmwareUpdateUiTracker")
    private var lastUiUpdateMs: Long = settings.getLong(KEY_LAST_UI_UPDATE_CHECK_MS, 0)
    private val haveNotifiedForUpdate = mutableSetOf<Int>()
    private val haveAutoUpdated = mutableSetOf<Int>()
    private val autoUpdateTargetVersions = mutableMapOf<PebbleIdentifier, String>()
    private val activeNotificationKeys = mutableSetOf<Int>()

    override fun didFirmwareUpdateCheckFromUi() {
        val nowMs = clock.now().toEpochMilliseconds()
        lastUiUpdateMs = nowMs
        settings.putLong(KEY_LAST_UI_UPDATE_CHECK_MS, nowMs)
    }

    override fun shouldUiUpdateCheck(): Boolean {
        val nowMs = clock.now().toEpochMilliseconds()
        val shouldCheck = (nowMs - lastUiUpdateMs) > UI_UPDATE_CHECK_AGAIN_TIME.inWholeMilliseconds
        logger.d { "shouldUiUpdateCheck: $shouldCheck" }
        return shouldCheck
    }

    override fun onConnectedWatch(watch: CommonConnectedDevice) {
        if (watch.firmwareUpdateState is FirmwareUpdateStatus.InProgress) {
            logger.d { "Firmware update in progress; removing notification" }
            removeNotification(watch.identifier.asString)
        }
        if (autoUpdateTargetVersions[watch.identifier] == watch.runningFwVersion) {
            autoUpdateTargetVersions.remove(watch.identifier)
            notifyFirmwareUpdate(
                appContext = appContext,
                title = "PebbleOS updated",
                body = "${watch.name} was updated to PebbleOS ${watch.runningFwVersion}",
                key = watch.identifier.asString.hashCode(),
                identifier = watch.identifier,
                showUpdateAction = false,
            )
        }
        if (watch.watchInfo.runningFwVersion.suffix?.isNotEmpty() == true) {
            logger.d { "Not doing notification of auto-update because watch is running custom build" }
            return
        }
        val update = watch.firmwareUpdateAvailable.result as? FirmwareUpdateCheckResult.FoundUpdate ?: return
        val key = update.hashCode() + watch.identifier.asString.hashCode()
        val config = coreConfigFlow.value
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
        // Recovery mode is left to the user.
        if (config.autoFirmwareUpdate &&
            watch is ConnectedPebbleDevice &&
            config.autoFirmwareUpdateWindow?.contains(now) != false &&
            watch.firmwareUpdateState is FirmwareUpdateStatus.NotInProgress &&
            haveAutoUpdated.add(key)
        ) {
            logger.i { "Auto-updating ${watch.name} to ${update.version.stringVersion}" }
            haveNotifiedForUpdate.add(key)
            removeNotification(watch.identifier.asString)
            autoUpdateTargetVersions[watch.identifier] = update.version.stringVersion
            watch.updateFirmware(update)
            return
        }
        if (config.disableFirmwareUpdateNotifications) {
            return
        }
        if (!haveNotifiedForUpdate.add(key)) {
            return
        }
        val notificationKey = watch.identifier.asString.hashCode()
        activeNotificationKeys.add(notificationKey)
        notifyFirmwareUpdate(
            appContext = appContext,
            title = "PebbleOS update available",
            body = "PebbleOS ${update.version.stringVersion} is available for ${watch.name}:\n${update.notes}",
            key = notificationKey,
            identifier = watch.identifier,
            showUpdateAction = true,
        )
    }

    private fun removeNotification(identifier: String) {
        val notificationKey = identifier.hashCode()
        if (activeNotificationKeys.contains(notificationKey)) {
            removeFirmwareUpdateNotification(appContext, notificationKey)
            activeNotificationKeys.remove(notificationKey)
        }
    }

    override fun updateWatchNow(libPebble: LibPebble, identifier: String) {
        removeNotification(identifier)
        val watch =
            libPebble.watches.value.firstOrNull { it.identifier.asString == identifier }
        if (watch == null) {
            logger.w { "No matching connected watch found for $identifier" }
            return
        }
        val update = (watch as? ConnectedPebble.Firmware)?.firmwareUpdateAvailable?.result
        if (update !is FirmwareUpdateCheckResult.FoundUpdate) {
            logger.w { "No update available for $watch" }
            return
        }
        val updater = watch as? ConnectedPebble.Firmware
        if (updater == null) {
            logger.w { "Can't update firmware for $watch" }
            return
        }
        logger.d { "Starting update for $identifier to $update" }
        updater.updateFirmware(update)
    }

    companion object {
        private const val KEY_LAST_UI_UPDATE_CHECK_MS = "LAST_UI_UPDATE_CHECK_MS"
        private val UI_UPDATE_CHECK_AGAIN_TIME = 1.hours
    }
}

expect fun notifyFirmwareUpdate(
    appContext: AppContext,
    title: String,
    body: String,
    key: Int,
    identifier: PebbleIdentifier,
    showUpdateAction: Boolean,
)

expect fun removeFirmwareUpdateNotification(appContext: AppContext, key: Int)