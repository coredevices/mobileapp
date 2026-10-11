package coredevices.indexai.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import coredevices.indexai.data.entity.AgentIterationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentIterationDao {
    @Query("SELECT * FROM AgentIterationEntity WHERE recordingId = :recordingId ORDER BY id ASC")
    fun getForRecording(recordingId: Long): Flow<List<AgentIterationEntity>>

    @Insert
    suspend fun insert(iteration: AgentIterationEntity): Long
}
