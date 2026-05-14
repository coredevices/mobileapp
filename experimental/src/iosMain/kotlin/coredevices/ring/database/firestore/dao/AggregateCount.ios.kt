package coredevices.ring.database.firestore.dao

import cocoapods.FirebaseFirestoreInternal.FIRAggregateSource
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.ios
import dev.gitlive.firebase.firestore.toException
import kotlinx.coroutines.suspendCancellableCoroutine

actual suspend fun CollectionReference.count(): Int {
    return suspendCancellableCoroutine { cont ->
        this.ios.count().aggregationWithSource(FIRAggregateSource.FIRAggregateSourceServer) { snapshot, error ->
            if (error != null) {
                cont.resumeWith(Result.failure(error.toException()))
            } else if (snapshot != null) {
                cont.resumeWith(Result.success(snapshot.count.integerValue.toInt()))
            } else {
                cont.resumeWith(
                    Result.failure(
                        IllegalStateException("Unexpected null snapshot and null error in count aggregation")
                    )
                )
            }
        }
    }
}