package coredevices.coreapp.auth

import PlatformUiContext
import coredevices.util.auth.GitHubAuthUtil
import dev.gitlive.firebase.auth.AuthCredential

expect class RealGithubAuthUtil: GitHubAuthUtil {
    override suspend fun signInGithub(context: PlatformUiContext): AuthCredential?
}