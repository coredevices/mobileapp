package coredevices.pebble.firmware

import co.touchlab.kermit.Logger
import coredevices.analytics.CoreAnalytics
import coredevices.pebble.services.EngDashOta
import coredevices.pebble.services.Memfault
import coredevices.util.CommonBuildKonfig
import coredevices.util.CoreConfigFlow
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform.*
import io.rebble.libpebblecommon.services.WatchInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class FirmwareUpdateCheck(
    private val memfault: Memfault,
    private val engDashOta: EngDashOta,
    private val cohorts: Cohorts,
    private val coreConfig: CoreConfigFlow,
    private val coreAnalytics: CoreAnalytics,
    private val clock: Clock = Clock.System,
) {
    private val logger = Logger.withTag("FirmwareUpdateCheck")

    private data class CacheKey(
        val platform: WatchHardwarePlatform,
        val serial: String,
    )

    /**
     * One entry per watch: the running version is an input to the check, so an entry only answers
     * for the version it was fetched for, and a version change evicts it rather than shadowing it.
     */
    private data class CacheEntry(
        val fwVersion: String,
        val isRecovery: Boolean,
        val result: FirmwareUpdateCheckResult,
        val expiresAt: Instant,
    )

    private val mutex = Mutex()
    private val cache = mutableMapOf<CacheKey, CacheEntry>()

    suspend fun checkForUpdates(watch: WatchInfo, force: Boolean): FirmwareUpdateCheckResult {
        val key = CacheKey(platform = watch.platform, serial = watch.serial)
        val fwVersion = watch.runningFwVersion.stringVersion
        val isRecovery = watch.runningFwVersion.isRecovery
        val now = clock.now()
        if (!force) {
            mutex.withLock {
                cache[key]
                    ?.takeIf { it.fwVersion == fwVersion && it.isRecovery == isRecovery }
                    ?.takeIf { it.expiresAt > now }
                    ?.let {
                        logger.v { "Serving FWUP from cache" }
                        return it.result
                    }
            }
        }
        val result = doCheck(watch)
        // Only cache definitive answers — transient failures (network, rate limit)
        // must retry on the next connect, not be locked in for the TTL.
        if (result !is FirmwareUpdateCheckResult.UpdateCheckFailed) {
            mutex.withLock {
                cache[key] = CacheEntry(fwVersion, isRecovery, result, now + CACHE_TTL)
            }
        }
        return result
    }

    private suspend fun doCheck(watch: WatchInfo): FirmwareUpdateCheckResult = when {
        watch.platform == UNKNOWN -> FirmwareUpdateCheckResult.UpdateCheckFailed("Unknown platform")
        watch.platform.isCoreDevice() -> coreDeviceCheck(watch)
        else -> cohorts.getLatestFirmware(watch)
    }

    private fun engDashOtaEnabled(): Boolean =
        CommonBuildKonfig.BUG_URL != null && coreConfig.value.useEngDashOta

    /** Prefer eng-dash when opted in, falling back to whichever source we'd otherwise have used. */
    private suspend fun coreDeviceCheck(watch: WatchInfo): FirmwareUpdateCheckResult {
        if (engDashOtaEnabled()) {
            val result = engDashOta.getLatestFirmware(watch)
            if (result !is FirmwareUpdateCheckResult.UpdateCheckFailed) {
                return result
            }
            logger.w { "eng-dash OTA check failed (${result.error}); falling back" }
            coreAnalytics.logEvent("core_ota_failed")
        }
        return if (CommonBuildKonfig.MEMFAULT_TOKEN != null) {
            memfault.getLatestFirmware(watch)
        } else {
            cohorts.getLatestFirmware(watch)
        }
    }

    companion object {
        private val CACHE_TTL: Duration = 15.minutes
    }
}

fun WatchHardwarePlatform.isCoreDevice(): Boolean = when (this) {
    UNKNOWN, PEBBLE_ONE_EV_1, PEBBLE_ONE_EV_2, PEBBLE_ONE_EV_2_3, PEBBLE_ONE_EV_2_4,
    PEBBLE_ONE_POINT_FIVE, PEBBLE_TWO_POINT_ZERO, PEBBLE_SNOWY_EVT_2, PEBBLE_SNOWY_DVT,
    PEBBLE_BOBBY_SMILES, PEBBLE_ONE_BIGBOARD_2, PEBBLE_ONE_BIGBOARD, PEBBLE_SNOWY_BIGBOARD,
    PEBBLE_SNOWY_BIGBOARD_2, PEBBLE_SPALDING_EVT, PEBBLE_SPALDING_PVT, PEBBLE_SPALDING_BIGBOARD,
    PEBBLE_SILK_EVT, PEBBLE_SILK, PEBBLE_SILK_BIGBOARD, PEBBLE_SILK_BIGBOARD_2_PLUS,
    PEBBLE_ROBERT_EVT, PEBBLE_ROBERT_BIGBOARD, PEBBLE_ROBERT_BIGBOARD_2 -> false
    else -> true
}
