package coredevices.ring.database.firestore.dao

import coredevices.firestore.CollectionDao
import coredevices.indexai.data.entity.ListDocument
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Firestore DAO for lists. Path: `lists/{uid}/lists/{listId}`.
 * The 3 system seed lists (`list_notes_self`, `list_todos`, `list_shopping`) live
 * here alongside any user-created lists.
 */
class FirestoreListsDao(dbProvider: () -> FirebaseFirestore) : CollectionDao("lists", dbProvider) {
    private val collection get() = authenticatedId?.let { db.collection("$it/lists") }
        ?: throw IllegalStateException("Not authenticated — cannot access lists")

    suspend fun addList(list: ListDocument): DocumentReference {
        return collection.add(list)
    }

    suspend fun setList(id: String, list: ListDocument) {
        collection.document(id).set(list)
    }

    suspend fun deleteList(id: String) {
        collection.document(id).delete()
    }

    fun getList(id: String): DocumentReference {
        return collection.document(id)
    }

    suspend fun getListSnapshot(id: String): DocumentSnapshot {
        return collection.document(id).get()
    }

    fun changesFlow(): Flow<QuerySnapshot> {
        return collection.snapshots
    }

    suspend fun getAll(): QuerySnapshot {
        return collection.get()
    }

    /** Used by the bootstrap to seed the 3 default lists in one round-trip. */
    suspend fun writeBatch(lists: List<Pair<String, ListDocument>>) {
        if (lists.isEmpty()) return
        val batch = db.batch()
        for ((id, doc) in lists) {
            batch.set(collection.document(id), doc)
        }
        batch.commit()
    }
}
