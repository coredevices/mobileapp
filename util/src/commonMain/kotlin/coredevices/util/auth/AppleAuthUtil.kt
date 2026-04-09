package coredevices.util.auth

import PlatformUiContext
import dev.gitlive.firebase.auth.AuthCredential

interface AppleAuthUtil {
    suspend fun signInApple(context: PlatformUiContext): AuthCredential?
}