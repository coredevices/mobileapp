package coredevices.coreapp.auth

import PlatformUiContext
import cocoapods.FirebaseAuth.FIROAuthProvider
import coredevices.util.auth.GitHubAuthUtil
import dev.gitlive.firebase.auth.AuthCredential
import kotlinx.coroutines.suspendCancellableCoroutine

actual class RealGithubAuthUtil : GitHubAuthUtil {
    actual override suspend fun signInGithub(context: PlatformUiContext): AuthCredential? {
        val provider = FIROAuthProvider.providerWithProviderID("github.com")
        provider.setScopes(listOf("user:email"))
        return suspendCancellableCoroutine {
            provider.getCredentialWithUIDelegate(null) { credential, error ->
                if (error != null) {
                    it.resumeWith(Result.failure(Exception("GitHub sign-in failed: ${error.localizedDescription}")))
                } else if (credential != null) {
                    it.resumeWith(Result.success(AuthCredential(credential)))
                } else {
                    it.resumeWith(Result.failure(Exception("Unknown error during GitHub sign-in")))
                }
            }
        }
    }
}