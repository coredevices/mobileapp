package coredevices.ring.battery

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Index 01 battery-life estimate. Mirrors eng-dash `lib/ring-battery.ts` (used by the
 * daily server cron) — keep the constants in sync.
 *
 * The cell is primary silver-oxide: voltage stays flat until a 1–5 day cliff, so it
 * can't give advance warning. Instead we do energy accounting:
 *   used = idle drain × days in service + per-collection cost × lifetime collections
 */
object RingBatteryEstimator {
    /** Usable capacity (Ring hardware notes: 40 mAh usable). */
    const val CAPACITY_MAH = 40.0
    /** Hibernation/advertising drain, conservative (3 mAh/year). */
    const val IDLE_MAH_PER_DAY = 3.0 / 365
    /** Bench energy per collection incl. per-press overhead at the fleet median of 3.4 collections/press. */
    const val MAH_PER_COLLECTION = 0.000523
    /** Each collection is ~500 ms of recording (bench). */
    const val SECONDS_PER_COLLECTION = 0.5
    /** Below this the cell is past its knee (fleet data: 1–5 days left). */
    const val CLIFF_MV = 1545

    fun isPastCliff(voltageMv: Int?): Boolean = voltageMv != null && voltageMv > 0 && voltageMv < CLIFF_MV

    data class Estimate(val daysLeft: Int, val remainingPct: Int, val replaceSoon: Boolean)

    fun estimate(
        lifetimeCollections: Int,
        ageDays: Double,
        collectionsPerDay: Double,
        voltageMv: Int?,
    ): Estimate {
        val used = IDLE_MAH_PER_DAY * max(0.0, ageDays) + MAH_PER_COLLECTION * max(0, lifetimeCollections)
        val remaining = max(0.0, CAPACITY_MAH - used)
        val perDay = IDLE_MAH_PER_DAY + MAH_PER_COLLECTION * max(0.0, collectionsPerDay)
        val cliff = isPastCliff(voltageMv)
        return Estimate(
            daysLeft = if (cliff) 0 else floor(remaining / perDay).toInt(),
            remainingPct = if (cliff) 0 else (remaining / CAPACITY_MAH * 100).roundToInt(),
            replaceSoon = cliff || remaining == 0.0,
        )
    }
}
