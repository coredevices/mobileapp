package coredevices.util

import PlatformUiContext
import dev.gitlive.firebase.auth.AuthCredential

interface GoogleAuthUtil {
    suspend fun signInGoogle(context: PlatformUiContext): AuthCredential?

    /** Interactive: authorize scopes, showing consent UI if needed. Returns access token or null. */
    suspend fun authorizeScopes(context: PlatformUiContext, scopes: List<String>): String? = null

    /** Non-interactive: returns access token if scopes already granted, null otherwise. */
    suspend fun getAccessToken(scopes: List<String>): String? = null
}