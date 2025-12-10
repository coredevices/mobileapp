package coredevices.coreapp

import cocoapods.GoogleSignIn.GIDSignIn
import coredevices.util.GoogleAuthUtil
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CompletableDeferred
import platform.UIKit.UIApplication

actual class RealGoogleAuthUtil : GoogleAuthUtil {
    override suspend fun signInGoogle(): AuthCredential? {
        val signIn = GIDSignIn.sharedInstance()
        val presentingViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
            ?: return null
        val completable = CompletableDeferred<AuthCredential?>()
        signIn.signInWithPresentingViewController(presentingViewController) { result, error ->
            if (error != null) {
                completable.completeExceptionally(Exception(error.localizedDescription))
            } else {
                val user = result?.user
                completable.complete(GoogleAuthProvider.credential(user?.idToken?.tokenString, user?.accessToken?.tokenString))
            }
        }
        return completable.await()
    }
}