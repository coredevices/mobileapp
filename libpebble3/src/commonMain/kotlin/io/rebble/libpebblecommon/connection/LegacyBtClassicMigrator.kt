package io.rebble.libpebblecommon.connection

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import io.rebble.libpebblecommon.connection.bt.ble.BlePlatformConfig
import io.rebble.libpebblecommon.database.dao.KnownWatchDao
import io.rebble.libpebblecommon.database.entity.TransportType
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import io.rebble.libpebblecommon.metadata.supportsBtClassic

/**
 * One-shot migration that rewrites BLE-paired classic-supporting watches (Aplite/Basalt/Chalk)
 * into dedicated BluetoothClassic rows, keyed by the classic MAC the watch reported during its
 * first BLE connection. Replaces the legacy "auto-handoff from BLE to BT Classic" behavior.
 *
 * Runs at app boot, gated by a Settings flag so it only executes once. Safe to call before
 * `BondedWatchSeeder` since either an existing row stays as-is (BLE) or gets re-keyed (Classic).
 */
class LegacyBtClassicMigrator(
    private val knownWatchDao: KnownWatchDao,
    private val settings: Settings,
    private val blePlatformConfig: BlePlatformConfig,
) {
    suspend fun migrateIfNeeded() {
        if (!blePlatformConfig.supportsBtClassic) return
        if (settings.getBoolean(MIGRATION_DONE_KEY, false)) return
        val rows = knownWatchDao.knownWatches()
        for (row in rows) {
            val classicMac = row.btClassicMacAddress ?: continue
            if (row.transportType != TransportType.BluetoothLe) continue
            val watchType = WatchHardwarePlatform.fromHWRevision(row.watchType).watchType
            if (!watchType.supportsBtClassic()) continue
            try {
                knownWatchDao.remove(row.transportIdentifier)
                knownWatchDao.insertOrUpdate(
                    row.copy(
                        transportIdentifier = classicMac.uppercase(),
                        transportType = TransportType.BluetoothClassic,
                        btClassicMacAddress = null,
                    )
                )
                logger.i {
                    "Migrated BLE-paired watch ${row.name} from ${row.transportIdentifier} to BT Classic $classicMac"
                }
            } catch (e: Exception) {
                logger.w(e) { "Failed to migrate ${row.transportIdentifier} to BT Classic" }
            }
        }
        settings.putBoolean(MIGRATION_DONE_KEY, true)
    }

    companion object {
        private const val MIGRATION_DONE_KEY = "legacy_bt_classic_migration_done_v1"
        private val logger = Logger.withTag("LegacyBtClassicMigrator")
    }
}
