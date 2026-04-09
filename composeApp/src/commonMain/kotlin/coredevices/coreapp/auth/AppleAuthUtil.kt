package coredevices.coreapp.auth

import PlatformUiContext
import coredevices.util.auth.AppleAuthUtil
import dev.gitlive.firebase.auth.AuthCredential

expect class RealAppleAuthUtil: AppleAuthUtil {
    override suspend fun signInApple(context: PlatformUiContext): AuthCredential?
}