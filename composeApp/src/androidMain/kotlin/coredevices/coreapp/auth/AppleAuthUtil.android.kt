package coredevices.coreapp.auth

import PlatformUiContext
import co.touchlab.kermit.Logger
import com.google.firebase.Firebase
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import coredevices.util.auth.AppleAuthUtil
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.FirebaseAuth
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.tasks.await

actual class RealAppleAuthUtil : AppleAuthUtil {
    companion object {
        private val logger = Logger.withTag("RealAppleAuthUtil")
    }
    actual override suspend fun signInApple(context: PlatformUiContext): AuthCredential? {
        val provider = OAuthProvider.newBuilder("apple.com").apply {
            scopes = listOf("email", "name")
        }.build()

        val result = try {
            Firebase.auth.pendingAuthResult?.await()
                ?: Firebase.auth.startActivityForSignInWithProvider(context.activity, provider).await()
        } catch (e: CancellationException) {
            logger.i("Apple sign-in cancelled")
            return null
        } catch (e: Exception) {
            throw IllegalStateException("Apple sign-in failed", e)
        }
        return result.credential?.let { AuthCredential(it) }
    }
}