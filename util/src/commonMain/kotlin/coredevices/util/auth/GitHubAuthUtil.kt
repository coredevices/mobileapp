package coredevices.util.auth

import PlatformUiContext
import dev.gitlive.firebase.auth.AuthCredential

interface GitHubAuthUtil {
    suspend fun signInGithub(context: PlatformUiContext): AuthCredential?
}