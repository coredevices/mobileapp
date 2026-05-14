package coredevices.ring.database.firestore.dao

import coredevices.firestore.CollectionDao
import coredevices.ring.data.entity.firestore.FirestoreKnownRing
import dev.gitlive.firebase.firestore.FirebaseFirestore

fun FirestoreKnownRing.documentId() = mac + "_" + (serial ?: "noserial")

class FirestoreKnownRingsDao(
    dbProvider: () -> FirebaseFirestore,
) : CollectionDao("known_rings", dbProvider) {
    private val collection get() = authenticatedId?.let { db.collection("$it/rings") }

    suspend fun getAll(): Map<String, FirestoreKnownRing> {
        val snapshot = collection?.get() ?: return emptyMap()
        return snapshot.documents.associate { doc ->
            val ring: FirestoreKnownRing = doc.data()
            ring.documentId() to ring
        }
    }

    suspend fun set(ring: FirestoreKnownRing) {
        collection?.document(ring.documentId())?.set(ring)
    }
}