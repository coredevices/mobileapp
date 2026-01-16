package coredevices.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import co.touchlab.kermit.Logger
import coredevices.analytics.AnalyticsBackend
import coredevices.analytics.setUser
import coredevices.util.GoogleAuthUtil
import coredevices.util.Platform
import coredevices.util.emailOrNull
import coredevices.util.getAndroidActivity
import coredevices.util.isAndroid
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun SignInButton(onError: (String) -> Unit = {}, enabled: Boolean = true) {
    val platform = koinInject<Platform>()
    val analyticsBackend: AnalyticsBackend = koinInject()
    val googleAuthUtil = if (platform.isAndroid) {
        val context = getAndroidActivity()
        koinInject<GoogleAuthUtil> { parametersOf(context) }
    } else {
        koinInject<GoogleAuthUtil>()
    }
    val scope = rememberCoroutineScope()
    Button(
        onClick = {
            scope.launch {
                val credential = try {
                    googleAuthUtil.signInGoogle() ?: return@launch
                } catch (e: Exception) {
                    onError(e.message ?: "Unknown error")
                    return@launch
                }
                try {
                    if (Firebase.auth.currentUser?.linkWithCredential(credential) != null) {
                        Logger.i { "Successfully linked anonymous user to account" }
                    }
                } catch (_: FirebaseAuthUserCollisionException) {
                    Logger.i { "User is already created, not linking anonymous user" }
                }
                Firebase.auth.signInWithCredential(credential)
                Firebase.auth.currentUser?.emailOrNull?.let {
                    analyticsBackend.setUser(email = it)
                }
                analyticsBackend.logEvent("signed_in_google")
            }
        },
        enabled = enabled
    ) {
        Text("Sign in with Google")
    }
}