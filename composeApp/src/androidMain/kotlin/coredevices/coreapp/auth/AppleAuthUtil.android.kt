package coredevices.coreapp.auth

import PlatformUiContext
import coredevices.util.auth.AppleAuthUtil
import dev.gitlive.firebase.auth.AuthCredential

actual class RealAppleAuthUtil : AppleAuthUtil {
    actual override suspend fun signInApple(context: PlatformUiContext): AuthCredential? =
        oAuthProviderCredential(context, "apple.com", listOf("email", "name"))
}
