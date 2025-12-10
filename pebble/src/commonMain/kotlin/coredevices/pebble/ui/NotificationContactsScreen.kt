package coredevices.pebble.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import coredevices.pebble.rememberLibPebble
import coredevices.ui.ShowOnceTooltipBox
import io.rebble.libpebblecommon.database.dao.ContactWithCount
import io.rebble.libpebblecommon.database.entity.MuteState
import kotlinx.coroutines.flow.map

private val logger = Logger.withTag("NotificationContactsScreen")

@Composable
fun NotificationContactsScreen(topBarParams: TopBarParams, nav: NavBarNav) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        LaunchedEffect(Unit) {
            topBarParams.searchAvailable(true)
            topBarParams.actions {
            }
            topBarParams.canGoBack(false)
        }
        val libPebble = rememberLibPebble()
        val contactsFlow = remember { libPebble.getContactsWithCounts() }
        val contacts by contactsFlow.collectAsState(emptyList())

        val filteredContacts by remember(
            contacts,
            topBarParams.searchState,
        ) {
            derivedStateOf {
                contacts.asSequence().filter { entry ->
                    if (topBarParams.searchState.query.isNotEmpty()) {
                        entry.contact.name.contains(
                            topBarParams.searchState.query,
                            ignoreCase = true
                        )
                    } else {
                        true
                    }
                }.toList()
            }
        }

        LazyColumn {
            itemsIndexed(
                items = filteredContacts,
                key = { _, item -> item.contact.lookupKey },
            ) { index, entry ->
                ContactCard(entry = entry, nav = nav, firstOrOnlyItem = index == 0)
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
        topBarParams.searchAvailable(false)
        topBarParams.actions {}
        topBarParams.title("Contact Notifications")
        topBarParams.canGoBack(true)
        topBarParams.goBack.collect {
            nav.goBack()
        }
    }
    val libPebble = rememberLibPebble()
    val flow = remember {
        libPebble.getContactsWithCounts().map { entries ->
            entries.firstOrNull { it.contact.lookupKey == contactId }
        }
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