package coredevices.pebble.firmware

import co.touchlab.kermit.Logger
import coredevices.pebble.services.Memfault
import coredevices.util.CommonBuildKonfig
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
    private val cohorts: Cohorts,
    private val clock: Clock = Clock.System,
) {
    private val logger = Logger.withTag("FirmwareUpdateCheck")

    private data class CacheKey(
        val platform: WatchHardwarePlatform,
        val serial: String,
        val fwVersion: String,
        val isRecovery: Boolean,
    )

    private data class CacheEntry(
        val result: FirmwareUpdateCheckResult,
        val expiresAt: Instant,
    )

    private val mutex = Mutex()
    private val cache = mutableMapOf<CacheKey, CacheEntry>()

    suspend fun checkForUpdates(watch: WatchInfo, force: Boolean): FirmwareUpdateCheckResult {
        val key = CacheKey(
            platform = watch.platform,
            serial = watch.serial,
            fwVersion = watch.runningFwVersion.stringVersion,
            isRecovery = watch.runningFwVersion.isRecovery,
        )
        val now = clock.now()
        if (!force) {
            mutex.withLock {
                cache[key]?.takeIf { it.expiresAt > now }?.let {
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
                cache[key] = CacheEntry(result, now + CACHE_TTL)
            }
        }
        return result
    }

    private suspend fun doCheck(watch: WatchInfo): FirmwareUpdateCheckResult = when {
        watch.platform == UNKNOWN -> FirmwareUpdateCheckResult.UpdateCheckFailed("Unknown platform")
        watch.platform.isCoreDevice() && CommonBuildKonfig.MEMFAULT_TOKEN != null -> memfault.getLatestFirmware(watch)
        else -> cohorts.getLatestFirmware(watch)
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
