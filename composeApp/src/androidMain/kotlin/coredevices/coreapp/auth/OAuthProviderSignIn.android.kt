package coredevices.coreapp.auth

import PlatformUiContext
import android.content.Context
import co.touchlab.kermit.Logger
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import dev.gitlive.firebase.auth.AuthCredential
import io.ktor.utils.io.CancellationException
import java.security.KeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private val logger = Logger.withTag("OAuthProviderSignIn")

private const val GENERIC_IDP_KEY_FAILURE = "public encryption key for Generic IDP flow"

// startActivityForSignInWithProvider signs in as a side effect, so an anonymous user is already
// gone by the time the caller gets a credential to decide with. Anonymous users must link instead.
internal suspend fun oAuthProviderCredential(
    context: PlatformUiContext,
    providerId: String,
    scopes: List<String>,
): AuthCredential? {
    val builder = OAuthProvider.newBuilder(providerId)
    builder.scopes = scopes
    val provider = builder.build()
    val anonUser = Firebase.auth.currentUser?.takeIf { it.isAnonymous }

    val result = try {
        Firebase.auth.pendingAuthResult?.await()
            ?: anonUser?.startActivityForLinkWithProvider(context.activity, provider)?.await()
            ?: Firebase.auth.startActivityForSignInWithProvider(context.activity, provider).await()
    } catch (e: CancellationException) {
        logger.i("$providerId sign-in cancelled")
        return null
    } catch (e: FirebaseAuthUserCollisionException) {
        val credential = e.updatedCredential
            ?: throw IllegalStateException("$providerId sign-in failed", e)
        logger.i("$providerId account already exists, leaving anonymous user for caller to confirm")
        return AuthCredential(credential)
    } catch (e: Exception) {
        logger.e(e) { "$providerId sign-in failed" }
        if (e.message?.contains(GENERIC_IDP_KEY_FAILURE) == true) {
            clearGenericIdpKeyset(context.activity)
            throw IllegalStateException(
                "Sign in needs an app restart. Close and reopen the app, then try again.",
                e,
            )
        }
        throw IllegalStateException("$providerId sign-in failed", e)
    }

    if (anonUser != null) {
        logger.i("Linked anonymous user ${anonUser.uid.take(8)} to $providerId")
    }
    return result.credential?.let { AuthCredential(it) }
}

// firebase-auth never recovers from a Generic IDP keyset it can't decrypt (unlike its storage
// keyset, which it rebuilds), and caches the failed helper per process — hence delete + restart.
private suspend fun clearGenericIdpKeyset(context: Context) = withContext(Dispatchers.IO) {
    val persistenceKey = FirebaseApp.getInstance().persistenceKey
    logger.w { "Clearing unusable Generic IDP keyset" }
    context.getSharedPreferences(
        "com.google.firebase.auth.api.crypto.$persistenceKey",
        Context.MODE_PRIVATE,
    ).edit().remove("GenericIdpKeyset").commit()
    try {
        KeyStore.getInstance("AndroidKeyStore")
            .apply { load(null) }
            .deleteEntry("firebear_master_key_id.$persistenceKey")
    } catch (e: Exception) {
        logger.w(e) { "Couldn't delete Generic IDP master key" }
    }
}
