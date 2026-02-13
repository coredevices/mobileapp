package coredevices.pebble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

@Composable
fun NotificationHistoryScreen(topBarParams: TopBarParams, nav: NavBarNav) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        LaunchedEffect(Unit) {
            topBarParams.searchAvailable(null)
            topBarParams.actions {}
//        topBarParams.title("Notification History")
        }

        NotificationHistoryList(
            packageName = null,
            channelId = null,
            contactId = null,
            limit = 25,
            showAppIcon = true,
        )
    }
}