package coredevices.coreapp

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import co.touchlab.kermit.Logger
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import coredevices.util.CommonBuildKonfig
import coredevices.util.GoogleAuthUtil
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.GoogleAuthProvider

actual class RealGoogleAuthUtil(private val context: Activity): GoogleAuthUtil {
    companion object {
        private val logger = Logger.withTag(RealGoogleAuthUtil::class.simpleName!!)
    }
    override suspend fun signInGoogle(): AuthCredential? {
        val token = CommonBuildKonfig.GOOGLE_CLIENT_ID
        if (token == null) {
            logger.i("No Google client ID found")
            return null
        }
        val googleIdOption = GetSignInWithGoogleOption.Builder(token)
            .setNonce("coreapp-${generateNonce()}")
            .build()
        val credentialManager = CredentialManager.create(context)
        val request: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val result = credentialManager.getCredential(
                request = request,
                context = context,
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
}