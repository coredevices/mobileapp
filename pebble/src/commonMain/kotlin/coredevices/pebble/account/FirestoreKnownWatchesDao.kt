package coredevices.pebble.account

import coredevices.firestore.CollectionDao
import dev.gitlive.firebase.firestore.FirebaseFirestore

class FirestoreKnownWatchesDao(
    dbProvider: () -> FirebaseFirestore,
) : CollectionDao("known_watches", dbProvider) {
    private val collection get() = authenticatedId?.let { db.collection("$it/watches") }

    suspend fun getAll(): Map<String, FirestoreKnownWatch> {
        val snapshot = collection?.get() ?: return emptyMap()
        return snapshot.documents.associate { doc ->
            val watch: FirestoreKnownWatch = doc.data()
            watch.serial to watch
        }
    }

    suspend fun set(watch: FirestoreKnownWatch) {
        collection?.document(watch.serial)?.set(watch)
    }

    suspend fun delete(serial: String) {
        collection?.document(serial)?.delete()
    }
}
