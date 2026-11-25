package coredevices.pebble.ui

import CoreNav
import DocumentAttachment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.contact_dev_add_files
import coreapp.pebble.generated.resources.contact_dev_add_photos
import coreapp.pebble.generated.resources.contact_dev_attach_hint
import coreapp.pebble.generated.resources.contact_dev_attachment_size
import coreapp.pebble.generated.resources.contact_dev_attachment_too_large
import coreapp.pebble.generated.resources.contact_dev_characters_remaining
import coreapp.pebble.generated.resources.contact_dev_email_not_verified
import coreapp.pebble.generated.resources.contact_dev_enter_message
import coreapp.pebble.generated.resources.contact_dev_intro
import coreapp.pebble.generated.resources.contact_dev_message
import coreapp.pebble.generated.resources.contact_dev_message_too_long
import coreapp.pebble.generated.resources.contact_dev_network_error
import coreapp.pebble.generated.resources.contact_dev_read_failed
import coreapp.pebble.generated.resources.contact_dev_send
import coreapp.pebble.generated.resources.contact_dev_sent
import coreapp.pebble.generated.resources.contact_dev_sign_in_again
import coreapp.pebble.generated.resources.contact_dev_sign_in_required
import coreapp.pebble.generated.resources.contact_dev_title
import coreapp.util.generated.resources.back
import coreapp.util.generated.resources.close
import coreapp.util.generated.resources.remove
import coreapp.util.generated.resources.sign_in
import coredevices.pebble.services.ContactAttachment
import coredevices.pebble.services.ContactDeveloperApi
import coredevices.pebble.services.ContactResult
import coredevices.ui.SignInDialog
import coredevices.util.Platform
import coredevices.util.isIOS
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.io.readByteArray
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import rememberOpenDocumentLauncher
import rememberOpenPhotoLauncher
import coreapp.util.generated.resources.Res as UtilRes

private const val MAX_BODY = 5000
private const val MAX_ATTACHMENTS = 3
private const val MAX_ATTACHMENT_BYTES = 2L * 1024 * 1024

@Composable
fun ContactDeveloperScreen(
    coreNav: CoreNav,
    appId: String,
    appTitle: String,
) {
    val api = koinInject<ContactDeveloperApi>()
    val platform = koinInject<Platform>()
    val scope = rememberCoroutineScope()
    val logger = remember { Logger.withTag("ContactDeveloperScreen") }

    val signedIn by Firebase.auth.authStateChanged
        .map { it != null }
        .collectAsState(Firebase.auth.currentUser != null)

    var showSignInDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<ContactAttachment>>(emptyList()) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun handlePicked(picked: List<DocumentAttachment>?) {
        if (picked == null) return
        val capped = picked.take(MAX_ATTACHMENTS)
        // The picker's reported size can be unreliable on Android (uses
        // InputStream.available()); read bytes eagerly so we can enforce
        // the 2 MB cap on the actual content, and so retries reuse the data.
        val prepared = mutableListOf<ContactAttachment>()
        for (doc in capped) {
            val bytes = try {
                doc.source.use { it.readByteArray() }
            } catch (e: Exception) {
                logger.w(e) { "Failed to read picked attachment: ${doc.fileName}" }
                scope.launch { error = getString(Res.string.contact_dev_read_failed, doc.fileName) }
                return
            }
            if (bytes.size > MAX_ATTACHMENT_BYTES) {
                scope.launch { error = getString(Res.string.contact_dev_attachment_too_large, doc.fileName) }
                return
            }
            prepared += ContactAttachment(
                fileName = doc.fileName,
                mimeType = doc.mimeType,
                bytes = bytes,
            )
        }
        error = null
        attachments = prepared
    }

    val pickAttachments = rememberOpenDocumentLauncher(::handlePicked)
    val pickPhotos = if (platform.isIOS) {
        rememberOpenPhotoLauncher(::handlePicked)
    } else {
        null
    }

    fun submit() {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) {
            scope.launch { error = getString(Res.string.contact_dev_enter_message) }
            return
        }
        if (trimmed.length > MAX_BODY) {
            scope.launch { error = getString(Res.string.contact_dev_message_too_long, trimmed.length, MAX_BODY) }
            return
        }
        sending = true
        error = null
        scope.launch {
            val result = try {
                api.sendMessage(appId, trimmed, attachments)
            } catch (e: Exception) {
                logger.e(e) { "sendMessage threw" }
                ContactResult.NetworkError
            }
            when (result) {
                ContactResult.Success -> sent = true
                ContactResult.NotSignedIn -> error = getString(Res.string.contact_dev_sign_in_again)
                ContactResult.EmailNotVerified ->
                    error = getString(Res.string.contact_dev_email_not_verified)
                is ContactResult.NotContactable -> error = result.message
                is ContactResult.RateLimited -> error = result.message
                is ContactResult.BadRequest -> error = result.message
                ContactResult.NetworkError -> error = getString(Res.string.contact_dev_network_error)
                is ContactResult.ServerError -> error = result.message
            }
            sending = false
        }
    }

    if (showSignInDialog) {
        SignInDialog(onDismiss = { showSignInDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.contact_dev_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = coreNav::goBack,
                        enabled = !sending,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(UtilRes.string.back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    stringResource(Res.string.contact_dev_intro, appTitle),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))

                when {
                    sent -> {
                        Text(
                            stringResource(Res.string.contact_dev_sent),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = coreNav::goBack,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(UtilRes.string.close))
                        }
                    }

                    !signedIn -> {
                        Text(
                            stringResource(Res.string.contact_dev_sign_in_required),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { showSignInDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(UtilRes.string.sign_in))
                        }
                    }

                    else -> {
                        OutlinedTextField(
                            value = message,
                            onValueChange = { if (it.length <= MAX_BODY) message = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            label = { Text(stringResource(Res.string.contact_dev_message)) },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                            ),
                            enabled = !sending,
                        )
                        Text(
                            stringResource(Res.string.contact_dev_characters_remaining, MAX_BODY - message.length),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(Res.string.contact_dev_attach_hint, MAX_ATTACHMENTS),
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { pickAttachments(listOf("*/*")) },
                                enabled = !sending && attachments.size < MAX_ATTACHMENTS,
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            ) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(Res.string.contact_dev_add_files))
                            }
                            if (pickPhotos != null) {
                                OutlinedButton(
                                    onClick = { pickPhotos() },
                                    enabled = !sending && attachments.size < MAX_ATTACHMENTS,
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                ) {
                                    Icon(
                                        Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                    )
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text(stringResource(Res.string.contact_dev_add_photos))
                                }
                            }
                        }
                        attachments.forEachIndexed { index, file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    stringResource(Res.string.contact_dev_attachment_size, file.fileName, file.bytes.size / 1024),
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .padding(start = 4.dp),
                                )
                                Spacer(Modifier.size(0.dp))
                                IconButton(
                                    onClick = {
                                        attachments =
                                            attachments.toMutableList().also { it.removeAt(index) }
                                    },
                                    enabled = !sending,
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(UtilRes.string.remove),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }

                        if (error != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                error!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                            )
                        }

                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = ::submit,
                            enabled = !sending && message.trim().isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (sending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(stringResource(Res.string.contact_dev_send))
                            }
                        }
                    }
                }
            }
        }
    }
}
