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
            topBarParams.searchAvailable(false)
            topBarParams.actions {}
//        topBarParams.title("Notification History")
            topBarParams.canGoBack(false)
            topBarParams.goBack.collect {
            }
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