package coredevices.coreapp.ui.screens

import CommonRoutes
import CoreNav
import DocumentAttachment
import NextBugReportContext
import PlatformShareLauncher
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.InsertPhoto
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import coreapp.util.generated.resources.back
import coredevices.pebble.rememberWatches
import coredevices.pebble.ui.TopBarIconButtonWithToolTip
import coredevices.pebble.ui.toImageBitmap
import coredevices.pebble.ui.toPngBytes
import io.rebble.libpebblecommon.connection.ConnectedPebble
import coredevices.ui.CoreLinearProgressIndicator
import coredevices.ui.PebbleElevatedButton
import coredevices.ui.SignInDialog
import coredevices.util.CoreConfigFlow
import coredevices.util.Platform
import coredevices.util.emailOrNull
import coredevices.util.isIOS
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.auth
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import rememberOpenDocumentLauncher
import rememberOpenPhotoLauncher
import size
import coreapp.composeapp.generated.resources.Res
import coreapp.composeapp.generated.resources.bug_report_add_image
import coreapp.composeapp.generated.resources.bug_report_add_images
import coreapp.composeapp.generated.resources.bug_report_attach_files
import coreapp.composeapp.generated.resources.bug_report_attach_image
import coreapp.composeapp.generated.resources.bug_report_capturing_screenshot
import coreapp.composeapp.generated.resources.bug_report_changelog_hint
import coreapp.composeapp.generated.resources.bug_report_clear_attachments
import coreapp.composeapp.generated.resources.bug_report_creating
import coreapp.composeapp.generated.resources.bug_report_describe
import coreapp.composeapp.generated.resources.bug_report_follow_up_hint
import coreapp.composeapp.generated.resources.bug_report_gathering_watch_logs
import coreapp.composeapp.generated.resources.bug_report_how_to
import coreapp.composeapp.generated.resources.bug_report_include_recent_recordings
import coreapp.composeapp.generated.resources.bug_report_include_recording
import coreapp.composeapp.generated.resources.bug_report_logs_note
import coreapp.composeapp.generated.resources.bug_report_must_sign_in
import coreapp.composeapp.generated.resources.bug_report_my_reports
import coreapp.composeapp.generated.resources.bug_report_remove_attachments
import coreapp.composeapp.generated.resources.bug_report_remove_files
import coreapp.composeapp.generated.resources.bug_report_remove_images
import coreapp.composeapp.generated.resources.bug_report_retake
import coreapp.composeapp.generated.resources.bug_report_send
import coreapp.composeapp.generated.resources.bug_report_send_icon
import coreapp.composeapp.generated.resources.bug_report_sent
import coreapp.composeapp.generated.resources.bug_report_share
import coreapp.composeapp.generated.resources.bug_report_sign_in_required
import coreapp.composeapp.generated.resources.bug_report_success
import coreapp.composeapp.generated.resources.bug_report_take_screenshot
import coreapp.composeapp.generated.resources.bug_report_title
import coreapp.composeapp.generated.resources.bug_report_type_index
import coreapp.composeapp.generated.resources.bug_report_type_label
import coreapp.composeapp.generated.resources.bug_report_type_watch
import coreapp.composeapp.generated.resources.bug_report_watch_screenshot
import coreapp.util.generated.resources.Res as UtilRes
import coreapp.util.generated.resources.remove
import coreapp.util.generated.resources.sign_in
import org.jetbrains.compose.resources.pluralStringResource

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
    screenshotPath: String?,
    screenContext: String? = null,
    initialText: String? = null,
) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val platform = koinInject<Platform>()
        // Watch screenshot capture / stored-watch detection
        val loadedWatches = rememberWatches()
        val watchesTimedOut by produceState(false) {
            delay(2.seconds)
            value = true
        }
        val watches = loadedWatches ?: if (watchesTimedOut) emptyList() else return@Box
        // Default to an Index bug report when there are no watches stored in libpebble.
        var isWatch by remember { mutableStateOf(pebble && watches.isNotEmpty()) }
        val coreConfigFlow: CoreConfigFlow = koinInject()
        val coreConfig by coreConfigFlow.flow.collectAsState()
        val bugReportProcessor = koinInject<BugReportProcessor>()
        val nextBugReportContext = koinInject<NextBugReportContext>()
        val (userMessage, setUserMessage) = remember { mutableStateOf(initialText ?: "") }
        val (sending, setSending) = remember { mutableStateOf(false) }
        val (showSuccess, setShowSuccess) = remember { mutableStateOf(false) }
        val (sendRecording, setSendRecording) = remember { mutableStateOf(recordingPath != null) }
        val (sendRecentRecordings, setSendRecentRecordings) = remember { mutableStateOf(true) }
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
        val snackbarHostState = remember { SnackbarHostState() }
        var showSignInDialog by remember { mutableStateOf(false) }

        val connectedScreenshotWatch = remember(watches) {
            watches.filterIsInstance<ConnectedPebble.Screenshot>().firstOrNull()
        }
        var watchScreenshot by remember { mutableStateOf<ImageBitmap?>(null) }
        var screenshotLoading by remember { mutableStateOf(false) }

        val signInRequiredText = stringResource(Res.string.bug_report_sign_in_required)
        val creatingText = stringResource(Res.string.bug_report_creating)
        val gatheringWatchLogsText = stringResource(Res.string.bug_report_gathering_watch_logs)

        fun sendLogs(shareLocally: Boolean) {
            if (isThirdPartyTest()) return

            // Check if user is signed in before proceeding
            if (user == null && !shareLocally) {
                setStatus(signInRequiredText)
                return
            }

            Logger.d { "Sending bug report" }
            setSending(true)

            scope.launch {
                // Extract Google ID token from current user
                val currentUser = user
                if (currentUser == null && !shareLocally) {
                    setStatus(signInRequiredText)
                    setSending(false)
                    return@launch
                }

                val capturedPngBytes = watchScreenshot?.toPngBytes()
                val params = BugReportGenerationParams(
                    userMessage = userMessage,
                    userName = currentUser?.userName,
                    userEmail = currentUser?.userEmail,
                    screenContext = screenContext ?: nextBugReportContext.nextContext ?: "",
                    attachments = attachments ?: emptyList(),
                    sendRecording = sendRecording,
                    sendRecentRecordings = !isWatch && sendRecentRecordings,
                    expOutputPath = recordingPath,
                    imageAttachments = (imageAttachments ?: emptyList()) +
                            if (capturedPngBytes != null) {
                                // Use the in-session screenshot capture (takes priority over screenshotPath)
                                listOf(
                                    DocumentAttachment(
                                        fileName = "watch-screenshot.png",
                                        mimeType = "image/png",
                                        source = kotlinx.io.Buffer().apply { write(capturedPngBytes) },
                                        size = capturedPngBytes.size.toLong(),
                                    )
                                )
                            } else if (screenshotPath != null) {
                                try {
                                    val screenshotFile = Path(screenshotPath)
                                    listOf(
                                        DocumentAttachment(
                                            fileName = "watch-screenshot.png",
                                            mimeType = "image/png",
                                            source = SystemFileSystem.source(screenshotFile)
                                                .buffered(),
                                            size = screenshotFile.size(),
                                        )
                                    )
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            } else {
                                emptyList()
                            },
                    fetchPebbleLogs = isWatch,
                    fetchPebbleCoreDump = isWatch,
                    includeExperimentalDebugInfo = !isWatch,
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
                            setStatus(creatingText)
                        }

                        BugReportState.GatheringWatchLogs -> {
                            if (!shareLocally) {
                                coreNav.goBack()
                            } else {
                                setStatus(gatheringWatchLogsText)
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

        if (showSignInDialog) {
            SignInDialog(
                onDismiss = { showSignInDialog = false }
            )
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.bug_report_title)) },
                    navigationIcon = {
                        IconButton(onClick = coreNav::goBack) {
                            Icon(
                                Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(
                                    UtilRes.string.back
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
                            description = stringResource(Res.string.bug_report_share),
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
                            enabled = !sending && !showSuccess && !screenshotLoading && user != null && canSendReports,
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
                                        stringResource(Res.string.bug_report_success),
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text(stringResource(Res.string.bug_report_sent))
                                }

                                else -> {
                                    Text(stringResource(Res.string.bug_report_send))
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        stringResource(Res.string.bug_report_send_icon),
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
                    label = { Text(stringResource(Res.string.bug_report_describe)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )
                if (coreConfig.enableIndex) {
                    Text(stringResource(Res.string.bug_report_type_label), modifier = Modifier.padding(top = 8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        FilterChip(
                            selected = !isWatch,
                            onClick = {
                                isWatch = false
                                watchScreenshot = null
                            },
                            label = { Text(stringResource(Res.string.bug_report_type_index)) }
                        )
                        FilterChip(
                            selected = isWatch,
                            onClick = { isWatch = true },
                            label = { Text(stringResource(Res.string.bug_report_type_watch)) }
                        )
                    }
                }
                if (user == null) {
                    Text(
                        stringResource(Res.string.bug_report_must_sign_in),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(4.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = { showSignInDialog = true },
                    ) {
                        Text(stringResource(UtilRes.string.sign_in))
                    }
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
                            stringResource(Res.string.bug_report_include_recording),
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
                if (coreConfig.enableIndex && !isWatch) {
                    Row(
                        modifier = Modifier.clickable(
                            interactionSource = null,
                            indication = null
                        ) { setSendRecentRecordings(!sendRecentRecordings) }) {
                        Checkbox(sendRecentRecordings, { setSendRecentRecordings(it) }, enabled = !sending)
                        Text(
                            stringResource(Res.string.bug_report_include_recent_recordings),
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
                // Watch screenshot capture
                if (isWatch && connectedScreenshotWatch != null) {
                    fun captureScreenshot() {
                        screenshotLoading = true
                        scope.launch {
                            try {
                                watchScreenshot = connectedScreenshotWatch.takeScreenshot()?.toImageBitmap()
                            } catch (e: Exception) {
                                Logger.withTag("BugReportScreen").e(e) { "Failed to capture screenshot" }
                            } finally {
                                screenshotLoading = false
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    if (watchScreenshot == null && !screenshotLoading) {
                        OutlinedButton(
                            onClick = { captureScreenshot() },
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(Res.string.bug_report_take_screenshot))
                        }
                    } else if (screenshotLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text(stringResource(Res.string.bug_report_capturing_screenshot), fontSize = 14.sp)
                        }
                    } else {
                        watchScreenshot?.let { screenshot ->
                            ElevatedCard(
                                shape = CutCornerShape(0.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(8.dp),
                                ) {
                                    val height = 120.dp
                                    val width = height / screenshot.height * screenshot.width
                                    Image(
                                        bitmap = screenshot,
                                        contentDescription = stringResource(Res.string.bug_report_watch_screenshot),
                                        modifier = Modifier.height(height).width(width),
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(top = 4.dp),
                                    ) {
                                        IconButton(
                                            onClick = { captureScreenshot() },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(Icons.Default.Refresh, stringResource(Res.string.bug_report_retake), modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = { watchScreenshot = null },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(Icons.Default.Close, stringResource(UtilRes.string.remove), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                PebbleElevatedButton(
                    onClick = {
                        scope.launch {
                            platform.openUrl("https://pbl.zip/bugs")
                        }
                    },
                    modifier = Modifier.padding(8.dp),
                    text = stringResource(Res.string.bug_report_how_to),
                    primaryColor = false,
                )
                Text(
                    stringResource(Res.string.bug_report_changelog_hint),
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
                    stringResource(Res.string.bug_report_logs_note),
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
                            text = stringResource(Res.string.bug_report_follow_up_hint),
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
                                text = stringResource(Res.string.bug_report_my_reports),
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
                    Icon(Icons.Default.AttachFile, stringResource(Res.string.bug_report_attach_files))
                }
            } else {
                IconButton(
                    onClick = {
                        setAttachments(null)
                    }
                ) {
                    Row(horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Delete, stringResource(Res.string.bug_report_remove_attachments))
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
                    Icon(Icons.Default.InsertPhoto, stringResource(Res.string.bug_report_add_image))
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
                            stringResource(Res.string.bug_report_remove_images),
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
                    contentDescription = stringResource(Res.string.bug_report_clear_attachments),
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(pluralStringResource(Res.plurals.bug_report_remove_files, attachments.size, attachments.size))
            } else {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = stringResource(Res.string.bug_report_attach_image),
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(Res.string.bug_report_add_images))
            }
        }
    }
}
