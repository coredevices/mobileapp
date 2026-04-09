package coredevices.ring.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import coredevices.ring.data.entity.room.RingDebugTransfer
import kotlinx.coroutines.flow.Flow

@Dao
interface RingDebugTransferDao {
    @Insert
    suspend fun insert(ringDebugTransfer: RingDebugTransfer): Long

    @Query("SELECT * FROM RingDebugTransfer")
    fun getAllFlow(): Flow<List<RingDebugTransfer>>

    @Query("SELECT * FROM RingDebugTransfer WHERE id = :id")
    suspend fun getById(id: Int): RingDebugTransfer?
}