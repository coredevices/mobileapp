package coredevices.pebble.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.SpaceEvenly
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coredevices.pebble.rememberLibPebble
import coredevices.ui.ShowOnceTooltipBox
import io.rebble.libpebblecommon.database.dao.ContactWithCount
import io.rebble.libpebblecommon.database.entity.MuteState
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

class ContactsViewModel(
) : ViewModel() {
    val onlyNotified = mutableStateOf(false)
    val searchState = SearchState()
}

@Composable
fun NotificationContactsScreen(topBarParams: TopBarParams, nav: NavBarNav, gotoDefaultTab: () -> Unit) {
    val viewModel = koinViewModel<ContactsViewModel>()
    val libPebble = rememberLibPebble()
    val items = remember(
        viewModel.searchState.query,
        viewModel.onlyNotified.value,
    ) {
        Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = true),
            pagingSourceFactory = {
                libPebble.getContactsWithCounts(viewModel.searchState.query, viewModel.onlyNotified.value)
            }
        ).flow
    }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        topBarParams.searchAvailable(viewModel.searchState)
        launch {
            topBarParams.scrollToTop.collect {
                if (listState.firstVisibleItemIndex > 0) {
                    listState.animateScrollToItem(0)
                } else {
                    gotoDefaultTab()
                }
            }
        }
    }
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val contacts = items.collectAsLazyPagingItems()
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = SpaceEvenly,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    ElevatedFilterChip(
                        onClick = {
                            viewModel.onlyNotified.value =
                                !viewModel.onlyNotified.value
                        },
                        label = {
                            Text("Notified only")
                        },
                        selected = viewModel.onlyNotified.value,
                        leadingIcon = if (viewModel.onlyNotified.value) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = "Done icon",
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else {
                            null
                        },
                        elevation = FilterChipDefaults.filterChipElevation(elevation = 2.dp),
                        colors = FilterChipDefaults.elevatedFilterChipColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                    )
                }
            }

            LazyColumn(state = listState) {
                items(
                    count = contacts.itemCount,
                    key = contacts.itemKey { it.contact.lookupKey }
                ) { index ->
                    val contact = contacts[index]
                    if (contact != null) {
                        ContactCard(entry = contact, nav = nav, firstOrOnlyItem = index == 0)
                    }
                }
            }
        }
    }
}

@Composable
fun ContactNotificationViewerScreen(
    topBarParams: TopBarParams,
    nav: NavBarNav,
    contactId: String,
) {
    LaunchedEffect(Unit) {
        topBarParams.searchAvailable(null)
        topBarParams.actions {}
        topBarParams.title("Contact Notifications")
    }
    val libPebble = rememberLibPebble()
    val flow = remember {
        libPebble.getContact(contactId)
    }
    val contact by flow.collectAsState(null)
    contact?.let { entry ->
        Column {
            ContactCard(entry = entry, nav = nav, firstOrOnlyItem = true)
            SelectVibePatternOrNone(
                currentPattern = entry.contact.vibePatternName,
                onChangePattern = { pattern ->
                    libPebble.updateContactState(
                        contactId = entry.contact.lookupKey,
                        muteState = entry.contact.muteState,
                        vibePatternName = pattern?.name,
                    )
                },
            )
            NotificationHistoryList(
                packageName = null,
                channelId = null,
                contactId = contactId,
                limit = 25,
                showAppIcon = true,
            )
        }
    }
}

@Composable
fun ContactCard(entry: ContactWithCount, nav: NavBarNav, firstOrOnlyItem: Boolean) {
    val libPebble = rememberLibPebble()
    val muted = remember(entry.contact.muteState) { entry.contact.muteState == MuteState.Always }
    val favorite = remember(entry.contact.muteState) { entry.contact.muteState == MuteState.Exempt }
    ListItem(
        modifier = Modifier.clickable {
            nav.navigateTo(PebbleNavBarRoutes.ContactNotificationViewerRoute(entry.contact.lookupKey))
        },
        leadingContent = { ContactImage(entry, modifier = Modifier.width(55.dp).height(55.dp)) },
        headlineContent = {
            Column {
                Text(entry.contact.name, fontSize = 17.sp)
                // TODO add phone number?
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.count > 0) {
                        Badge(modifier = Modifier.padding(horizontal = 5.dp)) {
                            Text("${entry.count}")
                        }
                    }
                    Icon(
                        Icons.Default.MoreHoriz,
                        "Details",
                        modifier = Modifier.padding(start = 4.dp, end = 10.dp)
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = !muted,
                    onCheckedChange = {
                        val toggledState = if (muted) MuteState.Never else MuteState.Always
                        libPebble.updateContactState(
                            contactId = entry.contact.lookupKey,
                            muteState = toggledState,
                            vibePatternName = entry.contact.vibePatternName,
                        )
                    },
                    enabled = !favorite,
                )
                ShowOnceTooltipBox(
                    settingsKey = "shown_starred_contact_tooltip",
                    persistent = true,
                    firstOrOnlyItem = firstOrOnlyItem,
                    text = "Star a contact to always receive notifications from them, even if e.g. the app is muted",
                ) {
                    IconToggleButton(
                        checked = favorite,
                        onCheckedChange = { checked ->
                            val newState =
                                if (checked) MuteState.Exempt else MuteState.Never
                            libPebble.updateContactState(
                                contactId = entry.contact.lookupKey,
                                muteState = newState,
                                vibePatternName = entry.contact.vibePatternName,
                            )
                        },
                        enabled = !muted,
                    ) {
                        Icon(
                            if (favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Star"
                        )
                    }
                }
            }
        },
        shadowElevation = 2.dp,
    )
}

@Composable
fun ContactImage(entry: ContactWithCount, modifier: Modifier) {
    val libPebble = rememberLibPebble()
    val icon by produceState<ImageBitmap?>(initialValue = null, entry.contact.lookupKey) {
        value = libPebble.getContactImage(entry.contact.lookupKey)
    }
    icon.let {
        if (it != null) {
            Box(modifier = modifier) {
                Image(
                    it,
                    contentDescription = entry.contact.name,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Box(modifier)
        }
    }
}