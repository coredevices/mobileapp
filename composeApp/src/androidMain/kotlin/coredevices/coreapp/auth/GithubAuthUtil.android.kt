package coredevices.coreapp.auth

import PlatformUiContext
import coredevices.util.auth.GitHubAuthUtil
import dev.gitlive.firebase.auth.AuthCredential

actual class RealGithubAuthUtil : GitHubAuthUtil {
    actual override suspend fun signInGithub(context: PlatformUiContext): AuthCredential? =
        oAuthProviderCredential(context, "github.com", listOf("user:email"))
}
