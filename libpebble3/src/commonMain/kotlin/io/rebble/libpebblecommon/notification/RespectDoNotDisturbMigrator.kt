package io.rebble.libpebblecommon.notification

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import io.rebble.libpebblecommon.LibPebbleConfigHolder
import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.dao.WatchPrefRealDao
import io.rebble.libpebblecommon.database.entity.BoolWatchPref
import io.rebble.libpebblecommon.database.entity.WatchPrefItem
import kotlin.time.Clock

/**
 * One-shot migration of the phone-side respectDoNotDisturb config into the
 * [BoolWatchPref.RespectPhoneSilence] watch pref.
 */
class RespectDoNotDisturbMigrator(
    private val watchPrefRealDao: WatchPrefRealDao,
    private val libPebbleConfigHolder: LibPebbleConfigHolder,
    private val settings: Settings,
    private val clock: Clock,
) {
    suspend fun migrateIfNeeded() {
        if (settings.getBoolean(MIGRATION_DONE_KEY, false)) return
        if (libPebbleConfigHolder.config.value.notificationConfig.respectDoNotDisturb) {
            val pref = BoolWatchPref.RespectPhoneSilence
            watchPrefRealDao.insertOrReplace(
                WatchPrefItem(
                    id = pref.id,
                    value = pref.encodeValue(true),
                    timestamp = clock.now().asMillisecond(),
                )
            )
            logger.i { "Migrated respectDoNotDisturb to ${pref.id}" }
        }
        settings.putBoolean(MIGRATION_DONE_KEY, true)
    }

    companion object {
        private const val MIGRATION_DONE_KEY = "respect_dnd_watch_pref_migration_done_v1"
        private val logger = Logger.withTag("RespectDoNotDisturbMigrator")
    }
}
