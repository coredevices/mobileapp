package coredevices.ring.ui.screens.settings.mcp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.settings_integration_unknown_error
import coreapp.ring.generated.resources.settings_mcp_add_title
import coreapp.ring.generated.resources.settings_mcp_auth_header
import coreapp.ring.generated.resources.settings_mcp_auth_optional
import coreapp.ring.generated.resources.settings_mcp_description
import coreapp.ring.generated.resources.settings_mcp_description_help
import coreapp.ring.generated.resources.settings_mcp_description_placeholder
import coreapp.ring.generated.resources.settings_mcp_edit_title
import coreapp.ring.generated.resources.settings_mcp_fetching
import coreapp.ring.generated.resources.settings_mcp_name_invalid
import coreapp.ring.generated.resources.settings_mcp_prompts
import coreapp.ring.generated.resources.settings_mcp_server_title
import coreapp.ring.generated.resources.settings_mcp_streamable
import coreapp.ring.generated.resources.settings_mcp_unknown_error
import coreapp.ring.generated.resources.settings_mcp_unreachable
import coreapp.ring.generated.resources.settings_mcp_url
import coreapp.util.generated.resources.cancel
import coreapp.util.generated.resources.delete
import coreapp.util.generated.resources.name
import coreapp.util.generated.resources.save
import coredevices.indexai.data.entity.mcp.HttpMcpServerEntity
import coredevices.mcp.client.HttpMcpIntegration
import coredevices.mcp.client.HttpMcpProtocol
import coredevices.mcp.client.isValidIntegrationName
import coredevices.mcp.data.McpPrompt
import coredevices.ring.ui.PreviewWrapper
import coredevices.ui.M3Dialog
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import coreapp.util.generated.resources.Res as UtilRes

