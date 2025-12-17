package coredevices.pebble.firmware

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

import coredevices.util.CoreConfigFlow

interface FirmwareUpdateUiTracker {
    fun didFirmwareUpdateCheckFromUi()
    fun shouldUiUpdateCheck(): Boolean
    fun maybeNotifyFirmwareUpdate(update: FirmwareUpdateCheckResult, identifier: PebbleIdentifier, watchName: String)
    fun firmwareUpdateIsInProgress(identifier: PebbleIdentifier)
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

    override fun maybeNotifyFirmwareUpdate(update: FirmwareUpdateCheckResult, identifier: PebbleIdentifier, watchName: String) {
        if (coreConfigFlow.value.disableFirmwareUpdateNotifications) {
            return
        }
        if (update !is FirmwareUpdateCheckResult.FoundUpdate) {
            return
        }
        val key = update.hashCode() + identifier.asString.hashCode()
        if (haveNotifiedForUpdate.contains(key)) {
            return
        }
        haveNotifiedForUpdate.add(key)
        val notificationKey = identifier.asString.hashCode()
        activeNotificationKeys.add(notificationKey)
        notifyFirmwareUpdate(
            appContext = appContext,
            title = "Firmware update available",
            body = "Firmware ${update.version.stringVersion} is available for $watchName:\n${update.notes}",
            key = notificationKey,
            identifier = identifier,
        )
    }

    override fun firmwareUpdateIsInProgress(identifier: PebbleIdentifier) {
        val notificationKey = identifier.asString.hashCode()
        if (activeNotificationKeys.contains(notificationKey)) {
            logger.d { "Firmware update in progress; removing notification" }
            removeFirmwareUpdateNotification(appContext, notificationKey)
            activeNotificationKeys.remove(notificationKey)
        }
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
)

expect fun removeFirmwareUpdateNotification(appContext: AppContext, key: Int)