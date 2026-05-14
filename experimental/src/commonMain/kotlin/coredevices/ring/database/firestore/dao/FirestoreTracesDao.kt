package coredevices.ring.database.firestore.dao

import coredevices.firestore.CollectionDao
import coredevices.ring.data.entity.room.TraceDocument
import coredevices.ring.data.entity.room.TraceSessionDocument
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlin.time.Clock

class FirestoreTracesDao(dbProvider: () -> FirebaseFirestore): CollectionDao("traces", dbProvider) {
    private val collection get() = authenticatedId?.let { db.collection("$it/traces") }
        ?: throw IllegalStateException("Not authenticated — cannot access traces")

    suspend fun setTrace(firestoreRecordingId: String, sessions: List<TraceSessionDocument>) {
        val doc = TraceDocument(
            sessions = sessions,
        )
        collection.document(firestoreRecordingId).set(doc)
    }
}
