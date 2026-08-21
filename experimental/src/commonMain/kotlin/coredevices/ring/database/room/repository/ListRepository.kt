@file:OptIn(ExperimentalTime::class)

package coredevices.ring.database.room.repository

import coredevices.indexai.data.entity.ListDocument
import coredevices.ring.data.entity.room.indexfeed.CachedList
import coredevices.ring.database.room.dao.CachedListDao
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Lists repository — Room-only. Same pattern as [ItemRepository]: per-call
 * writes land in Room; the manual "Sync now" pipeline pushes to Firestore.
 */
class ListRepository(
    private val cacheDao: CachedListDao,
) {
    fun getAllFlow(): Flow<List<CachedList>> = cacheDao.getAllFlow()

    /** Sync-only flow that includes soft-deleted rows so tombstones
     *  propagate to Firestore. UI must not use this. */
    fun getAllForSyncFlow(): Flow<List<CachedList>> = cacheDao.getAllForSyncFlow()

    fun getByIdFlow(id: String): Flow<CachedList?> = cacheDao.getByIdFlow(id)

    suspend fun getById(id: String): CachedList? = cacheDao.getById(id)

    suspend fun getBySeed(seed: String): CachedList? = cacheDao.getBySeed(seed)

    suspend fun localCount(): Int = cacheDao.count()

    suspend fun setList(id: String, list: ListDocument) {
        cacheDao.upsert(CachedList.fromDocument(id, list))
    }

    /** Creates a fresh user list with a locally-generated id and returns that id.
     *  The next "Sync now" pushes it to Firestore alongside everything else. */
    suspend fun createList(title: String): String {
        val now = Clock.System.now()
        val id = "list_${now.toEpochMilliseconds()}"
        setList(id, ListDocument(createdAt = now, updatedAt = now, title = title))
        return id
    }

    suspend fun softDelete(id: String) {
        val existing = cacheDao.getById(id) ?: return
        val updated = existing.toDocument().copy(
            deleted = true,
            updatedAt = Clock.System.now(),
        )
        setList(id, updated)
    }

    suspend fun writeBatch(lists: List<Pair<String, ListDocument>>) {
        cacheDao.upsertAll(lists.map { (id, doc) -> CachedList.fromDocument(id, doc) })
    }

    suspend fun upsertLocal(id: String, doc: ListDocument, locked: Boolean = false) {
        cacheDao.upsert(CachedList.fromDocument(id, doc, locked))
    }

    suspend fun upsertAllLocal(lists: List<Pair<String, ListDocument>>) {
        cacheDao.upsertAll(lists.map { (id, doc) -> CachedList.fromDocument(id, doc) })
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
