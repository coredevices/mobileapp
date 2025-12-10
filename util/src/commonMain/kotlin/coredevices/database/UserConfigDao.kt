package coredevices.database

import dev.gitlive.firebase.firestore.FirebaseFirestore

class UserConfigDao(private val firestore: FirebaseFirestore) {
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