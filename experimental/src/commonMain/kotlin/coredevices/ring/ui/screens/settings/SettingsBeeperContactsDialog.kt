package coredevices.ring.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coredevices.ui.M3Dialog
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import coredevices.util.rememberUiContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

expect class SettingsBeeperContactsDialogViewModel(): ViewModel {
    fun getContacts(query: String?): PagingSource<Int, SettingsBeeperContact>
    val approvedIds: StateFlow<Set<String>>
    val approvedContacts: StateFlow<List<SettingsBeeperContact>>
    val hasPermission: StateFlow<Boolean>
    fun addContact(roomId: String, contact: SettingsBeeperContact)
    fun removeContact(roomId: String)
    fun setNickname(roomId: String, nickname: String?)
    fun persist()
    fun refreshPermission()
    fun loadApprovedContacts()
}

data class SettingsBeeperContact(
    val id: String,
    val name: String,
    val protocol: String,
    val roomId: String? = null,
    val chatTitle: String? = null,
    val isGroupChat: Boolean = false,
    val lastMessageTimestamp: Long = 0L,
    val nickname: String? = null
)

@Composable
fun SettingsBeeperContactsDialog(
    onDismissRequest: () -> Unit
) {
    val viewModel = koinViewModel<SettingsBeeperContactsDialogViewModel>()
    val permissionRequester: PermissionRequester = koinInject()
    val uiContext = rememberUiContext()
    val scope = rememberCoroutineScope()
    val approvedIds by viewModel.approvedIds.collectAsState()
    val approvedContacts by viewModel.approvedContacts.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()
    var isAddMode by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.persist()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadApprovedContacts()
    }

    M3Dialog(
        onDismissRequest = {
            if (isAddMode) isAddMode = false else onDismissRequest()
        },
        icon = { Icon(Icons.Outlined.Contacts, null) },
        title = { Text(if (isAddMode) "Add Contact" else "Allowed Contacts") },
        buttons = {
            if (isAddMode) {
                TextButton(onClick = { isAddMode = false }) {
                    Text("Back")
                }
            } else {
                TextButton(onClick = onDismissRequest) {
                    Text("Done")
                }
            }
        }
    ) {
        Column {
            if (!hasPermission) {
                Text(
                    "Beeper permission is required to access your contacts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Button(
                    onClick = {
                        if (uiContext != null) {
                            scope.launch {
                                permissionRequester.requestPermission(Permission.Beeper, uiContext)
                                viewModel.refreshPermission()
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Grant Permission")
                }
            } else if (isAddMode) {
                AddContactView(viewModel, approvedIds)
            } else {
                ApprovedContactsView(viewModel, approvedContacts, onAdd = { isAddMode = true })
            }
        }
    }
}

@Composable
private fun ApprovedContactsView(
    viewModel: SettingsBeeperContactsDialogViewModel,
    approvedContacts: List<SettingsBeeperContact>,
    onAdd: () -> Unit
) {
    var editingNicknameFor by remember { mutableStateOf<String?>(null) }
    var nicknameText by remember { mutableStateOf("") }

    Text(
        "Contacts Index is allowed to message. Try saying \"Message Alice - What's up?\"",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    if (approvedContacts.isEmpty()) {
        Text(
            "No contacts added yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(),
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
        ) {
            items(approvedContacts, key = { it.roomId ?: it.id }) { contact ->
                val roomId = contact.roomId ?: contact.id
                val displayName = if (contact.isGroupChat) {
                    contact.chatTitle ?: contact.name
                } else {
                    contact.name
                }
                val identifier = extractIdentifier(contact.id, contact.protocol)
                val cleanProtocol = formatProtocol(contact.protocol)
                val protocolLine = buildString {
                    append(cleanProtocol)
                    if (identifier != null) append(" ($identifier)")
                }
                val isEditing = editingNicknameFor == roomId
                Column {
                    ListItem(
                        modifier = Modifier.clickable {
                            if (isEditing) {
                                editingNicknameFor = null
                            } else {
                                editingNicknameFor = roomId
                                nicknameText = contact.nickname ?: ""
                            }
                        },
                        headlineContent = { Text(displayName) },
                        supportingContent = {
                            Column {
                                Text(protocolLine)
                                if (contact.nickname != null && !isEditing) {
                                    Text(
                                        "Nickname: ${contact.nickname}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                viewModel.removeContact(roomId)
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                    if (isEditing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            TextField(
                                value = nicknameText,
                                onValueChange = { nicknameText = it },
                                label = { Text("Nickname") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                viewModel.setNickname(
                                    roomId,
                                    nicknameText.trim().ifEmpty { null }
                                )
                                editingNicknameFor = null
                            }) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }

    Button(
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("  Add Contact", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun AddContactView(
    viewModel: SettingsBeeperContactsDialogViewModel,
    approvedIds: Set<String>
) {
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var dmOnly by remember { mutableStateOf(true) }

    LaunchedEffect(query) {
        delay(300)
        debouncedQuery = query
    }

    val pager = remember(debouncedQuery) {
        Pager(
            config = PagingConfig(pageSize = 50, enablePlaceholders = false),
            pagingSourceFactory = { viewModel.getContacts(debouncedQuery.ifBlank { null }) }
        ).flow
    }.collectAsLazyPagingItems()

    TextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Search") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Checkbox(
            checked = dmOnly,
            onCheckedChange = { dmOnly = it }
        )
        Text("DMs only", style = MaterialTheme.typography.bodySmall)
    }

    if (pager.loadState.refresh is LoadState.Loading) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
        ) {
            val filteredContacts = (0 until pager.itemCount).mapNotNull { pager[it] }
                .filter { !dmOnly || !it.isGroupChat }
            items(
                count = filteredContacts.size
            ) { index ->
                val contact = filteredContacts[index]
                val toggleKey = contact.roomId ?: contact.id
                val isApproved = approvedIds.contains(toggleKey)
                val displayName = if (contact.isGroupChat) {
                    contact.chatTitle ?: contact.name
                } else {
                    contact.name
                }
                val identifier = extractIdentifier(contact.id, contact.protocol)
                val cleanProtocol = formatProtocol(contact.protocol)
                val protocolLine = buildString {
                    append(if (contact.isGroupChat) "$cleanProtocol (group)" else cleanProtocol)
                    if (identifier != null) append(" ($identifier)")
                }
                val lastMessageText = if (contact.lastMessageTimestamp > 0) {
                    val dt = Instant.fromEpochMilliseconds(contact.lastMessageTimestamp)
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                    "Last message: ${dt.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${dt.dayOfMonth}, ${dt.year}"
                } else null

                ListItem(
                    modifier = Modifier.clickable {
                        if (isApproved) {
                            viewModel.removeContact(toggleKey)
                        } else {
                            viewModel.addContact(toggleKey, contact)
                        }
                    },
                    headlineContent = { Text(displayName) },
                    supportingContent = {
                        Column {
                            Text(protocolLine)
                            if (lastMessageText != null) {
                                Text(
                                    lastMessageText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    trailingContent = {
                        Checkbox(
                            checked = isApproved,
                            onCheckedChange = {
                                if (isApproved) {
                                    viewModel.removeContact(toggleKey)
                                } else {
                                    viewModel.addContact(toggleKey, contact)
                                }
                            }
                        )
                    }
                )
            }
            if (pager.loadState.append is LoadState.Loading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

/** Extract a human-readable identifier from a Beeper contact ID.
 *  e.g., "@whatsapp_14155086773:beeper.local" -> "+14155086773"
 *        "@telegram_926137720:beeper.local" -> "926137720"
 *        "@signal_f1531b64-...:beeper.local" -> null (UUID, not useful)
 */
private fun extractIdentifier(contactId: String, protocol: String): String? {
    // Only show phone number for WhatsApp
    if (!protocol.equals("whatsapp", ignoreCase = true)) return null
    // Format: @whatsapp_14155086773:beeper.local
    val withoutAt = contactId.removePrefix("@")
    val colonIdx = withoutAt.indexOf(':')
    val local = if (colonIdx >= 0) withoutAt.substring(0, colonIdx) else withoutAt
    val underscoreIdx = local.indexOf('_')
    if (underscoreIdx < 0) return null
    val identifier = local.substring(underscoreIdx + 1)
    if (identifier.startsWith("lid") || identifier.contains('-')) return null
    return if (identifier.all { it.isDigit() } && identifier.length >= 7) {
        "+$identifier"
    } else null
}

/** Clean up protocol name: remove trailing "go", capitalize */
private fun formatProtocol(protocol: String): String {
    if (protocol.isBlank()) return "Beeper"
    val cleaned = protocol.removeSuffix("go")
    return cleaned.replaceFirstChar { it.uppercase() }
}
