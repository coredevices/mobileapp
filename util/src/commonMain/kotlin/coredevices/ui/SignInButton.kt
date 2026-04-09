package coredevices.ui

import PlatformUiContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import co.touchlab.kermit.Logger
import coredevices.analytics.AnalyticsBackend
import coredevices.analytics.setUser
import coredevices.util.auth.AppleAuthUtil
import coredevices.util.auth.GitHubAuthUtil
import coredevices.util.auth.GoogleAuthUtil
import coredevices.util.emailOrNull
import coredevices.util.rememberUiContext
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.compose.currentKoinScope
import org.koin.compose.koinInject

internal expect suspend fun signInWithCredential(credential: AuthCredential)

@Composable
fun SignInButton(
    onError: (String) -> Unit = {},
    onSuccess: () -> Unit = {},
    text: String,
    credentialProvider: suspend (context: PlatformUiContext) -> AuthCredential?,
    primaryColor: Boolean,
    modifier: Modifier = Modifier,
) {
    val analyticsBackend: AnalyticsBackend = koinInject()
    val context = rememberUiContext()
    PebbleElevatedButton(
        onClick = {
            // Must use GlobalScope here: on iOS, presenting the native Apple/Google sign-in sheet
            // causes the Compose UIKitComposeSceneLayer to be disposed, which would cancel a
            // rememberCoroutineScope mid-flight and throw ForgottenCoroutineScopeException.
            // Dispatchers.Main ensures onSuccess/onError callbacks update Compose state safely.
            GlobalScope.launch(Dispatchers.Main) {
                val credential = try {
                    credentialProvider(context!!) ?: return@launch
                } catch (e: Exception) {
                    onError(e.message ?: "Unknown error")
                    return@launch
                }
                try {
                    signInWithCredential(credential)
                    Firebase.auth.currentUser?.emailOrNull?.let {
                        analyticsBackend.setUser(email = it)
                    }
                    Logger.i { "Signed in successfully as ${Firebase.auth.currentUser?.uid} via ${credential.providerId}" }
                    analyticsBackend.logEvent(
                        "signed_in_google",
                        mapOf("provider" to credential.providerId)
                    )
                    onSuccess()
                } catch (e: Exception) {
                    Logger.e(e) { "Error signing in with credential: ${e.message}" }
                    onError("Network error during sign in")
                    return@launch
                }
            }
        },
        text = text,
        primaryColor = primaryColor,
        modifier = modifier,
    )
}

@Composable
fun SignInDialog(
    onDismiss: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Sign in",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(8.dp)
                )
                SignInButtons(onDismiss, primaryColor = true)
            }
        }
    }
}

@Composable
fun SignInButtons(
    onDismiss: () -> Unit,
    primaryColor: Boolean,
) {
    val koin = currentKoinScope()
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            SignInButton(
                onError = { error = it },
                onSuccess = onDismiss,
                text = "Sign in with Google",
                credentialProvider = { context ->
                    val googleAuthUtil = koin.get<GoogleAuthUtil>()
                    googleAuthUtil.signInGoogle(context)
                },
                primaryColor = primaryColor,
                modifier = Modifier.fillMaxWidth(),
            )
            SignInButton(
                onError = { error = it },
                onSuccess = onDismiss,
                text = "Sign in with Apple",
                credentialProvider = { context ->
                    val appleAuthUtil = koin.get<AppleAuthUtil>()
                    appleAuthUtil.signInApple(context)
                },
                primaryColor = primaryColor,
                modifier = Modifier.fillMaxWidth(),
            )
            SignInButton(
                onError = { error = it },
                onSuccess = onDismiss,
                text = "Sign in with GitHub",
                credentialProvider = { context ->
                    val githubAuthUtil = koin.get<GitHubAuthUtil>()
                    githubAuthUtil.signInGithub(context)
                },
                primaryColor = primaryColor,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (error != null) {
            Text(
                error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}
