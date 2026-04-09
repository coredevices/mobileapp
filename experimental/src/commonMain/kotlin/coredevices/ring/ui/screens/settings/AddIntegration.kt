package coredevices.ring.ui.screens.settings

import BugReportButton
import CoreNav
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import coreapp.util.generated.resources.back
import coredevices.ring.agent.integrations.GTasksIntegration
import coredevices.ring.agent.integrations.NotionIntegration
import coredevices.ring.data.IntegrationDefinition
import coredevices.ui.M3Dialog
import coredevices.util.rememberUiContext
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun AddIntegration(coreNav: CoreNav) {
    var dialog by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    dialog?.invoke()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = coreNav::goBack
                    ) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(coreapp.util.generated.resources.Res.string.back)
                        )
                    }
                },
                title = {
                    Text("Add integration")
                },
                actions = {
                    BugReportButton(
                        coreNav,
                        pebble = false,
                        screenContext = mapOf(
                            "screen" to "AddIntegration",
                        )
                    )
                }
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                val def = remember { GTasksIntegration.DEFINITION }
                Item(def) {
                    dialog = {
                        GTasksDialog(
                            onDismiss = { dialog = null }
                        )
                    }
                }
            }
            item {
                val def = remember { NotionIntegration.DEFINITION }
                Item(def) {
                    dialog = {
                        NotionDialog(
                            onDismiss = { dialog = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Item(def: IntegrationDefinition, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(def.title) },
        supportingContent = { Text(
            buildList {
                if (def.reminder != null) {
                    add("Reminders")
                }
                if (def.notes != null) {
                    add("Notes")
                }
            }.joinToString()
        ) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private sealed class SignInState {
    data object Idle : SignInState()
    data object SigningIn : SignInState()
    data object Success : SignInState()
    data class Error(val message: String) : SignInState()
}

@Composable
fun GTasksDialog(
    onDismiss: () -> Unit
) {
    val uiContext = rememberUiContext()!!
    val integration = koinInject<GTasksIntegration> { parametersOf(uiContext) }
    var state by remember { mutableStateOf<SignInState>(SignInState.Idle) }
    val scope = rememberCoroutineScope()

    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text("Google Tasks") },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(if (state is SignInState.Success) "Done" else "Cancel")
            }
            if (state !is SignInState.Success) {
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = state !is SignInState.SigningIn,
                    onClick = {
                        state = SignInState.SigningIn
                        scope.launch {
                            state = try {
                                if (integration.signIn(uiContext)) {
                                    SignInState.Success
                                } else {
                                    SignInState.Error("Sign in was cancelled.")
                                }
                            } catch (e: Throwable) {
                                Logger.w("AddIntegration", e) { "Error during Google Tasks sign in: ${e.message}" }
                                SignInState.Error(e.message ?: "Unknown error")
                            }
                        }
                    }
                ) {
                    Text("Sign In")
                }
            }
        }
    ) {
        when (val s = state) {
            is SignInState.Idle -> {
                Text("Sign in with Google to sync reminders to Google Tasks.")
            }
            is SignInState.SigningIn -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is SignInState.Success -> {
                Text("Successfully signed in.")
            }
            is SignInState.Error -> {
                Text(
                    "Error signing in: ${s.message}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun NotionDialog(
    onDismiss: () -> Unit
) {
    val uiContext = rememberUiContext()!!
    val integration = koinInject<NotionIntegration>()
    var state by remember { mutableStateOf<SignInState>(SignInState.Idle) }
    val scope = rememberCoroutineScope()
    fun onSignIn() {
        state = SignInState.SigningIn
        scope.launch {
            state = try {
                if (integration.signIn(uiContext)) {
                    if (integration.hasPage()) {
                        SignInState.Success
                    } else {
                        try {
                            integration.unlink()
                        } catch (e: Throwable) {
                            Logger.w("NotionDialog", e) { "Error during Notion unlink: ${e.message}" }
                        }
                        SignInState.Error("No page found for notes. Please give access to a single page in Notion for your notes.")
                    }
                } else {
                    SignInState.Error("Sign in was cancelled.")
                }
            } catch (e: Throwable) {
                Logger.w("AddIntegration", e) { "Error during Notion sign in: ${e.message}" }
                SignInState.Error(e.message ?: "Unknown error")
            }
        }
    }
    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text("Notion") },
        buttons = {
            if (state !is SignInState.Success) {
                TextButton(
                    enabled = state !is SignInState.SigningIn,
                    onClick = ::onSignIn
                ) {
                    Text("Sign In")
                }
            } else if (state is SignInState.Success) {
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
        }
    ) {
        Column {
            when (val s = state) {
                is SignInState.Idle -> {
                    Text("Sign in to send notes to Notion.")
                }
                is SignInState.SigningIn -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is SignInState.Success -> {
                    Column {
                        Text("Successfully signed in.")
                    }
                }
                is SignInState.Error -> {
                    Text(
                        "Error signing in:\n${s.message}",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}