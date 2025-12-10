package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.rebble.libpebblecommon.database.entity.ContactEntity
import io.rebble.libpebblecommon.database.entity.MuteState
import kotlinx.coroutines.flow.Flow

data class ContactWithCount(
    @Embedded val contact: ContactEntity,
    val count: Int,
)

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(watch: ContactEntity)

    @Query("SELECT a.*, COUNT(ne.id) as count " +
            "FROM ContactEntity a " +
            "LEFT JOIN NotificationEntity ne ON ne.people LIKE '%' || a.lookupKey || '%'" +
            "GROUP BY a.lookupKey " +
            "ORDER BY a.name ASC")
    fun getContactsWithCountFlow(): Flow<List<ContactWithCount>>

    @Query("""
        SELECT *
        FROM ContactEntity
    """)
    suspend fun getContacts(): List<ContactEntity>

    @Query("""
        SELECT *
        FROM ContactEntity
        WHERE lookupKey = :key
    """)
    suspend fun getContact(key: String): ContactEntity?

    @Query("""
        DELETE FROM ContactEntity
        WHERE lookupKey = :key
    """)
    suspend fun delete(key: String)

    @Query("""
        UPDATE ContactEntity
        SET muteState = :muteState, vibePatternName = :vibePatternName
        WHERE lookupKey = :key
    """)
    suspend fun updateContactState(key: String, muteState: MuteState, vibePatternName: String?)
}