package coredevices.ring.database.firestore.dao

import coredevices.firestore.CollectionDao
import coredevices.indexai.data.entity.GeofenceLocationDocument
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.flow.Flow

/** Firestore DAO for saved places. Path: `places/{uid}/places/{placeId}`. */
class FirestoreGeofenceLocationsDao(dbProvider: () -> FirebaseFirestore) : CollectionDao("places", dbProvider) {
    private val collection get() = authenticatedId?.let { db.collection("$it/places") }
        ?: throw IllegalStateException("Not authenticated — cannot access places")

    fun changesFlow(): Flow<QuerySnapshot> = collection.snapshots

    suspend fun getAll(): QuerySnapshot = collection.get()

    suspend fun writeBatch(places: List<Pair<String, GeofenceLocationDocument>>) {
        if (places.isEmpty()) return
        val batch = db.batch()
        for ((id, doc) in places) {
            batch.set(collection.document(id), doc)
        }
        batch.commit()
    }
}
