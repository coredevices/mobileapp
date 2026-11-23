package coredevices.ring.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.settings_notion_page_description
import coreapp.ring.generated.resources.settings_notion_page_load_failed
import coreapp.ring.generated.resources.settings_notion_page_none
import coreapp.ring.generated.resources.settings_notion_page_title
import coreapp.util.generated.resources.cancel
import coreapp.util.generated.resources.ok
import coredevices.ring.agent.integrations.NotionIntegration
import coredevices.ui.M3Dialog
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import coreapp.util.generated.resources.Res as UtilRes

/**
 * Lets the user pick which Notion page to-do block is placed
 */
@Composable
fun NotionPageDialog(onDismiss: () -> Unit) {
    val notion = koinInject<NotionIntegration>()
    val scope = rememberCoroutineScope()
    var pages by remember { mutableStateOf<List<NotionIntegration.NotionPage>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val loaded = notion.listPages()
            selectedId = notion.selectedPageId() ?: loaded.firstOrNull()?.id
            pages = loaded
        } catch (e: Throwable) {
            error = e.message ?: getString(Res.string.settings_notion_page_load_failed)
        }
    }

    M3Dialog(
        onDismissRequest = onDismiss,
        // The page list can be long; scroll it so the buttons stay reachable.
        scrollableContent = true,
        title = { Text(stringResource(Res.string.settings_notion_page_title)) },
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(UtilRes.string.cancel)) }
            TextButton(
                enabled = selectedId != null && !saving,
                onClick = {
                    val pageId = selectedId ?: return@TextButton
                    saving = true
                    scope.launch {
                        notion.selectPage(pageId)
                        onDismiss()
                    }
                }
            ) { Text(stringResource(UtilRes.string.ok)) }
        }
    ) {
        Column {
            Text(
                stringResource(Res.string.settings_notion_page_description),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            val loadedPages = pages
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                loadedPages == null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                loadedPages.isEmpty() -> Text(stringResource(Res.string.settings_notion_page_none))
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    loadedPages.forEach { page ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedId = page.id }
                        ) {
                            RadioButton(
                                selected = selectedId == page.id,
                                onClick = { selectedId = page.id }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(page.title)
                        }
                    }
                }
            }
        }
    }
}
