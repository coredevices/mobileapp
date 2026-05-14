package coredevices.firestore

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore

abstract class CollectionDao(val id: String, dbProvider: () -> FirebaseFirestore) {
    protected val db: FirebaseFirestore by lazy(dbProvider)
    protected val authenticatedId get() = Firebase.auth.currentUser?.uid?.let { "$id/$it" }
}