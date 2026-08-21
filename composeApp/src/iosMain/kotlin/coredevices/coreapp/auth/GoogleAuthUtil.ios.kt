package coredevices.coreapp.auth

import PlatformUiContext
import co.touchlab.kermit.Logger
import cocoapods.GoogleSignIn.GIDGoogleUser
import cocoapods.GoogleSignIn.GIDSignIn
import coredevices.util.auth.GoogleAuthUtil
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.UIKit.UIApplication
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual class RealGoogleAuthUtil : GoogleAuthUtil {
    companion object {
        private val logger = Logger.withTag("RealGoogleAuthUtil")
    }
    private val restoreMutex = Mutex()
    override suspend fun signInGoogle(context: PlatformUiContext): AuthCredential? {
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

    override suspend fun authorizeScopes(
        context: PlatformUiContext,
        scopes: List<String>
    ): String? {
        val vc = UIApplication.sharedApplication.keyWindow?.rootViewController ?: run {
            logger.e { "authorizeScopes: no presenting view controller, can't show consent UI" }
            error("No presenting view controller, can't show consent UI")
        }
        var user = suspendCoroutine {
            GIDSignIn.sharedInstance().restorePreviousSignInWithCompletion { user, error ->
                if (error != null) {
                    logger.e { "authorizeScopes: error restoring previous sign in: ${error.localizedDescription}" }
                    it.resume(null)
                } else {
                    it.resume(user)
                }
            }
        }
        if (user == null) {
            user = suspendCoroutine {
                GIDSignIn.sharedInstance().signInWithPresentingViewController(vc) { result, error ->
                    if (error != null) {
                        logger.e { "authorizeScopes: error signing in: ${error.localizedDescription}" }
                        it.resume(null)
                    } else {
                        it.resume(result?.user)
                    }
                }
            }
        }
        user ?: run {
            logger.e { "authorizeScopes: sign in failed, can't authorize scopes" }
            return null
        }
        val granted = user.grantedScopes?.mapNotNull { it as? String }?.toSet() ?: emptySet()
        val missing = scopes.filter { it !in granted }

        if (missing.isEmpty()) {
            logger.d { "authorizeScopes: all scopes already granted, refreshing token" }
            val d = CompletableDeferred<String?>()
            user.refreshTokensIfNeededWithCompletion { u, _ -> d.complete(u?.accessToken?.tokenString) }
            return d.await()
        }

        val d = CompletableDeferred<String?>()
        user.addScopes(missing, presentingViewController = vc) { result, e ->
            if (e != null) {
                logger.e { "authorizeScopes: error adding scopes: ${e.localizedDescription}" }
                d.completeExceptionally(Exception(e.localizedDescription))
            } else {
                d.complete(result?.user?.accessToken?.tokenString)
            }
        }
        return d.await()
    }

    override suspend fun getAccessToken(scopes: List<String>): String? {
        val user = GIDSignIn.sharedInstance().currentUser ?: restorePreviousSignIn() ?: return null
        val granted = user.grantedScopes?.mapNotNull { it as? String }?.toSet() ?: emptySet()
        if (!scopes.all { it in granted }) return null

        val d = CompletableDeferred<String?>()
        user.refreshTokensIfNeededWithCompletion { u, _ -> d.complete(u?.accessToken?.tokenString) }
        return d.await()
    }

    // GIDSignIn.currentUser is in-memory only; after a cold launch it's nil until the keychain
    // session is restored, even though the grant is still valid. The SDK keeps a single restore
    // completion, so concurrent restore calls must be serialized.
    private suspend fun restorePreviousSignIn(): GIDGoogleUser? = restoreMutex.withLock {
        GIDSignIn.sharedInstance().currentUser ?: suspendCoroutine<GIDGoogleUser?> { cont ->
            GIDSignIn.sharedInstance().restorePreviousSignInWithCompletion { user, error ->
                if (error != null) {
                    logger.d { "getAccessToken: could not restore previous sign in: ${error.localizedDescription}" }
                    cont.resume(null)
                } else {
                    cont.resume(user)
                }
            }
        }
    }
}