package coredevices.database

import PlatformContext
import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

internal actual fun getCoreDatabaseBuilder(ctx: PlatformContext): RoomDatabase.Builder<CoreDatabase> {
    val dbFilePath = documentDirectory() + "/$CORE_DATABASE_FILENAME"
    return Room.databaseBuilder<CoreDatabase>(
        name = dbFilePath,
    )
}

private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}
