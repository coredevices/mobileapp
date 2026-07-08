@file:OptIn(ExperimentalTime::class)

package coredevices.ring.database.room.repository

import coredevices.indexai.data.entity.ItemDocument
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.database.room.dao.CachedItemDao
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Items repository — Room-only. Reads come from the local cache table; writes
 * land in Room and are pushed to Firestore in a single place via the manual
 * "Sync now" pipeline (`SettingsViewModel.downloadFeedHistory`), which mirrors
 * how recordings already work. There is no per-call Firestore write-through
 * here so that we have ONE auto-sync mechanism (the existing recording
 * observer) and ONE manual sync path — same pattern across all three
 * collections.
 */
class ItemRepository(
    private val cacheDao: CachedItemDao,
    private val cancelReminder: suspend (localReminderId: Int) -> Unit,
) {
    fun getAllFlow(): Flow<List<CachedItem>> = cacheDao.getAllFlow()

    /** Sync-only flow that includes soft-deleted rows so tombstones
     *  propagate to Firestore. UI must not use this. */
    fun getAllForSyncFlow(): Flow<List<CachedItem>> = cacheDao.getAllForSyncFlow()

    fun getByListFlow(listId: String): Flow<List<CachedItem>> = cacheDao.getByListFlow(listId)

    suspend fun getByList(listId: String): List<CachedItem> = cacheDao.getByList(listId)

    fun getByRecordingFlow(recordingId: String): Flow<List<CachedItem>> =
        cacheDao.getByRecordingFlow(recordingId)

    suspend fun getByRecording(recordingId: String): List<CachedItem> =
        cacheDao.getByRecording(recordingId)

    fun getByIdFlow(id: String): Flow<CachedItem?> = cacheDao.getByIdFlow(id)

    suspend fun getById(id: String): CachedItem? = cacheDao.getById(id)

    suspend fun setItem(id: String, item: ItemDocument) {
        val existing = cacheDao.getById(id)
        cacheDao.upsert(CachedItem.fromDocument(id, item))
        // When a reminder item is completed (MOB-7831) or deleted (MOB-8390), cancel its
        // scheduled reminder so the notification doesn't still fire. Only on the false->true
        // transition; sync uses upsertLocal.
        val completed = existing != null && !existing.done && item.done
        val deleted = existing != null && !existing.deleted && item.deleted
        if (completed || deleted) {
            (item.metadata as? ItemDocument.ItemMetadata.Reminder)?.localReminderId?.let {
                runCatching { cancelReminder(it) }
            }
        }
    }

    /** Soft-delete; matches the prototype's `deleted: true` flag. */
    suspend fun softDelete(id: String) {
        val existing = cacheDao.getById(id) ?: return
        val updated = existing.toDocument().copy(
            deleted = true,
            updatedAt = Clock.System.now(),
        )
        setItem(id, updated)
    }

    suspend fun writeBatch(items: List<Pair<String, ItemDocument>>) {
        cacheDao.upsertAll(items.map { (id, doc) -> CachedItem.fromDocument(id, doc) })
    }

    suspend fun upsertLocal(id: String, doc: ItemDocument, locked: Boolean = false) {
        cacheDao.upsert(CachedItem.fromDocument(id, doc, locked))
    }

    suspend fun upsertAllLocal(items: List<Pair<String, ItemDocument>>) {
        cacheDao.upsertAll(items.map { (id, doc) -> CachedItem.fromDocument(id, doc) })
    }

    suspend fun deleteLocal(id: String) {
        cacheDao.deleteById(id)
    }

    suspend fun deleteAllLocal() {
        cacheDao.deleteAll()
    }

    /** Number of rows synced from an encrypted doc we couldn't decrypt. */
    suspend fun countLocked(): Int = cacheDao.countLocked()
}
