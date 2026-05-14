package coredevices.pebble.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import coredevices.pebble.services.ContactAttachment
import coredevices.pebble.services.ContactDeveloperApi
import coredevices.pebble.services.ContactResult
import coredevices.ui.SignInDialog
import kotlinx.io.readByteArray
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import rememberOpenDocumentLauncher

private const val MAX_BODY = 5000
private const val MAX_ATTACHMENTS = 3
private const val MAX_ATTACHMENT_BYTES = 2L * 1024 * 1024

@Composable
fun ContactDeveloperDialog(
    appId: String,
    appTitle: String,
    onDismiss: () -> Unit,
) {
    val api = koinInject<ContactDeveloperApi>()
    val scope = rememberCoroutineScope()
    val logger = remember { Logger.withTag("ContactDeveloperDialog") }

    val signedIn by Firebase.auth.authStateChanged
        .map { it != null }
        .collectAsState(Firebase.auth.currentUser != null)

    var showSignInDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<ContactAttachment>>(emptyList()) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickAttachments = rememberOpenDocumentLauncher { picked ->
        if (picked == null) return@rememberOpenDocumentLauncher
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
                error = "Could not read \"${doc.fileName}\"."
                return@rememberOpenDocumentLauncher
            }
            if (bytes.size > MAX_ATTACHMENT_BYTES) {
                error = "Attachment \"${doc.fileName}\" exceeds 2 MB."
                return@rememberOpenDocumentLauncher
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

    fun submit() {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) {
            error = "Please enter a message."
            return
        }
        if (trimmed.length > MAX_BODY) {
            error = "Message is too long (${trimmed.length}/$MAX_BODY)."
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
                ContactResult.NotSignedIn -> error = "Please sign in again."
                ContactResult.EmailNotVerified ->
                    error = "Your email address must be verified before contacting developers."
                is ContactResult.NotContactable -> error = result.message
                is ContactResult.RateLimited -> error = result.message
                is ContactResult.BadRequest -> error = result.message
                ContactResult.NetworkError -> error = "Network error. Please try again."
                is ContactResult.ServerError -> error = result.message
            }
            sending = false
        }
    }

    if (showSignInDialog) {
        SignInDialog(onDismiss = { showSignInDialog = false })
    }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Contact the developer") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Send a message about $appTitle. The developer will receive it via email and can reply to you. Your email address is not shared with them.",
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))

                when {
                    sent -> {
                        Text(
                            "Message sent. The developer will receive an email and may reply to you directly.",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    !signedIn -> {
                        Text(
                            "You need to sign in to contact the developer.",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    else -> {
                        OutlinedTextField(
                            value = message,
                            onValueChange = { if (it.length <= MAX_BODY) message = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            label = { Text("Message") },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                            ),
                            enabled = !sending,
                        )
                        Text(
                            "${MAX_BODY - message.length} characters remaining",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        Spacer(Modifier.height(12.dp))
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
                            Text("Add files (optional, up to $MAX_ATTACHMENTS, 2 MB each)")
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
                                    "${file.fileName} — ${file.bytes.size / 1024} KB",
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
                                        contentDescription = "Remove",
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
                    }
                }
            }
        },
        confirmButton = {
            when {
                sent -> {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }

                !signedIn -> {
                    Button(onClick = { showSignInDialog = true }) { Text("Sign in") }
                }

                else -> {
                    Button(
                        onClick = ::submit,
                        enabled = !sending && message.trim().isNotEmpty(),
                    ) {
                        if (sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Send message")
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (!sent) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !sending,
                ) { Text("Cancel") }
            }
        },
    )
}
