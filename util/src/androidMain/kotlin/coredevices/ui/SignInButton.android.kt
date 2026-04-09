package coredevices.ui

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.auth

internal actual suspend fun signInWithCredential(credential: AuthCredential) {
    try {
        if (Firebase.auth.currentUser?.isAnonymous == true) {
            if (Firebase.auth.currentUser?.linkWithCredential(credential) != null) {
                Logger.i { "Successfully linked anonymous user to account" }
            }
        }
    } catch (_: FirebaseAuthUserCollisionException) {
        Logger.i { "User is already created, not linking anonymous user" }
    }
    Firebase.auth.signInWithCredential(credential)
}