package coredevices.ring.database.firestore.dao

import coredevices.firestore.CollectionDao
import coredevices.indexai.data.entity.RecordingDocument
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.QuerySnapshot
import dev.gitlive.firebase.firestore.Source
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

class FirestoreRecordingsDao(dbProvider: () -> FirebaseFirestore): CollectionDao("recordings", dbProvider) {
    private val collection get() = authenticatedId?.let { db.collection("$it/recordings") }
        ?: throw IllegalStateException("Not authenticated — cannot access recordings")

    /** Pure client-side, offline-safe Firestore document id generator.
     *  Returns a fresh 20-char alphanumeric id matching Firestore's own
     *  auto-id format. No network round trip; safe to call before sign-in.
     *
     *  Used at [coredevices.ring.database.room.repository.RecordingRepository.createRecording]
     *  time so every LocalRecording has its firestoreId pre-allocated. This
     *  collapses a class of bugs around items pointing at recordings that
     *  haven't been uploaded yet. The gitlive CollectionReference doesn't
     *  expose Firestore's no-arg `document()` overload, so we generate the
     *  same shape here directly. */
    fun newDocumentId(): String = buildString(20) {
        repeat(20) { append(FIRESTORE_ID_ALPHABET.random()) }
    }

    companion object {
        private val FIRESTORE_ID_ALPHABET: List<Char> =
            ('A'..'Z') + ('a'..'z') + ('0'..'9')
    }

    suspend fun addRecording(
        recording: RecordingDocument
    ): DocumentReference {
        return collection.add(recording)
    }

    suspend fun setRecording(id: String, recording: RecordingDocument) {
        collection.document(id).set(recording)
    }

    fun changesFlow(): Flow<QuerySnapshot> {
        return collection.snapshots
    }

    suspend fun recordingsSince(since: Instant): QuerySnapshot {
        return collection
            .where { "updated" greaterThan since.toEpochMilliseconds() }
            .get()
    }

    suspend fun getPaginated(limit: Int, startAfter: DocumentSnapshot? = null, source: Source = Source.DEFAULT): QuerySnapshot {
        return collection
            .orderBy("timestamp", Direction.DESCENDING)
            .limit(limit)
            .let { if (startAfter != null) it.startAfter(startAfter) else it }
            .get(source)
    }

    fun getRecording(id: String): DocumentReference {
        return collection.document(id)
    }

    suspend fun getCount(): Int {
        return collection.count()
    }

    suspend fun hasAnyRecordings(): Boolean {
        val snapshot = getPaginated(1)
        return snapshot.documents.isNotEmpty()
    }

    suspend fun deleteRecordingsByIds(ids: List<String>) {
        for (chunk in ids.chunked(500)) {
            val batch = db.batch()
            for (id in chunk) {
                batch.delete(collection.document(id))
            }
            batch.commit()
        }
    }

    suspend fun deleteAllRecordings() {
        while (true) {
            val snapshot = getPaginated(500)
            val docs = snapshot.documents
            if (docs.isEmpty()) break
            val batch = db.batch()
            for (doc in docs) {
                batch.delete(collection.document(doc.id))
            }
            batch.commit()
        }
    }
}