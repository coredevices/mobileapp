package coredevices.ui

import PlatformUiContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import co.touchlab.kermit.Logger
import coredevices.analytics.AnalyticsBackend
import coredevices.analytics.setUser
import coredevices.util.CommonBuildKonfig
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

// Thrown when the current user is anonymous and the supplied credential belongs to an
// existing Firebase account, so linking is not possible. Completing sign-in would switch
// to the existing account's UID and orphan the anonymous account's per-UID Firestore data
// (locker entries, user doc fields). The caller must confirm with the user before
// proceeding via [forceSignInWithCredential].
internal class AccountSwitchRequiredException(val credential: AuthCredential) : Exception(
    "Sign-in target account already exists; switching would abandon anonymous-account data"
)

internal suspend fun forceSignInWithCredential(credential: AuthCredential) {
    val beforeUid = Firebase.auth.currentUser?.uid?.take(8)
    val wasAnonymous = Firebase.auth.currentUser?.isAnonymous
    Firebase.auth.signInWithCredential(credential)
    Logger.i { "forceSignInWithCredential: provider=${credential.providerId} beforeUid=$beforeUid wasAnonymous=$wasAnonymous finalUid=${Firebase.auth.currentUser?.uid?.take(8)}" }
}

@Composable
private fun SignInButton(
    onError: (String) -> Unit = {},
    onSuccess: () -> Unit = {},
    text: String,
    credentialProvider: suspend (context: PlatformUiContext) -> AuthCredential?,
    primaryColor: Boolean,
    skipAccountSwitchConfirmation: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val analyticsBackend: AnalyticsBackend = koinInject()
    val context = rememberUiContext()
    var pendingSwitchCredential by remember { mutableStateOf<AuthCredential?>(null) }
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
                    completeSignIn(analyticsBackend, credential, onSuccess)
                } catch (e: AccountSwitchRequiredException) {
                    if (skipAccountSwitchConfirmation) {
                        try {
                            forceSignInWithCredential(e.credential)
                            completeSignIn(analyticsBackend, e.credential, onSuccess)
                        } catch (e2: Exception) {
                            Logger.e(e2) { "Error during forced account switch: ${e2.message}" }
                            onError("Network error during sign in")
                        }
                    } else {
                        pendingSwitchCredential = e.credential
                    }
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

    pendingSwitchCredential?.let { credential ->
        AccountSwitchConfirmationDialog(
            onConfirm = {
                pendingSwitchCredential = null
                GlobalScope.launch(Dispatchers.Main) {
                    try {
                        forceSignInWithCredential(credential)
                        completeSignIn(analyticsBackend, credential, onSuccess)
                    } catch (e: Exception) {
                        Logger.e(e) { "Error completing forced account switch: ${e.message}" }
                        onError("Network error during sign in")
                    }
                }
            },
            onDismiss = { pendingSwitchCredential = null },
        )
    }
}

private fun completeSignIn(
    analyticsBackend: AnalyticsBackend,
    credential: AuthCredential,
    onSuccess: () -> Unit,
) {
    Firebase.auth.currentUser?.emailOrNull?.let {
        analyticsBackend.setUser(email = it)
    }
    Logger.i { "Signed in successfully as ${Firebase.auth.currentUser?.uid} via ${credential.providerId}" }
    analyticsBackend.logEvent(
        "signed_in_google",
        mapOf("provider" to credential.providerId)
    )
    onSuccess()
}

@Composable
private fun AccountSwitchConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign in to existing account?") },
        text = {
            Text(
                "This account already exists. Signing in will switch to it, and any " +
                    "apps or settings saved as a guest on this device won't carry over."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Sign in anyway") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun SignInDialog(
    onDismiss: () -> Unit = {},
    skipAccountSwitchConfirmation: Boolean = false,
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
                SignInButtons(
                    onDismiss = onDismiss,
                    primaryColor = true,
                    skipAccountSwitchConfirmation = skipAccountSwitchConfirmation,
                )
            }
        }
    }
}

// Set [skipAccountSwitchConfirmation] = true on screens where the user cannot yet have
// any anonymous-account data worth preserving (e.g. onboarding, before any apps are
// installed). It bypasses the "Sign in to existing account?" dialog that would otherwise
// appear when linking the anonymous account fails because the destination account exists.
@Composable
private fun SignInUnavailable(provider: String) {
    Text(
        text = "$provider login disabled within this build",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    )
}

@Composable
fun SignInButtons(
    onDismiss: () -> Unit,
    primaryColor: Boolean,
    skipAccountSwitchConfirmation: Boolean = false,
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
            if (CommonBuildKonfig.GOOGLE_AUTH_ENABLED) {
                SignInButton(
                    onError = { error = it },
                    onSuccess = onDismiss,
                    text = "Sign in with Google",
                    credentialProvider = { context ->
                        val googleAuthUtil = koin.get<GoogleAuthUtil>()
                        googleAuthUtil.signInGoogle(context)
                    },
                    primaryColor = primaryColor,
                    skipAccountSwitchConfirmation = skipAccountSwitchConfirmation,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SignInUnavailable("Google")
            }
            if (CommonBuildKonfig.APPLE_AUTH_ENABLED) {
                SignInButton(
                    onError = { error = it },
                    onSuccess = onDismiss,
                    text = "Sign in with Apple",
                    credentialProvider = { context ->
                        val appleAuthUtil = koin.get<AppleAuthUtil>()
                        appleAuthUtil.signInApple(context)
                    },
                    primaryColor = primaryColor,
                    skipAccountSwitchConfirmation = skipAccountSwitchConfirmation,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SignInUnavailable("Apple")
            }
            if (CommonBuildKonfig.GITHUB_AUTH_ENABLED) {
                SignInButton(
                    onError = { error = it },
                    onSuccess = onDismiss,
                    text = "Sign in with GitHub",
                    credentialProvider = { context ->
                        val githubAuthUtil = koin.get<GitHubAuthUtil>()
                        githubAuthUtil.signInGithub(context)
                    },
                    primaryColor = primaryColor,
                    skipAccountSwitchConfirmation = skipAccountSwitchConfirmation,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SignInUnavailable("GitHub")
            }
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
