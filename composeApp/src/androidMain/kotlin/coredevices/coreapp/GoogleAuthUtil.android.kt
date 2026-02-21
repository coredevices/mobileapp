package coredevices.coreapp

import PlatformUiContext
import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import co.touchlab.kermit.Logger
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.russhwolf.settings.Settings
import coredevices.util.CommonBuildKonfig
import coredevices.util.GoogleAuthUtil
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CompletableDeferred
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

actual class RealGoogleAuthUtil(private val appContext: Context, private val settings: Settings): GoogleAuthUtil {
    companion object {
        private val logger = Logger.withTag(RealGoogleAuthUtil::class.simpleName!!)
    }

    override suspend fun signInGoogle(context: PlatformUiContext): AuthCredential? {
        val token = CommonBuildKonfig.GOOGLE_CLIENT_ID
        if (token == null) {
            logger.i("No Google client ID found")
            return null
        }
        val googleIdOption = GetSignInWithGoogleOption.Builder(token)
            .setNonce("coreapp-${generateNonce()}")
            .build()
        val credentialManager = CredentialManager.create(context.activity)
        val request: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val result = credentialManager.getCredential(
                request = request,
                context = context.activity,
            )
            val idToken = GoogleIdTokenCredential.createFrom(result.credential.data)
            return GoogleAuthProvider.credential(idToken.idToken, null)
        } catch (e: GetCredentialCancellationException) {
            logger.i("Google ID request cancelled")
            return null
        } catch (e: GetCredentialException) {
            throw IllegalStateException("Failed to get Google ID", e)
        }
    }

    private suspend fun authorize(context: Context, request: AuthorizationRequest): AuthorizationResult {
        return suspendCoroutine { cont ->
            Identity.getAuthorizationClient(context)
                .authorize(request)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    override suspend fun authorizeScopes(context: PlatformUiContext, scopes: List<String>): String? {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(scopes.map { Scope(it) })
            .build()
        val result = authorize(context.activity, request)

        if (!result.hasResolution()) {
            return result.accessToken
        }

        // Launch consent UI
        val componentActivity = context.activity as? ComponentActivity ?: return null
        val completable = CompletableDeferred<String?>()
        val key = "google-scope-auth-${System.nanoTime()}"
        val launcher = componentActivity.activityResultRegistry.register(
            key, ActivityResultContracts.StartIntentSenderForResult()
        ) { activityResult ->
            if (activityResult.resultCode == Activity.RESULT_OK) {
                val resolved = Identity.getAuthorizationClient(context.activity)
                    .getAuthorizationResultFromIntent(activityResult.data)
                completable.complete(resolved.accessToken)
            } else {
                completable.complete(null)
            }
        }
        launcher.launch(IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build())
        val token = completable.await()
        launcher.unregister()
        return token
    }

    override suspend fun getAccessToken(scopes: List<String>): String? {
        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(scopes.map { Scope(it) })
            .build()
        val result = authorize(context = appContext, request = request)
        return if (result.hasResolution()) null else result.accessToken
    }
}