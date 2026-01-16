package coredevices.coreapp.ui.screens

import CommonRoutes
import CoreNav
import DocumentAttachment
import NextBugReportContext
import PlatformShareLauncher
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.InsertPhoto
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.back
import coredevices.analytics.AnalyticsBackend
import coredevices.analytics.setUser
import coredevices.pebble.ui.TopBarIconButtonWithToolTip
import coredevices.ui.CoreLinearProgressIndicator
import coredevices.ui.SignInButton
import coredevices.util.GoogleAuthUtil
import coredevices.util.Platform
import coredevices.util.emailOrNull
import coredevices.util.getAndroidActivity
import coredevices.util.isAndroid
import coredevices.util.isIOS
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import rememberOpenDocumentLauncher
import rememberOpenPhotoLauncher

expect fun isThirdPartyTest(): Boolean
expect fun getExperimentalDebugInfoDirectory(): String

data class UserProps(
    val userName: String,
    val userEmail: String,
)

fun FirebaseUser?.toUserProps(): UserProps? =
    this?.let {
        UserProps(
            userName = it.displayName ?: "",
            userEmail = it.email ?: "",
        )
    }

@Composable
fun BugReportScreen(
    coreNav: CoreNav,
    pebble: Boolean,
    recordingPath: String?,
) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val platform = koinInject<Platform>()
        val bugReportProcessor = koinInject<BugReportProcessor>()
        val nextBugReportContext = koinInject<NextBugReportContext>()
        val (userMessage, setUserMessage) = remember { mutableStateOf("") }
        val (sending, setSending) = remember { mutableStateOf(false) }
        val (showSuccess, setShowSuccess) = remember { mutableStateOf(false) }
        val (sendRecording, setSendRecording) = remember { mutableStateOf(recordingPath != null) }
        val (attachments, setAttachments) = remember {
            mutableStateOf<List<DocumentAttachment>?>(
                null
            )
        }
        val (imageAttachments, setImageAttachments) = remember {
            mutableStateOf<List<DocumentAttachment>?>(
                null
            )
        }
        val attachmentScreenLauncher = rememberOpenDocumentLauncher { sources ->
            setAttachments(sources)
        }
        val imageAttachmentScreenLauncher = if (platform.isIOS) {
            rememberOpenPhotoLauncher { sources ->
                setImageAttachments(sources)
            }
        } else {
            null
        }
        val (status, setStatus) = remember { mutableStateOf("") }
        val (sendingProgress, setSendingProgress) = remember { mutableStateOf<Double?>(null) }
        val scope = rememberCoroutineScope()
        val user by Firebase.auth.authStateChanged.map {
            it?.emailOrNull ?: return@map null
            it.toUserProps()
        }.distinctUntilChanged()
            .collectAsState(Firebase.auth.currentUser.toUserProps())

        val keyboardController = LocalSoftwareKeyboardController.current
        val canSendReports = bugReportProcessor.canSendReports()
        val platformShareLauncher: PlatformShareLauncher = koinInject()

        fun sendLogs(shareLocally: Boolean) {
            if (isThirdPartyTest()) return

            // Check if user is signed in before proceeding
            if (user == null && !shareLocally) {
                setStatus("Please sign in with Google before submitting a bug report")
                return
            }

            Logger.d { "Sending bug report" }
            setSending(true)

            scope.launch {
                // Extract Google ID token from current user
                val currentUser = user
                if (currentUser == null && !shareLocally) {
                    setStatus("Please sign in with Google before submitting a bug report")
                    setSending(false)
                    return@launch
                }

                val params = BugReportGenerationParams(
                    userMessage = userMessage,
                    userName = currentUser?.userName,
                    userEmail = currentUser?.userEmail,
                    screenContext = nextBugReportContext.nextContext ?: "",
                    attachments = attachments ?: emptyList(),
                    sendRecording = sendRecording,
                    expOutputPath = recordingPath,
                    imageAttachments = imageAttachments ?: emptyList(),
                    fetchPebbleLogs = pebble,
                    fetchPebbleCoreDump = pebble,
                    includeExperimentalDebugInfo = !pebble,
                    shareLocally = shareLocally,
                )

                // Process bug report directly in all cases for Phase 1
                val status = bugReportProcessor.newBugReport(params)
                status.collect {
                    when (it) {
                        is BugReportState.BugReportResult.Success -> Unit
                        is BugReportState.BugReportResult.Failed -> {
                            setStatus(it.error)
                            setSending(false)
                            setSendingProgress(null)
                        }

                        BugReportState.Creating -> {
                            setStatus("Creating bug report...")
                        }

                        BugReportState.GatheringWatchLogs -> {
                            if (!shareLocally) {
                                coreNav.goBack()
                            } else {
                                setStatus("Gathering Watch Logs")
                            }
                        }

                        BugReportState.UploadingAttachments -> Unit
                        is BugReportState.ReadyToShare -> {
                            platformShareLauncher.share(it.name, it.file)
                            setSending(false)
                            setStatus("")
                            setSendingProgress(null)
                        }
                    }
                }
            }
        }

        fun openAttachmentScreen() {
            attachmentScreenLauncher(listOf("*/*"))
        }

        fun openImageAttachmentScreen() {
            imageAttachmentScreenLauncher?.invoke()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Bug Report") },
                    navigationIcon = {
                        IconButton(onClick = coreNav::goBack) {
                            Icon(
                                Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(
                                    Res.string.back
                                )
                            )
                        }
                    },
                    actions = {
                        TopBarIconButtonWithToolTip(
                            onClick = {
                                sendLogs(shareLocally = true)
                            },
                            icon = Icons.Filled.Share,
                            description = "Share",
                        )
                    },
                )
            },
            bottomBar = {
                BottomAppBar(
                    modifier = Modifier.wrapContentHeight(),
                    windowInsets = BottomAppBarDefaults.windowInsets.union(WindowInsets.ime),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.End)
                    ) {
                        AttachmentButtons(
                            attachments = attachments,
                            imageAttachments = imageAttachments,
                            openAttachmentScreen = ::openAttachmentScreen,
                            openImageAttachmentScreen = ::openImageAttachmentScreen,
                            setAttachments = setAttachments,
                            setImageAttachments = setImageAttachments
                        )
                        Spacer(Modifier.weight(1.0f))
                        Button(
                            enabled = !sending && !showSuccess && user != null && canSendReports,
                            onClick = {
                                keyboardController?.hide()
                                sendLogs(shareLocally = false)
                            },
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            when {
                                sending -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                }

                                showSuccess -> {
                                    Icon(
                                        Icons.Default.Check,
                                        "Success",
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text("Report Sent!")
                                }

                                else -> {
                                    Text("Send Report")
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        "Send",
                                        modifier = Modifier.size(ButtonDefaults.IconSize)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextField(
                    modifier = Modifier.height(200.dp).fillMaxWidth().padding(16.dp),
                    value = userMessage,
                    onValueChange = setUserMessage,
                    label = { Text("Please describe the bug") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )
                if (user == null) {
                    Text(
                        "You must sign in with Google to submit a bug report",
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(4.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                    SignInButton(onError = setStatus, enabled = !sending)
                    Spacer(Modifier.height(8.dp))
                }
                if (recordingPath != null) {
                    Row(
                        modifier = Modifier.clickable(
                            interactionSource = null,
                            indication = null
                        ) { setSendRecording(!sendRecording) }) {
                        Checkbox(sendRecording, { setSendRecording(it) }, enabled = !sending)
                        Text(
                            "Include recording",
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
                Text(
                    "Tap to see what's working and what's still in development",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp).clickable {
                        scope.launch {
                            platform.openUrl("https://ndocs.repebble.com/changelog")
                        }
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Please note that logs + device info will be sent with this report",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(20.dp))
                ElevatedCard(
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 6.dp
                    ),
                    modifier = Modifier.padding(15.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "You can follow up on an existing bug report by adding more logs or information:",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(6.dp),
                        )
                        Button(
                            onClick = {
                                coreNav.navigateTo(CommonRoutes.ViewMyBugReportsRoute)
                            },
                            modifier = Modifier.padding(8.dp),
                        ) {
                            Text(
                                text = "My Bug Reports",
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                if (sending) {
                    CoreLinearProgressIndicator({ sendingProgress?.toFloat() ?: 0.0f })
                }
                if (status.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        status,
                        textAlign = TextAlign.Center,
                        color = if (status.contains("failed", ignoreCase = true) ||
                            status.contains("error", ignoreCase = true) ||
                            status.contains("authentication", ignoreCase = true)
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = if (status.contains("failed", ignoreCase = true) ||
                            status.contains("error", ignoreCase = true) ||
                            status.contains("authentication", ignoreCase = true)
                        ) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentButtons(
    modifier: Modifier = Modifier,
    attachments: List<DocumentAttachment>?,
    imageAttachments: List<DocumentAttachment>?,
    openAttachmentScreen: () -> Unit,
    openImageAttachmentScreen: () -> Unit,
    setAttachments: (List<DocumentAttachment>?) -> Unit,
    setImageAttachments: (List<DocumentAttachment>?) -> Unit
) {
    val platform = koinInject<Platform>()
    if (platform.isIOS) {
        Row(modifier) {
            if (attachments.isNullOrEmpty()) {
                IconButton(
                    onClick = {
                        openAttachmentScreen()
                    },
                    modifier = Modifier.animateContentSize()
                ) {
                    Icon(Icons.Default.AttachFile, "Attach Files")
                }
            } else {
                IconButton(
                    onClick = {
                        setAttachments(null)
                    }
                ) {
                    Row(horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Delete, "Remove Attachments")
                        Text(
                            attachments.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
            if (imageAttachments.isNullOrEmpty()) {
                IconButton(
                    onClick = {
                        openImageAttachmentScreen()
                    },
                ) {
                    Icon(Icons.Default.InsertPhoto, "Add Image")
                }
            } else {
                IconButton(
                    onClick = {
                        setImageAttachments(null)
                    },
                ) {
                    Row(horizontalArrangement = Arrangement.Center) {
                        Icon(
                            Icons.Default.HideImage,
                            "Remove Images",
                            tint = IconButtonDefaults.iconButtonColors().contentColor
                        )
                        Text(
                            imageAttachments.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }
    } else {
        OutlinedButton(
            onClick = {
                if (attachments.isNullOrEmpty()) {
                    openAttachmentScreen()
                } else {
                    setAttachments(null)
                }
            },
            modifier = modifier,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            if (!attachments.isNullOrEmpty()) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Clear attachments",
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Remove ${attachments.size} file(s)")
            } else {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = "Attach an image",
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Add Images")
            }
        }
    }
}
