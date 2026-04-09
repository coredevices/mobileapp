package coredevices.coreapp.auth

import PlatformUiContext
import co.touchlab.kermit.Logger
import com.google.firebase.Firebase
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import coredevices.util.auth.GitHubAuthUtil
import dev.gitlive.firebase.auth.AuthCredential
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.tasks.await

actual class RealGithubAuthUtil : GitHubAuthUtil {
    companion object {
        private val logger = Logger.withTag("RealGithubAuthUtil")
    }
    actual override suspend fun signInGithub(context: PlatformUiContext): AuthCredential? {
        val provider = OAuthProvider.newBuilder("github.com").apply {
            scopes = listOf("user:email")
        }.build()

        val result = try {
            Firebase.auth.pendingAuthResult?.await()
                ?: Firebase.auth.startActivityForSignInWithProvider(context.activity, provider).await()
        } catch (e: CancellationException) {
            logger.i("GitHub sign-in cancelled")
            return null
        } catch (e: Exception) {
            throw IllegalStateException("GitHub sign-in failed", e)
        }
        return result.credential?.let { AuthCredential(it) }
    }
}