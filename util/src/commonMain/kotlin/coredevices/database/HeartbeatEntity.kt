package coredevices.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

@Entity
data class HeartbeatStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // Added default value for id
    val name: String,
    val timestamp: Long,
    val state: Boolean,
)

@Dao
interface HeartbeatStateDao {
    @Insert
    suspend fun updateState(heartbeatState: HeartbeatStateEntity) // Changed parameter

    @Query("SELECT * FROM HeartbeatStateEntity WHERE name = :name ORDER BY timestamp ASC")
    suspend fun getValuesForName(name: String): List<HeartbeatStateEntity>

    @Query("DELETE FROM HeartbeatStateEntity WHERE name = :name")
    suspend fun clearValuesForName(name: String)

    @Transaction
    suspend fun getValuesAndClear(name: String, timestamp: Long): List<HeartbeatStateEntity> {
        val values = getValuesForName(name)
        if (values.isEmpty()) {
            return emptyList()
        }
        clearValuesForName(name)
        val lastValue = values.last().copy(timestamp = timestamp)
        updateState(lastValue)
        return values + lastValue
    }

    @Query("SELECT DISTINCT name FROM HeartbeatStateEntity")
    suspend fun getNames(): List<String>

    @Query("SELECT MIN(timestamp) FROM HeartbeatStateEntity")
    suspend fun getEarliestTimestamp(): Long?
}