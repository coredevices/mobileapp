package coredevices.ui

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.auth

private val logger = Logger.withTag("SignInButton.android")

internal actual suspend fun signInWithCredential(credential: AuthCredential) {
    val currentUser = Firebase.auth.currentUser
    val anonUid = if (currentUser?.isAnonymous == true) currentUser.uid.take(8) else null
    if (anonUid != null) {
        try {
            currentUser!!.linkWithCredential(credential)
            logger.i { "Successfully linked anonymous user to account: anonUid=$anonUid provider=${credential.providerId} finalUid=${Firebase.auth.currentUser?.uid?.take(8)}" }
            return
        } catch (_: FirebaseAuthUserCollisionException) {
            logger.i { "User is already created, not linking anonymous user: anonUid=$anonUid provider=${credential.providerId}" }
            throw AccountSwitchRequiredException(credential)
        }
    }
    Firebase.auth.signInWithCredential(credential)
    logger.i { "signInWithCredential (non-anon path): provider=${credential.providerId} finalUid=${Firebase.auth.currentUser?.uid?.take(8)}" }
}