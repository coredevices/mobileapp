package coredevices.coreapp.ui.screens

import CommonRoutes
import CoreNav
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import coredevices.coreapp.api.AtlasTicketDetails
import coredevices.coreapp.api.BugReports
import coredevices.util.GoogleAuthUtil
import coredevices.util.Platform
import coredevices.util.emailOrNull
import coredevices.util.getAndroidActivity
import coredevices.util.isAndroid
import coredevices.util.rememberUiContext
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun BugReportsListScreen(
    coreNav: CoreNav,
) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val scope = rememberCoroutineScope()
        val bugReports: BugReports = koinInject()
        val logger = remember { Logger.withTag("BugReportsListScreen") }
        val context = rememberUiContext()
        val googleAuthUtil = koinInject<GoogleAuthUtil>()
        val tickets by bugReports.ticketDetails.collectAsState()
        val ticketDetails = tickets?.ticketDetails

        var loading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }

        // Get the authenticated user's email
        val user by Firebase.auth.authStateChanged.map {
            it?.emailOrNull
        }.distinctUntilChanged()
            .collectAsState(Firebase.auth.currentUser?.emailOrNull)

        fun signIn() {
            scope.launch {
                try {
                    val credential = googleAuthUtil.signInGoogle(context!!) ?: return@launch
                    Firebase.auth.signInWithCredential(credential)
                } catch (e: Exception) {
                    logger.e(e) { "Failed to sign in" }
                    error = "Sign in failed: ${e.message}"
                }
            }
        }

        fun loadBugReports() {
            scope.launch {
                loading = true
                bugReports.refresh()
                loading = false
            }
        }

        // Load bug reports when screen first appears or user changes
        LaunchedEffect(user) {
            // Clear data immediately when user changes (including sign out)
            if (user == null) {
                error = "Please sign in to view your bug reports"
                loading = false
            } else {
                loadBugReports()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("My Bug Reports") },
                    navigationIcon = {
                        IconButton(onClick = coreNav::goBack) {
                            Icon(
                                Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { loadBugReports() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    loading && (ticketDetails == null) -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = error!!,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                // Show sign in button if user is not authenticated
                                if (user == null) {
                                    Button(onClick = { signIn() }) {
                                        Text("Sign in with Google")
                                    }
                                } else {
                                    // Show refresh button if user is authenticated but there was an error
                                    IconButton(onClick = { loadBugReports() }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Retry")
                                    }
                                }
                            }
                        }
                    }

                    ticketDetails == null || ticketDetails.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No bug reports found",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    else -> {
                        // Show list of tickets using ListItem style
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val userEmail = user
                            if (userEmail == null) {
                                return@LazyColumn
                            }
                            items(ticketDetails) { ticket ->
                                TicketListItem(
                                    ticket = ticket,
                                    onClick = {
                                        coreNav.navigateTo(
                                            CommonRoutes.ViewBugReportRoute(
                                                conversationId = ticket.ticketId,
                                            )
                                        )
                                    }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketListItem(
    ticket: AtlasTicketDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        headlineContent = {
            Text(
                text = "#${ticket.simpleId} - ${ticket.subject}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            if (!ticket.lastMessageText.isNullOrEmpty() && ticket.lastMessageSide == "agent") {
                // Strip HTML tags from the message
                val strippedText = ticket.lastMessageText
                    .replace(Regex("<[^>]*>"), "") // Remove HTML tags
                    .replace(Regex("&[a-zA-Z]+;"), "") // Remove HTML entities
                    .trim()
                
                Text(
                    text = "Agent: $strippedText",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Text(
                text = ticket.displayTime,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}


