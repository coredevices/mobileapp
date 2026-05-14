package coredevices.ring.database.firestore.dao

import com.google.firebase.firestore.AggregateSource
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.android
import kotlinx.coroutines.tasks.await

actual suspend fun CollectionReference.count(): Int {
    val snapshot = this.android.count().get(AggregateSource.SERVER).await()
    return snapshot.count.toInt()
}