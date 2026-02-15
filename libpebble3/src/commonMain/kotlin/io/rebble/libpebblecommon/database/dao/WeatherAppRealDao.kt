package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import androidx.room.Query
import io.rebble.libpebblecommon.database.entity.WeatherAppEntry
import io.rebble.libpebblecommon.database.entity.WeatherAppEntryDao

@Dao
interface WeatherAppRealDao : WeatherAppEntryDao {
    @Query("SELECT * FROM WeatherAppEntryEntity WHERE deleted = 0")
    suspend fun getAll(): List<WeatherAppEntry>
}