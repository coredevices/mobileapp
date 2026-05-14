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

    suspend fun upsertLocal(id: String, doc: ListDocument) {
        cacheDao.upsert(CachedList.fromDocument(id, doc))
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
}
