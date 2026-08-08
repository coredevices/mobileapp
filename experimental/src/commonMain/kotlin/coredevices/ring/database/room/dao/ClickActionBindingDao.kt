package coredevices.ring.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import coredevices.ring.data.entity.room.ClickActionBinding
import kotlinx.coroutines.flow.Flow

@Dao
interface ClickActionBindingDao {
    @Query("SELECT * FROM ClickActionBinding ORDER BY clickCount ASC")
    fun getAllFlow(): Flow<List<ClickActionBinding>>

    @Query("SELECT * FROM ClickActionBinding WHERE clickCount = :clickCount AND enabled = 1")
    suspend fun getEnabledByClickCount(clickCount: Int): ClickActionBinding?

    @Query("SELECT * FROM ClickActionBinding WHERE clickCount = :clickCount")
    suspend fun getByClickCount(clickCount: Int): ClickActionBinding?

    @Insert
    suspend fun insert(binding: ClickActionBinding): Long

    @Update
    suspend fun update(binding: ClickActionBinding)

    @Query("DELETE FROM ClickActionBinding WHERE id = :id")
    suspend fun delete(id: Long)
}
