package coredevices.coreapp.ui.screens

import CoreNav
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.SpaceEvenly
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.back
import coredevices.coreapp.api.BugReports
import coredevices.pebble.Platform
import coredevices.ui.PebbleWebview
import coredevices.ui.PebbleWebviewNavigator
import coredevices.ui.PebbleWebviewUrlInterceptor
import coredevices.util.emailOrNull
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import rememberOpenDocumentLauncher

private val logger = Logger.withTag("ViewBugReportScreen")

@Composable
fun ViewBugReportScreen(
    coreNav: CoreNav,
    conversationId: String,
) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val bugReports: BugReports = koinInject()
        val tickets = bugReports.ticketDetails.value
        val ticket = tickets?.ticketDetails?.firstOrNull { it.ticketId == conversationId }
        val userEmail = Firebase.auth.currentUser?.emailOrNull

        val url = if (ticket != null && tickets != null && userEmail != null) {
            "https://embed.atlas.so/index.html?appId=${tickets.appId}&userId=$userEmail&userEmail=$userEmail&query=open:ticket;ticketId:${ticket.ticketId};noHeader:1&userHash=${tickets.userHash}"
        } else {
            ""
        }
        var requestedMoreLogs by remember { mutableStateOf(false) }
        val bugReportProcessor = koinInject<BugReportProcessor>()

        val interceptor = remember {
            object : PebbleWebviewUrlInterceptor {
                override var navigator: PebbleWebviewNavigator? = null

                override fun onIntercept(url: String, navigator: PebbleWebviewNavigator): Boolean {
                    // Allow all URLs to load
                    return true
                }
            }
        }
        val calculateBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val platform = koinInject<Platform>()
        val webviewPadding = remember(calculateBottomPadding, platform) {
            if (platform == Platform.Android) {
                max(calculateBottomPadding - 15.dp, 0.dp)
            } else {
                0.dp
            }
        }
        val snackBarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val launchAttachmentDialog = rememberOpenDocumentLauncher { attachments ->
            if (attachments == null) return@rememberOpenDocumentLauncher
            ticket?.let {
                scope.launch {
                    val message =
                        if (attachments.size == 1) "Uploading attachment" else "Uploading attachments"
                    snackBarHostState.showSnackbar(message)
                }
                bugReportProcessor.updateBugReportWithNewAttachments(ticket.ticketId, attachments)
            }
        }

        val subject = ticket?.subject ?: ""
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Report: $subject", maxLines = 1, fontSize = 17.sp) },
                    navigationIcon = {
                        IconButton(onClick = coreNav::goBack) {
                            Icon(
                                Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(Res.string.back),
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackBarHostState) }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = SpaceEvenly,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ElevatedAssistChip(
                        label = { Text("Attach Files") },
                        onClick = {
                            ticket?.let {
                                launchAttachmentDialog(listOf("*/*"))
                            }
                        },
                        enabled = ticket != null,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Attach Files",
                            )
                        },
                        elevation = AssistChipDefaults.elevatedAssistChipElevation(elevation = 6.dp),
                    )
                    ElevatedAssistChip(
                        label = { Text("Attach More Logs") },
                        onClick = {
                            ticket?.let {
                                requestedMoreLogs = true
                                scope.launch {
                                    snackBarHostState.showSnackbar("Fetching and uploading new logs")
                                }
                                bugReportProcessor.updateBugReportWithNewLogs(ticket.ticketId)
                            }
                        },
                        enabled = !requestedMoreLogs && ticket != null,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Troubleshoot,
                                contentDescription = "Upload More Logs",
                            )
                        },
                        elevation = AssistChipDefaults.elevatedAssistChipElevation(elevation = 6.dp),
                    )
                }
                PebbleWebview(
                    url = url,
                    interceptor = interceptor,
                    modifier = Modifier.fillMaxSize().padding(bottom = webviewPadding),
                    onPageFinishedJavaScript = """
                    (function() {
                        function hideAttachmentIcon() {
                            let hidden = false;
                            
                            // Primary selector: by title attribute
                            const buttonByTitle = document.querySelector('button[title*="Attach files"]');
                            if (buttonByTitle) {
                                buttonByTitle.style.display = 'none';
                                hidden = true;
                            }
                            
                            // Backup selector: find button containing SVG with paperclip path
                            const buttons = document.querySelectorAll('button');
                            buttons.forEach(button => {
                                const svg = button.querySelector('svg');
                                if (svg && svg.innerHTML.includes('M15.841 4.252')) {
                                    button.style.display = 'none';
                                    hidden = true;
                                }
                            });
                            
                            // Also inject CSS for future elements
                            if (!document.getElementById('hide-attachment-style')) {
                                const style = document.createElement('style');
                                style.id = 'hide-attachment-style';
                                style.textContent = 'button[title*="Attach files"] { display: none !important; }';
                                document.head.appendChild(style);
                            }
                            
                            return hidden;
                        }
                        
                        // Try immediately
                        hideAttachmentIcon();
                        
                        // Retry with delays
                        setTimeout(hideAttachmentIcon, 500);
                        setTimeout(hideAttachmentIcon, 1000);
                        setTimeout(hideAttachmentIcon, 2000);
                        
                        // Watch for dynamic content
                        const observer = new MutationObserver(() => {
                            hideAttachmentIcon();
                        });
                        
                        observer.observe(document.body, {
                            childList: true,
                            subtree: true
                        });
                    })();
                """.trimIndent()
                )
            }
        }
    }
}