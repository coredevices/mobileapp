package coredevices.database

import dev.gitlive.firebase.firestore.FirebaseFirestore

class UserConfigDao(firestoreProvider: () -> FirebaseFirestore) {
    private val firestore: FirebaseFirestore by lazy(firestoreProvider)
    companion object {
        val DEFAULT = UserConfig()
    }

    suspend fun getUserConfig(uid: String): UserConfig {
        return firestore.collection("user_config")
            .document(uid)
            .get()
            .takeIf { it.exists }
            ?.data<UserConfig>() ?: DEFAULT
    }
}