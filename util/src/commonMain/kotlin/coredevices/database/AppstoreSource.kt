package coredevices.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Entity
@Serializable
data class AppstoreSource(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    @ColumnInfo(defaultValue = "null")
    val algoliaAppId: String? = null,
    @ColumnInfo(defaultValue = "null")
    val algoliaApiKey: String? = null,
    @ColumnInfo(defaultValue = "null")
    val algoliaIndexName: String? = null,
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
)

@Dao
interface AppstoreSourceDao {
    @Insert
    suspend fun insertSource(source: AppstoreSource): Long

    @Query("SELECT * FROM AppstoreSource")
    fun getAllSources(): Flow<List<AppstoreSource>>

    @Query("SELECT * FROM AppstoreSource WHERE enabled = 1")
    fun getAllEnabledSourcesFlow(): Flow<List<AppstoreSource>>

    @Query("SELECT * FROM AppstoreSource WHERE enabled = 1")
    suspend fun getAllEnabledSources(): List<AppstoreSource>

    @Query("DELETE FROM AppstoreSource WHERE id = :sourceId")
    suspend fun deleteSourceById(sourceId: Int)

    @Query("UPDATE AppstoreSource SET enabled = :isEnabled WHERE id = :sourceId")
    suspend fun setSourceEnabled(sourceId: Int, isEnabled: Boolean)

    @Query("SELECT * FROM AppstoreSource WHERE id = :sourceId LIMIT 1")
    suspend fun getSourceById(sourceId: Int): AppstoreSource?
}