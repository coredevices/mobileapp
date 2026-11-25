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
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import coredevices.analytics.AnalyticsBackend
import coredevices.analytics.PROP_FIREBASE_UID
import coredevices.analytics.setUser
import coredevices.firestore.KEY_LAST_SIGN_IN_PROVIDERS
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
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.cancel
import coreapp.util.generated.resources.misc_sign_in_anyway
import coreapp.util.generated.resources.misc_sign_in_apple
import coreapp.util.generated.resources.misc_sign_in_existing_body
import coreapp.util.generated.resources.misc_sign_in_existing_title
import coreapp.util.generated.resources.misc_sign_in_github
import coreapp.util.generated.resources.misc_sign_in_google
import coreapp.util.generated.resources.misc_sign_in_network_error
import coreapp.util.generated.resources.misc_sign_in_unavailable
import coreapp.util.generated.resources.misc_sign_in_unknown_error
import coreapp.util.generated.resources.sign_in
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

internal expect suspend fun signInWithCredential(credential: AuthCredential)

// Thrown when the current user is anonymous and the supplied credential belongs to an
// existing Firebase account, so linking is not possible. Completing sign-in would switch
// to the existing account's UID and orphan the anonymous account's per-UID Firestore data
// (locker entries, user doc fields). The caller must confirm with the user before
// proceeding via [forceSignInWithCredential]. [credential] is null when the original was
// consumed by the failed link and a fresh one must be requested from the provider.
internal class AccountSwitchRequiredException(val credential: AuthCredential?) : Exception(
    "Sign-in target account already exists; switching would abandon anonymous-account data"
)

internal suspend fun forceSignInWithCredential(credential: AuthCredential): String? {
    val beforeUid = Firebase.auth.currentUser?.uid?.take(8)
    val wasAnonymous = Firebase.auth.currentUser?.isAnonymous
    val uid = Firebase.auth.signInWithCredential(credential).user?.uid
    Logger.i { "forceSignInWithCredential: provider=${credential.providerId} beforeUid=$beforeUid wasAnonymous=$wasAnonymous finalUid=${uid?.take(8)}" }
    return uid
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
    val settings: Settings = koinInject()
    val context = rememberUiContext()
    var pendingSwitch by remember { mutableStateOf<AccountSwitchRequiredException?>(null) }
    suspend fun forceSwitch(e: AccountSwitchRequiredException) {
        val credential = e.credential ?: credentialProvider(context!!) ?: return
        val anonymousUid = Firebase.auth.currentUser?.takeIf { it.isAnonymous }?.uid
        val uid = forceSignInWithCredential(credential)
        if (anonymousUid != null && uid != null && uid != anonymousUid) {
            analyticsBackend.logEvent("firebase_uid_linked", mapOf("anonymous_firebase_uid" to anonymousUid, PROP_FIREBASE_UID to uid))
        }
        completeSignIn(analyticsBackend, settings, credential, onSuccess)
    }
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
                    Logger.e(e) { "Error getting sign-in credential: ${e.message}" }
                    onError(e.message ?: getString(Res.string.misc_sign_in_unknown_error))
                    return@launch
                }
                try {
                    signInWithCredential(credential)
                    completeSignIn(analyticsBackend, settings, credential, onSuccess)
                } catch (e: AccountSwitchRequiredException) {
                    if (skipAccountSwitchConfirmation) {
                        try {
                            forceSwitch(e)
                        } catch (e2: Exception) {
                            Logger.e(e2) { "Error during forced account switch: ${e2.message}" }
                            onError(getString(Res.string.misc_sign_in_network_error))
                        }
                    } else {
                        pendingSwitch = e
                    }
                } catch (e: Exception) {
                    Logger.e(e) { "Error signing in with credential: ${e.message}" }
                    onError(getString(Res.string.misc_sign_in_network_error))
                    return@launch
                }
            }
        },
        text = text,
        primaryColor = primaryColor,
        modifier = modifier,
    )

    pendingSwitch?.let { e ->
        AccountSwitchConfirmationDialog(
            onConfirm = {
                pendingSwitch = null
                GlobalScope.launch(Dispatchers.Main) {
                    try {
                        forceSwitch(e)
                    } catch (e: Exception) {
                        Logger.e(e) { "Error completing forced account switch: ${e.message}" }
                        onError(getString(Res.string.misc_sign_in_network_error))
                    }
                }
            },
            onDismiss = { pendingSwitch = null },
        )
    }
}

private fun completeSignIn(
    analyticsBackend: AnalyticsBackend,
    settings: Settings,
    credential: AuthCredential,
    onSuccess: () -> Unit,
) {
    // Silent re-auth needs this even if the session is lost before any auth-state emission
    // reaches UsersDao, which is the case it exists to recover from.
    settings[KEY_LAST_SIGN_IN_PROVIDERS] = credential.providerId
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
        title = { Text(stringResource(Res.string.misc_sign_in_existing_title)) },
        text = {
            Text(stringResource(Res.string.misc_sign_in_existing_body))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.misc_sign_in_anyway)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
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
                    stringResource(Res.string.sign_in),
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
        text = stringResource(Res.string.misc_sign_in_unavailable, provider),
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
                    text = stringResource(Res.string.misc_sign_in_google),
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
                    text = stringResource(Res.string.misc_sign_in_apple),
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
                    text = stringResource(Res.string.misc_sign_in_github),
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
