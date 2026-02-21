package coredevices.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(
    primaryKeys = ["sourceId", "appId"],
    foreignKeys = [
        ForeignKey(
            entity = AppstoreSource::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class HeartEntity(
    val sourceId: Int,
    val appId: String,
)

@Dao
interface HeartsDao {
    @Upsert
    suspend fun addHeart(heart: HeartEntity)

    @Upsert
    suspend fun addHearts(hearts: List<HeartEntity>)

    @Delete
    suspend fun removeHeart(heart: HeartEntity)

    @Delete
    suspend fun removeHearts(hearts: List<HeartEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM HeartEntity WHERE appId = :appId AND sourceId = :sourceId)")
    fun isHeartedFlow(sourceId: Int, appId: String): Flow<Boolean>

    @Query("SELECT * FROM HeartEntity")
    fun getAllHeartsFlow(): Flow<List<HeartEntity>>

    @Query("SELECT appId FROM HeartEntity WHERE sourceId = :sourceId")
    suspend fun getAllHeartsForSource(sourceId: Int): List<String>

    @Transaction
    suspend fun updateHeartsForSource(sourceId: Int, newHearts: List<String>) {
        val oldHearts = getAllHeartsForSource(sourceId)
        val toInsert = newHearts.filter { it !in oldHearts }
        val toRemove = oldHearts.filter { it !in newHearts }
        addHearts(toInsert.map { HeartEntity(sourceId, it) })
        removeHearts(toRemove.map { HeartEntity(sourceId, it) })
    }
}
