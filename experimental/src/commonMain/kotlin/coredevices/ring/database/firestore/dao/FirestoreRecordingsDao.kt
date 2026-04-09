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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.time.Instant

class FirestoreRecordingsDao(dbProvider: () -> FirebaseFirestore): CollectionDao("recordings", dbProvider) {
    suspend fun addRecording(
        recording: RecordingDocument
    ): DocumentReference {
        return db.collection("$authenticatedId/recordings").add(recording)
    }

    fun changesFlow(): Flow<QuerySnapshot> {
        return db.collection("$authenticatedId/recordings").snapshots
    }

    suspend fun recordingsSince(since: Instant): QuerySnapshot {
        return db.collection("$authenticatedId/recordings")
            .where {
                "updated" greaterThan since.toEpochMilliseconds()
            }
            .get()
    }

    suspend fun getPaginated(limit: Int, startAfter: DocumentSnapshot? = null, source: Source = Source.DEFAULT): QuerySnapshot {
        return db.collection("$authenticatedId/recordings")
            .orderBy("timestamp", Direction.DESCENDING)
            .limit(limit)
            .let { if (startAfter != null) it.startAfter(startAfter) else it }
            .get(source)
    }

    fun getRecording(
        id: String
    ): DocumentReference {
        return db.collection("$authenticatedId/recordings").document(id)
    }
}