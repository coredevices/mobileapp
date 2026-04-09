package coredevices.ring.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.compose.collectAsLazyPagingItems
import coredevices.ring.database.Preferences
import coredevices.ui.M3Dialog
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

expect class SettingsBeeperContactsDialogViewModel(): ViewModel {
    fun getApprovedContacts(query: String?): PagingSource<Int, SettingsBeeperContact>
    fun setContactApproved(contactId: String, approved: Boolean)
}

data class SettingsBeeperContact(
    val id: String,
    val name: String,
    val protocol: String
)

@Composable
fun SettingsBeeperContactsDialog(
    onDismissRequest: () -> Unit
) {
    val viewModel = koinViewModel<SettingsBeeperContactsDialogViewModel>()
    val prefs = koinInject<Preferences>()
    val approvedContacts by prefs.approvedBeeperContacts.collectAsState()
    var query by remember { mutableStateOf("") }
    val pager = remember(query) {
        Pager(
            config = PagingConfig(pageSize = 50, enablePlaceholders = false),
            pagingSourceFactory = { viewModel.getApprovedContacts(query) }
        ).flow
    }.collectAsLazyPagingItems()
    M3Dialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Outlined.Contacts, null) },
        title = { Text("Approve Contacts") },
        buttons = {
            TextButton(onClick = onDismissRequest) {
                Text("Done")
            }
        }
    ) {
        Column {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search Contacts") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    pager.itemCount
                ) {
                    val contact = pager[it]
                    if (contact != null) {
                        ListItem(
                            headlineContent = { Text(contact.name) },
                            supportingContent = { Text(contact.protocol) },
                            trailingContent = {
                                Checkbox(
                                    checked = approvedContacts.contains(contact.id),
                                    onCheckedChange = { approved ->
                                        viewModel.setContactApproved(contact.id, approved)
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}