@Composable
internal fun HttpServerEditDialog(
    initialServer: HttpMcpServerEntity?,
    onDismiss: () -> Unit,
    onConfirm: (HttpMcpServerEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initialServer?.name ?: "") }
    var url by remember { mutableStateOf(initialServer?.url ?: "") }
    var description by remember { mutableStateOf(initialServer?.description ?: "") }
    var streamable by remember { mutableStateOf(initialServer?.streamable ?: false) }
    var authHeader by remember { mutableStateOf(initialServer?.authHeader ?: "") }
    var showAuthSection by remember { mutableStateOf(initialServer?.authHeader?.isNotBlank() == true) }
    var cachedTitle by remember { mutableStateOf(initialServer?.cachedTitle ?: "") }
    // The endpoint (url, protocol, auth) that `cachedTitle` was fetched for. A title only
    // describes that exact endpoint, so if these fields change we must not keep it.
    var titleEndpoint by remember {
        mutableStateOf(
            initialServer?.let { Triple(it.url, it.streamable, it.authHeader ?: "") }
        )
    }
    var isFetchingTitle by remember { mutableStateOf(false) }
    var serverContactable by remember { mutableStateOf<Boolean?>(null) }
    // The reason the server couldn't be reached, surfaced to the user (null when reachable/unknown).
    var fetchError by remember { mutableStateOf<String?>(null) }
    var availablePrompts by remember { mutableStateOf<List<McpPrompt>>(emptyList()) }
    var selectedPrompts by remember { mutableStateOf(initialServer?.includedPrompts?.toSet() ?: emptySet()) }
    var showPromptsSection by remember { mutableStateOf(initialServer?.includedPrompts?.isNotEmpty() == true) }

    // Debounced fetch of server title and prompts when URL, protocol, or auth header changes
    LaunchedEffect(url, streamable, authHeader) {
        if (url.isBlank()) {
            cachedTitle = ""
            availablePrompts = emptyList()
            serverContactable = null
            fetchError = null
            return@LaunchedEffect
        }
        delay(500) // Debounce
        isFetchingTitle = true
        try {
            val protocol = if (streamable) HttpMcpProtocol.Streaming else HttpMcpProtocol.Sse
            val integration = HttpMcpIntegration(
                name = "title-fetch",
                implementation = Implementation(name = "CoreApp", version = "1.0.0"),
                url = url,
                protocol = protocol,
                authHeader = authHeader.ifBlank { null }
            )
            integration.connect()
            cachedTitle = integration.title ?: ""
            titleEndpoint = Triple(url, streamable, authHeader)
            availablePrompts = integration.listPrompts()
            integration.close()
            serverContactable = true
            fetchError = null
        } catch (e: Exception) {
            Logger.withTag("HttpServerEditDialog").w("Failed to fetch MCP server title", e)
            // Keep the cached title only if the endpoint is unchanged (server just momentarily
            // unreachable); if the user edited url/protocol/auth, the old title is stale for the
            // new endpoint and must not be shown or saved.
            if (Triple(url, streamable, authHeader) != titleEndpoint) {
                cachedTitle = ""
            }
            availablePrompts = emptyList()
            serverContactable = false
            fetchError = e.message ?: e::class.simpleName ?: getString(Res.string.settings_integration_unknown_error)
        } finally {
            isFetchingTitle = false
        }
    }

    val isEditing = initialServer != null
    // Only Name and URL are required. Contactability is advisory (surfaced as an error below)
    // but must not gate saving: a server may be momentarily down, need auth entered here, or
    // simply not respond to the title probe while still being a valid entry to create.
    val nameIsValid = name.isBlank() || isValidIntegrationName(name)
    val canSave = name.isNotBlank() && nameIsValid && url.isNotBlank()

    M3Dialog(
        onDismissRequest = onDismiss,
        scrollableContent = true,
        title = {
            Text(stringResource(if (isEditing) Res.string.settings_mcp_edit_title else Res.string.settings_mcp_add_title))
        },
        buttons = {
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(UtilRes.string.delete))
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text(stringResource(UtilRes.string.cancel))
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = {
                    onConfirm(
                        HttpMcpServerEntity(
                            id = initialServer?.id ?: 0L,
                            cachedTitle = cachedTitle,
                            name = name,
                            url = url,
                            streamable = streamable,
                            authHeader = authHeader.ifBlank { null },
                            includedPrompts = selectedPrompts.toList(),
                            enabled = initialServer?.enabled ?: true,
                            description = description.trim().ifBlank { null },
                        )
                    )
                },
                enabled = canSave
            ) {
                Text(stringResource(UtilRes.string.save))
            }
        }
    ) {
        Column {
            // Resolved inside the dialog: dialogs have their own focus manager.
            val focusManager = LocalFocusManager.current
            val dismissKeyboard = KeyboardActions(onDone = { focusManager.clearFocus() })
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(UtilRes.string.name)) },
                isError = !nameIsValid,
                supportingText = when {
                    !nameIsValid -> {
                        { Text(stringResource(Res.string.settings_mcp_name_invalid)) }
                    }
                    else -> {
                        { Text("") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = dismissKeyboard,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(Res.string.settings_mcp_url)) },
                isError = serverContactable == false,
                supportingText = when {
                    isFetchingTitle -> {
                        { Text(stringResource(Res.string.settings_mcp_fetching)) }
                    }
                    serverContactable == false -> {
                        { Text(stringResource(Res.string.settings_mcp_unreachable, fetchError ?: stringResource(Res.string.settings_mcp_unknown_error))) }
                    }
                    cachedTitle.isNotBlank() -> {
                        { Text(stringResource(Res.string.settings_mcp_server_title, cachedTitle)) }
                    }
                    else -> {
                        { Text("") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = dismissKeyboard,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(Res.string.settings_mcp_description)) },
                placeholder = { Text(stringResource(Res.string.settings_mcp_description_placeholder)) },
                supportingText = { Text(stringResource(Res.string.settings_mcp_description_help)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = !streamable,
                    onClick = { streamable = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("SSE")
                }
                SegmentedButton(
                    selected = streamable,
                    onClick = { streamable = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(stringResource(Res.string.settings_mcp_streamable))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAuthSection = !showAuthSection },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (showAuthSection) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = null
                )
                Text(stringResource(Res.string.settings_mcp_auth_optional))
            }
            AnimatedVisibility(visible = showAuthSection) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = authHeader,
                        onValueChange = { authHeader = it },
                        label = { Text(stringResource(Res.string.settings_mcp_auth_header)) },
                        placeholder = { Text("Bearer token123...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = dismissKeyboard,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (availablePrompts.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPromptsSection = !showPromptsSection },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (showPromptsSection) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = null
                    )
                    Text(stringResource(Res.string.settings_mcp_prompts, selectedPrompts.size, availablePrompts.size))
                }
                AnimatedVisibility(visible = showPromptsSection) {
                    Column {
                        availablePrompts.forEach { prompt ->
                            val description = prompt.description
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPrompts = if (prompt.name in selectedPrompts) {
                                            selectedPrompts - prompt.name
                                        } else {
                                            selectedPrompts + prompt.name
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = prompt.name in selectedPrompts,
                                    onCheckedChange = { checked ->
                                        selectedPrompts = if (checked) {
                                            selectedPrompts + prompt.name
                                        } else {
                                            selectedPrompts - prompt.name
                                        }
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(prompt.title ?: prompt.name)
                                    if (description != null) {
                                        Text(
                                            text = description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun HttpServerEditDialogNewPreview() {
    PreviewWrapper {
        HttpServerEditDialog(
            initialServer = null,
            onDismiss = {},
            onConfirm = {},
            onDelete = null
        )
    }
}

@Preview
@Composable
private fun HttpServerEditDialogEditPreview() {
    PreviewWrapper {
        HttpServerEditDialog(
            initialServer = previewServer,
            onDismiss = {},
            onConfirm = {},
            onDelete = {}
        )
    }
}

private val previewServer = HttpMcpServerEntity(
    id = 1L,
    cachedTitle = "My MCP Server",
    name = "my-server",
    url = "https://example.com/mcp",
    streamable = false,
    authHeader = "Bearer 123abc",
    includedPrompts = listOf("default"),
    description = "Home automation: lights, thermostat and the kids' internet",
)
