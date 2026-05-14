package coredevices.ring.database.firestore.dao

import coredevices.firestore.CollectionDao
import coredevices.indexai.data.entity.ItemDocument
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Firestore DAO for items. Path: `items/{uid}/items/{itemId}`.
 * Mirrors the shape of [FirestoreRecordingsDao] so the room repository can sync
 * the same way.
 */
class FirestoreItemsDao(dbProvider: () -> FirebaseFirestore) : CollectionDao("items", dbProvider) {
    private val collection get() = authenticatedId?.let { db.collection("$it/items") }
        ?: throw IllegalStateException("Not authenticated — cannot access items")

    suspend fun addItem(item: ItemDocument): DocumentReference {
        return collection.add(item)
    }

    suspend fun setItem(id: String, item: ItemDocument) {
        collection.document(id).set(item)
    }

    suspend fun deleteItem(id: String) {
        collection.document(id).delete()
    }

    fun getItem(id: String): DocumentReference {
        return collection.document(id)
    }

    fun changesFlow(): Flow<QuerySnapshot> {
        return collection.snapshots
    }

    suspend fun getAll(): QuerySnapshot {
        return collection.orderBy("updatedAt", Direction.DESCENDING).get()
    }

    /** Bulk-write multiple items in a single batch (used by ingest). */
    suspend fun writeBatch(items: List<Pair<String, ItemDocument>>) {
        if (items.isEmpty()) return
        for (chunk in items.chunked(500)) {
            val batch = db.batch()
            for ((id, doc) in chunk) {
                batch.set(collection.document(id), doc)
            }
            batch.commit()
        }
    }
}
