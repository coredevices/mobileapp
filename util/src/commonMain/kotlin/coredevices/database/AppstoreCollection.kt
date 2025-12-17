package coredevices.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import io.rebble.libpebblecommon.locker.AppType
import kotlinx.coroutines.flow.Flow

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = AppstoreSource::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["sourceId", "slug", "type"], unique = true)
    ],
)
data class AppstoreCollection (
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sourceId: Int,
    val title: String,
    val type: AppType,
    val slug: String,
    val enabled: Boolean,
)

@Dao
interface AppstoreCollectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCollection(collection: AppstoreCollection): Long

    @Query("SELECT * FROM AppstoreCollection WHERE sourceId = :sourceId AND slug = :slug AND type = :type LIMIT 1")
    suspend fun getCollection(sourceId: Int, slug: String, type: AppType): AppstoreCollection?

    @Query("SELECT * FROM AppstoreCollection")
    fun getAllCollections(): Flow<List<AppstoreCollection>>
}