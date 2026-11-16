package coredevices.ring.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import coredevices.ring.data.entity.room.battery.RingBatteryHistory

@Dao
interface RingBatteryHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: RingBatteryHistory)

    @Query("SELECT * FROM RingBatteryHistory WHERE serial = :serial")
    suspend fun get(serial: String): RingBatteryHistory?
}
