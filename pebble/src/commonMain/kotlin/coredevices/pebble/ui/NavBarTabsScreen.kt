package coredevices.pebble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import coredevices.pebble.rememberLibPebble
import coredevices.util.CoreConfigHolder
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun NavBarTabsScreen(topBarParams: TopBarParams) {
    LaunchedEffect(Unit) {
        topBarParams.searchAvailable(null)
        topBarParams.actions {}
        topBarParams.title("Bottom Bar")
    }
    val healthSettings by rememberLibPebble().healthSettings.collectAsState(null)
    val healthEnabled = healthSettings?.trackingEnabled ?: return
    val coreConfigHolder: CoreConfigHolder = koinInject()
    val coreConfig by coreConfigHolder.config.collectAsState()
    // Unavailable tabs stay in the list, unshown, so they keep their place relative to their neighbours.
    val allTabs = remember { WatchHomeNavTab.orderedTabs(coreConfigHolder.config.value).toMutableStateList() }
    val tabs = allTabs.filter { WatchHomeNavTab.isAvailable(it, coreConfig, healthEnabled) }
    val barTabs = WatchHomeNavTab.barTabs(coreConfig, tabs)
    val listState = rememberLazyListState()
    // Keyed lookups: the list has headers, and hidden tabs aren't shown.
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = allTabs.indexOfFirst { it.name == from.key }
        val toIndex = allTabs.indexOfFirst { it.name == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            allTabs.add(toIndex, allTabs.removeAt(fromIndex))
        }
    }
    val save = { coreConfigHolder.update(coreConfigHolder.config.value.copy(navBarTabs = allTabs.map { it.name })) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item(key = "barHeader") { SectionHeader("In bar") }
        tabItems(barTabs, reorderState, save)
        if (tabs.size > barTabs.size) {
            item(key = "moreHeader") { SectionHeader("Under More") }
        }
        tabItems(tabs.drop(barTabs.size), reorderState, save)
        item(key = "gap") { Spacer(Modifier.height(16.dp)) }
        item(key = "count") {
            basicSettingsDropdownItem(
                title = "Items in bar",
                topLevelType = TopLevelType.Phone,
                section = Section.Appearance,
                items = (2..WatchHomeNavTab.MAX_BAR_SLOTS).toList(),
                selectedItem = WatchHomeNavTab.barSlots(coreConfig),
                onItemSelected = { coreConfigHolder.update(coreConfig.copy(navBarSlots = it)) },
                itemText = { "$it" },
            ).Item()
        }
        item(key = "startOnFirst") {
            basicSettingsToggleItem(
                title = "Start on first tab",
                description = "Otherwise the app opens to the most useful tab for your devices",
                topLevelType = TopLevelType.Phone,
                section = Section.Appearance,
                checked = coreConfig.startOnFirstNavBarTab,
                onCheckChanged = { coreConfigHolder.update(coreConfig.copy(startOnFirstNavBarTab = it)) },
            ).Item()
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

/** Draws its slice of a settings-group card per item, so each tab can still be dragged between cards. */
private fun LazyListScope.tabItems(
    tabs: List<WatchHomeNavTab>,
    reorderState: ReorderableLazyListState,
    onDragStopped: () -> Unit,
) {
    itemsIndexed(tabs, key = { _, tab -> tab.name }) { index, tab ->
        ReorderableItem(reorderState, key = tab.name) { isDragging ->
            val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
            val first = if (index == 0 || isDragging) 16.dp else 0.dp
            val last = if (index == tabs.lastIndex || isDragging) 16.dp else 0.dp
            val shape = RoundedCornerShape(topStart = first, topEnd = first, bottomStart = last, bottomEnd = last)
            Column(
                Modifier.padding(horizontal = 12.dp)
                    .shadow(if (isDragging) 4.dp else 0.dp, shape, clip = true)
                    .background(cardColor)
            ) {
                if (index > 0 && !isDragging) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.background)
                }
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = cardColor),
                    leadingContent = { Icon(tab.icon, contentDescription = null) },
                    headlineContent = { Text(stringResource(tab.title)) },
                    trailingContent = {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Reorder",
                            modifier = Modifier.draggableHandle(onDragStopped = { onDragStopped() }),
                        )
                    },
                )
            }
        }
    }
}
