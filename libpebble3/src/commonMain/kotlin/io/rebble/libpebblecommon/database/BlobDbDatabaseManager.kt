package io.rebble.libpebblecommon.database

import io.rebble.libpebblecommon.connection.endpointmanager.blobdb.BlobDbDaos

internal interface BlobDbDatabaseManager {
    suspend fun deleteSyncRecordsForStaleDevices()
}

internal class RealBlobDbDatabaseManager(
    private val blobDatabases: BlobDbDaos,
) : BlobDbDatabaseManager {
    override suspend fun deleteSyncRecordsForStaleDevices() {
        blobDatabases.get().forEach { db ->
            db.deleteSyncRecordsForDevicesWhichDontExist()
        }
    }
}