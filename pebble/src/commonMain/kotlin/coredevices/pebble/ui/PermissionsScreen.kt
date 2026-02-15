package coredevices.pebble.ui

import PlatformUiContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import coredevices.util.PermissionRequester
import coredevices.util.PermissionResult
import coredevices.util.description
import coredevices.util.name
import coredevices.util.rememberUiContext
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val logger = Logger.withTag("PermissionsScreen")

@Composable
fun PermissionsScreen(navBarNav: NavBarNav, topBarParams: TopBarParams) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        LaunchedEffect(Unit) {
            topBarParams.searchAvailable(null)
            topBarParams.actions {
            }
            topBarParams.title("Permissions")
        }
        val permissionRequester: PermissionRequester = koinInject()

        val missingPermissions by permissionRequester.missingPermissions.collectAsState(emptySet())
        if (missingPermissions.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(15.dp).fillMaxWidth(),
            ) {
                ElevatedCard(
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 6.dp
                    ),
                    modifier = Modifier.padding(15.dp),
                ) {
                    Text("All permissions granted!", modifier = Modifier.padding(15.dp))
                }
                return
            }
        }

        val scope = rememberCoroutineScope()
        val missingPermissionsList = remember(missingPermissions) { missingPermissions.toList() }
        val uiContext = rememberUiContext()
        if (uiContext == null) {
            logger.e { "uiContext is null" }
            return
        }

        LazyColumn {
            items(missingPermissionsList, key = { it.name() }) { permission ->
                ListItem(
                    headlineContent = {
                        Text(text = permission.name())
                    },
                    supportingContent = {
                        Text(text = permission.description())
                    },
                    modifier = Modifier.clickable {
                        scope.launch {
                            val granted =
                                permissionRequester.requestPermission(permission, uiContext)
                            logger.d { "$permission granted = $granted" }
                            if (granted == PermissionResult.RejectedForever) {
                                permissionRequester.openPermissionsScreen(uiContext)
                            }
                        }
                    },
                )
            }
        }
    }
}
