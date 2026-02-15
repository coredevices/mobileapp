package coredevices.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
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
data class AppstoreCollection(
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

    @Transaction
    suspend fun updateListOfCollections(appType: AppType, collections: List<AppstoreCollection>, sourceId: Int) {
        val existing = getAllCollections().filter { it.sourceId == sourceId && it.type == appType }
        val new = collections.filter { it.sourceId == sourceId && it.type == appType }
        new.filter { it.sourceId == sourceId && it.type == appType }.forEach { collection ->
            val existingEntry = existing.firstOrNull {  it.slug == collection.slug }
            val toInsert = when {
                existingEntry == null -> collection
                else -> collection.copy(id = existingEntry.id, enabled = existingEntry.enabled)
            }
            insertOrUpdateCollection(toInsert)
        }
        existing.filter { existingEntry ->
            new.none { existingEntry.slug == it.slug }
        }.forEach { existingEntry ->
            deleteCollection(existingEntry)
        }
    }

    @Query("SELECT * FROM AppstoreCollection WHERE sourceId = :sourceId AND slug = :slug AND type = :type LIMIT 1")
    suspend fun getCollection(sourceId: Int, slug: String, type: AppType): AppstoreCollection?

    @Delete
    suspend fun deleteCollection(collection: AppstoreCollection)

    @Query("SELECT * FROM AppstoreCollection")
    fun getAllCollectionsFlow(): Flow<List<AppstoreCollection>>

    @Query("SELECT * FROM AppstoreCollection")
    suspend fun getAllCollections(): List<AppstoreCollection>
}