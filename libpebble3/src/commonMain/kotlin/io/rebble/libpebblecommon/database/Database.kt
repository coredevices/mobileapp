package io.rebble.libpebblecommon.database

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.database.dao.CalendarDao
import io.rebble.libpebblecommon.database.dao.ContactDao
import io.rebble.libpebblecommon.database.dao.HealthDao
import io.rebble.libpebblecommon.database.dao.KnownWatchDao
import io.rebble.libpebblecommon.database.dao.LockerAppPermissionDao
import io.rebble.libpebblecommon.database.dao.LockerEntryRealDao
import io.rebble.libpebblecommon.database.dao.NotificationAppRealDao
import io.rebble.libpebblecommon.database.dao.NotificationDao
import io.rebble.libpebblecommon.database.dao.TimelineNotificationRealDao
import io.rebble.libpebblecommon.database.dao.TimelinePinRealDao
import io.rebble.libpebblecommon.database.dao.TimelineReminderRealDao
import io.rebble.libpebblecommon.database.dao.VibePatternDao
import io.rebble.libpebblecommon.database.dao.WatchPrefRealDao
import io.rebble.libpebblecommon.database.dao.WeatherAppRealDao
import io.rebble.libpebblecommon.database.entity.AppPrefsEntryDao
import io.rebble.libpebblecommon.database.entity.AppPrefsEntryEntity
import io.rebble.libpebblecommon.database.entity.AppPrefsEntrySyncEntity
import io.rebble.libpebblecommon.database.entity.CalendarEntity
import io.rebble.libpebblecommon.database.entity.ContactEntity
import io.rebble.libpebblecommon.database.entity.HealthDataEntity
import io.rebble.libpebblecommon.database.entity.HealthSettingsEntryDao
import io.rebble.libpebblecommon.database.entity.HealthSettingsEntryEntity
import io.rebble.libpebblecommon.database.entity.HealthSettingsEntrySyncEntity
import io.rebble.libpebblecommon.database.entity.HealthStatDao
import io.rebble.libpebblecommon.database.entity.HealthStatEntity
import io.rebble.libpebblecommon.database.entity.HealthStatSyncEntity
import io.rebble.libpebblecommon.database.entity.KnownWatchItem
import io.rebble.libpebblecommon.database.entity.LockerAppPermission
import io.rebble.libpebblecommon.database.entity.LockerEntryEntity
import io.rebble.libpebblecommon.database.entity.LockerEntrySyncEntity
import io.rebble.libpebblecommon.database.entity.NotificationAppItemEntity
import io.rebble.libpebblecommon.database.entity.NotificationAppItemSyncEntity
import io.rebble.libpebblecommon.database.entity.NotificationEntity
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity
import io.rebble.libpebblecommon.database.entity.TimelineNotificationEntity
import io.rebble.libpebblecommon.database.entity.TimelineNotificationSyncEntity
import io.rebble.libpebblecommon.database.entity.TimelinePinEntity
import io.rebble.libpebblecommon.database.entity.TimelinePinSyncEntity
import io.rebble.libpebblecommon.database.entity.TimelineReminderEntity
import io.rebble.libpebblecommon.database.entity.TimelineReminderSyncEntity
import io.rebble.libpebblecommon.database.entity.VibePatternEntity
import io.rebble.libpebblecommon.database.entity.WatchPrefItemEntity
import io.rebble.libpebblecommon.database.entity.WatchPrefItemSyncEntity
import io.rebble.libpebblecommon.database.entity.WeatherAppEntryEntity
import io.rebble.libpebblecommon.database.entity.WeatherAppEntrySyncEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal const val DATABASE_FILENAME = "libpebble3.db"

@androidx.room.Database(
    entities = [
        KnownWatchItem::class,
        LockerEntryEntity::class,
        LockerEntrySyncEntity::class,
        TimelineNotificationEntity::class,
        TimelineNotificationSyncEntity::class,
        TimelinePinEntity::class,
        TimelinePinSyncEntity::class,
        TimelineReminderEntity::class,
        TimelineReminderSyncEntity::class,
        NotificationAppItemEntity::class,
        NotificationAppItemSyncEntity::class,
        CalendarEntity::class,
        HealthSettingsEntryEntity::class,
        HealthSettingsEntrySyncEntity::class,
        LockerAppPermission::class,
        NotificationEntity::class,
        ContactEntity::class,
        VibePatternEntity::class,
        HealthDataEntity::class,
        OverlayDataEntity::class,
        HealthStatEntity::class,
        HealthStatSyncEntity::class,
        WatchPrefItemEntity::class,
        WatchPrefItemSyncEntity::class,
        WeatherAppEntryEntity::class,
        WeatherAppEntrySyncEntity::class,
        AppPrefsEntryEntity::class,
        AppPrefsEntrySyncEntity::class,
    ],
    version = 33,
    autoMigrations = [
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21),
        AutoMigration(from = 21, to = 22),
        AutoMigration(from = 22, to = 23),
        AutoMigration(from = 23, to = 24),
        AutoMigration(from = 24, to = 25),
        AutoMigration(from = 25, to = 26),
        AutoMigration(from = 26, to = 27),
        AutoMigration(from = 27, to = 28),
        AutoMigration(from = 28, to = 29),
        AutoMigration(from = 29, to = 30, spec = AutoMigration30::class),
        AutoMigration(from = 30, to = 31),
        AutoMigration(from = 31, to = 32),
        AutoMigration(from = 32, to = 33),
    ],
    exportSchema = true,
)
@ConstructedBy(DatabaseConstructor::class)
@TypeConverters(RoomTypeConverters::class)
abstract class Database : RoomDatabase() {
    abstract fun knownWatchDao(): KnownWatchDao
    abstract fun lockerEntryDao(): LockerEntryRealDao
    abstract fun notificationAppDao(): NotificationAppRealDao
    abstract fun timelineNotificationDao(): TimelineNotificationRealDao
    abstract fun timelinePinDao(): TimelinePinRealDao
    abstract fun calendarDao(): CalendarDao
    abstract fun timelineReminderDao(): TimelineReminderRealDao
    abstract fun healthSettingsDao(): HealthSettingsEntryDao
    abstract fun lockerAppPermissionDao(): LockerAppPermissionDao
    abstract fun notificationsDao(): NotificationDao
    abstract fun contactDao(): ContactDao
    abstract fun vibePatternDao(): VibePatternDao
    abstract fun healthDao(): HealthDao
    abstract fun healthStatDao(): HealthStatDao
    abstract fun watchPrefDao(): WatchPrefRealDao
    abstract fun weatherAppDao(): WeatherAppRealDao
    abstract fun appPrefsDao(): AppPrefsEntryDao
}

@DeleteTable(tableName = "WatchSettingsEntity")
@DeleteTable(tableName = "WatchSettingsSyncEntity")
class AutoMigration30 : AutoMigrationSpec

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object DatabaseConstructor : RoomDatabaseConstructor<Database> {
    override fun initialize(): Database
}

fun getRoomDatabase(ctx: AppContext): Database {
    return getDatabaseBuilder(ctx)
        //.addMigrations()
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        // V7 required a full re-create.
        .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

internal expect fun getDatabaseBuilder(ctx: AppContext): RoomDatabase.Builder<Database>
