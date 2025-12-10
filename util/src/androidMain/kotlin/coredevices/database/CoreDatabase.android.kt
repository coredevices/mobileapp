package coredevices.database

import PlatformContext
import androidx.room.Room

internal actual fun getCoreDatabaseBuilder(ctx: PlatformContext): androidx.room.RoomDatabase.Builder<CoreDatabase> {
    val appContext = ctx.context.applicationContext
    val dbFile = appContext.getDatabasePath(CORE_DATABASE_FILENAME)
    return Room.databaseBuilder<CoreDatabase>(
        context = appContext,
        name = dbFile.absolutePath
    )
}
