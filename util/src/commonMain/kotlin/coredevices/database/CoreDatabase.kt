package coredevices.database

import PlatformContext
import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal const val CORE_DATABASE_FILENAME = "coreapp.db"

@Database(
    entities = [
        HeartbeatStateEntity::class,
        AppstoreSource::class,
        AppstoreCollection::class
    ],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
    exportSchema = true,
)
@ConstructedBy(CoreDatabaseConstructor::class)
abstract class CoreDatabase : RoomDatabase() {
    abstract fun analyticsDao(): HeartbeatStateDao
    abstract fun appstoreSourceDao(): AppstoreSourceDao
    abstract fun appstoreCollectionDao(): AppstoreCollectionDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object CoreDatabaseConstructor : RoomDatabaseConstructor<CoreDatabase> {
    override fun initialize(): CoreDatabase
}

fun getCoreRoomDatabase(ctx: PlatformContext): CoreDatabase {
    return getCoreDatabaseBuilder(ctx)
        //.addMigrations()
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        // V7 required a full re-create.
//        .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

internal expect fun getCoreDatabaseBuilder(ctx: PlatformContext): RoomDatabase.Builder<CoreDatabase>
