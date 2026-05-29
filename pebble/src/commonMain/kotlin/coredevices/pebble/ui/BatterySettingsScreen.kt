package coredevices.pebble.ui

import CommonApiConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import com.russhwolf.settings.Settings
import coredevices.pebble.ui.SettingsKeys.KEY_ENABLE_MEMFAULT_UPLOADS
import coredevices.ui.PebbleElevatedButton
import coredevices.ui.SignInDialog
import coredevices.util.emailOrNull
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

private const val MOBILE_BATTERY_PATH = "/m/battery"

@Composable
fun BatterySettingsScreen(navBarNav: NavBarNav, topBarParams: TopBarParams) {
    val apiConfig = koinInject<CommonApiConfig>()
    val settings = koinInject<Settings>()
    val analyticsEnabled = settings.getBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)
    val accountEmail by Firebase.auth.idTokenChanged
        .map { it?.emailOrNull }
        .distinctUntilChanged()
        .collectAsState(Firebase.auth.currentUser?.emailOrNull)

    var showSignInDialog by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        topBarParams.searchAvailable(null)
        topBarParams.title("Battery")
    }

    LaunchedEffect(accountEmail) {
        val email = accountEmail ?: run {
            url = null
            return@LaunchedEffect
        }
        val baseUrl = apiConfig.bugUrl
        if (baseUrl.isNullOrBlank()) {
            url = null
            loadError = "Battery analytics service is not configured"
            return@LaunchedEffect
        }
        val idToken = try {
            Firebase.auth.currentUser?.getIdToken(false)
        } catch (e: Exception) {
            Logger.withTag("BatterySettingsScreen").e(e) { "Failed to mint id token" }
            null
        }
        if (idToken == null) {
            // Drop the previous URL too, otherwise a stale (still-rendered)
            // WebView would hide the new error from the user.
            url = null
            loadError = "Sign in to view your battery analytics"
            return@LaunchedEffect
        }
        loadError = null
        url = buildBatteryUrl(baseUrl, email = email, idToken = idToken)
    }

    if (showSignInDialog) {
        SignInDialog(onDismiss = { showSignInDialog = false })
    }

    if (accountEmail == null) {
        SignedOutBatteryContent(onSignIn = { showSignInDialog = true })
        LaunchedEffect(Unit) {
            topBarParams.actions { }
        }
        return
    }

    if (!analyticsEnabled) {
        AnalyticsDisabledBatteryContent(
            onOpenSettings = {
                navBarNav.navigateTo(
                    PebbleNavBarRoutes.WatchSettingsCategoryRoute(
                        section = Section.Diagnostics.name,
                        topLevelType = TopLevelType.Phone.name,
                    ),
                )
            },
        )
        LaunchedEffect(Unit) {
            topBarParams.actions { }
        }
        return
    }

    val currentUrl = url
    val currentError = loadError
    if (currentUrl == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (currentError != null) {
                Text(
                    currentError,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        LaunchedEffect(Unit) {
            topBarParams.actions { }
        }
        return
    }

    val state = rememberWebViewState(currentUrl)
    val navigator = rememberWebViewNavigator()
    LaunchedEffect(Unit) {
        topBarParams.actions {
            IconButton(onClick = { navigator.reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        WebView(
            state = state,
            modifier = Modifier.fillMaxSize(),
            navigator = navigator,
        )
        if (state.loadingState is LoadingState.Loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        }
    }
}

private fun buildBatteryUrl(
    baseUrl: String,
    email: String,
    idToken: String,
): String {
    // bugUrl is configured as `<host>/api`; the mobile battery page is at the host root.
    val root = baseUrl.trimEnd('/').removeSuffix("/api")
    return buildString {
        append(root)
        append(MOBILE_BATTERY_PATH)
        append("?email=").append(email.encodeURLParameter())
        append("&googleIdToken=").append(idToken.encodeURLParameter())
    }
}

@Composable
private fun SignedOutBatteryContent(onSignIn: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "You must be signed into your Pebble account to view your Battery usage.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.height(12.dp))
        PebbleElevatedButton(
            onClick = onSignIn,
            text = "Sign in",
            primaryColor = true,
        )
    }
}

@Composable
private fun AnalyticsDisabledBatteryContent(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "You need 'Send watch analytics' enabled to view your Battery usage.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.height(12.dp))
        PebbleElevatedButton(
            onClick = onOpenSettings,
            text = "Open settings",
            primaryColor = true,
        )
    }
}
