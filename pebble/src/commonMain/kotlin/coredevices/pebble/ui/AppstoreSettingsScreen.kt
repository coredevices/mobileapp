package coredevices.pebble.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coredevices.database.AppstoreCollection
import coredevices.database.AppstoreCollectionDao
import coredevices.database.AppstoreSource
import coredevices.database.AppstoreSourceDao
import coredevices.pebble.account.PebbleAccount
import coredevices.pebble.services.PebbleWebServices
import coredevices.ui.M3Dialog
import io.ktor.http.URLProtocol
import io.ktor.http.parseUrl
import io.rebble.libpebblecommon.locker.AppType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class AppstoreSettingsScreenViewModel(
    private val sourceDao: AppstoreSourceDao,
    private val collectionDao: AppstoreCollectionDao,
    private val pebbleWebServices: PebbleWebServices,
) : ViewModel() {
    val sources = sourceDao.getAllSources()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateCollections() {
        viewModelScope.launch {
            pebbleWebServices.fetchAppStoreHome(AppType.Watchapp, null, enabledOnly = false, useCache = false)
        }
        viewModelScope.launch {
            pebbleWebServices.fetchAppStoreHome(AppType.Watchface, null, enabledOnly = false, useCache = false)
        }
    }

    val collections: StateFlow<Map<AppstoreSource, Map<AppType, List<AppstoreCollection>>>?> =
        sourceDao.getAllSources().combine(collectionDao.getAllCollectionsFlow()) { sources, collections ->
            sources.associateWith { source ->
                collections.filter { it.sourceId == source.id }
                    .groupBy { it.type }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun removeSource(sourceId: Int) {
        viewModelScope.launch {
            sourceDao.deleteSourceById(sourceId)
        }
    }

    fun updateCollectionEnabled(collection: AppstoreCollection, isEnabled: Boolean) {
        viewModelScope.launch {
            collectionDao.insertOrUpdateCollection(
                collection.copy(enabled = isEnabled)
            )
        }
    }

    fun addSource(title: String, url: String) {
        viewModelScope.launch {
            val source = AppstoreSource(
                title = title,
                url = url
            )
            sourceDao.insertSource(source)
        }
    }
}

@Composable
fun AppstoreSettingsScreen(nav: NavBarNav, topBarParams: TopBarParams) {
    val uriHandler = LocalUriHandler.current
    val viewModel = koinViewModel<AppstoreSettingsScreenViewModel> { parametersOf(uriHandler) }
    val sources by viewModel.sources.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val pebbleAccount: PebbleAccount = koinInject()
    val sourceDao: AppstoreSourceDao = koinInject()
    val scope = rememberCoroutineScope()
    val pebbleLoggedIn = pebbleAccount.loggedIn
    LaunchedEffect(Unit) {
        topBarParams.searchAvailable(null)
        topBarParams.actions {
            TopBarIconButtonWithToolTip(
                onClick = {
                    viewModel.updateCollections()
                },
                icon = Icons.Filled.Refresh,
                description = "Refresh Collections",
            )
        }
        topBarParams.title("Appstore Sources")
    }

    AppstoreSettingsScreen(
        sources = sources,
        collections = collections,
        onSourceRemoved = viewModel::removeSource,
        onSourceAdded = viewModel::addSource,
        onSourceEnableChange = { sourceId, isEnabled ->
            scope.launch {
                if (sources.firstOrNull {
                        parseUrl(it.url)?.host?.endsWith("rebble.io") ?: false
                    }?.id == sourceId && isEnabled && pebbleLoggedIn.value == null) {
                        uriHandler.openUri(REBBLE_LOGIN_URI)
                } else {
                    sourceDao.setSourceEnabled(sourceId, isEnabled)
                }
            }
        },
        onCollectionEnabledChanged = viewModel::updateCollectionEnabled,
    )
}

@Composable
fun AppstoreSettingsScreen(
    sources: List<AppstoreSource>,
    collections: Map<AppstoreSource, Map<AppType, List<AppstoreCollection>>>?,
    onSourceRemoved: (Int) -> Unit,
    onSourceAdded: (title: String, url: String) -> Unit,
    onSourceEnableChange: (Int, Boolean) -> Unit,
    onCollectionEnabledChanged: (AppstoreCollection, Boolean) -> Unit,
) {
    var createSourceOpen by remember { mutableStateOf(false) }
    Scaffold(
        /*floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    createSourceOpen = true
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Source")
            }
        }*/
    ) { insets ->
        if (createSourceOpen) {
            CreateAppstoreSourceDialog(
                onDismissRequest = {
                    createSourceOpen = false
                },
                onSourceCreated = { title, url ->
                    onSourceAdded(title, url)
                    createSourceOpen = false
                }
            )
        }
        LazyColumn(Modifier.padding(insets)) {
            items(sources.size, { sources[it].id }) { i ->
                val source = sources[i]
                val collections = collections?.get(source)
                AppstoreSourceItem(
                    source = source,
                    collections = collections,
                    onRemove = onSourceRemoved,
                    onEnableChange = onSourceEnableChange,
                    onCollectionEnabledChanged = onCollectionEnabledChanged,
                )
            }
        }
    }
}

@Composable
fun AppstoreSourceItem(
    source: AppstoreSource,
    collections: Map<AppType, List<AppstoreCollection>>?,
    onRemove: (Int) -> Unit,
    onEnableChange: (Int, Boolean) -> Unit,
    onCollectionEnabledChanged: (AppstoreCollection, Boolean) -> Unit,
) {
    Column {
        ListItem(
            headlineContent = {
                Text(text = source.title)
            },
            supportingContent = {
                Text(text = source.url)
            },
            trailingContent = {
                Checkbox(
                    checked = source.enabled,
                    onCheckedChange = {
                        onEnableChange(source.id, it)
                    }
                )
                /*IconButton(
                    onClick = {
                        onRemove(source.id)
                    }
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Source")
                }*/
            },
            modifier = Modifier.clickable {
                onEnableChange(source.id, !source.enabled)
            }
        )
        if (collections != null) {
            if (collections.values.any { it.isNotEmpty() } && source.enabled) {
                collections.forEach { (appType, cols) ->
                    if (cols.isNotEmpty()) {
                        Text(
                            text = when (appType) {
                                AppType.Watchapp -> "Watchapp Collections"
                                AppType.Watchface -> "Watchface Collections"
                            },
                            modifier = Modifier.padding(start = 32.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                        cols.forEach { col ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = col.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                },
                                trailingContent = {
                                    Checkbox(
                                        col.enabled,
                                        onCheckedChange = {
                                            onCollectionEnabledChanged(col, it)
                                        }
                                    )
                                },
                                modifier = Modifier.height(40.dp)
                            )
                        }
                    }
                }
            }
        } else {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }

    }
}

@Composable
fun CreateAppstoreSourceDialog(
    onDismissRequest: () -> Unit,
    onSourceCreated: (title: String, url: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    M3Dialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(Icons.Filled.Link, contentDescription = null)
        },
        title = {
            Text("Add Appstore Source")
        },
        buttons = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text("Cancel")
            }
            TextButton(
                onClick = {
                    onSourceCreated(title, url)
                },
                enabled = title.isNotBlank() &&
                        url.isNotBlank() &&
                        parseUrl(url)?.protocolOrNull in setOf(URLProtocol.HTTP, URLProtocol.HTTPS)
            ) {
                Text("Add")
            }
        }
    ) {
        Column {
            TextField(title, onValueChange = { title = it }, label = { Text("Name") })
            Spacer(Modifier.height(8.dp))
            TextField(url, onValueChange = { url = it }, label = { Text("Source URL") })
        }
    }
}

@Preview
@Composable
fun AppstoreSettingsScreenPreview() {
    val sourceA = AppstoreSource(id = 1, title = "Source 1", url = "https://example.com/source1")
    val sourceB = AppstoreSource(id = 2, title = "Source 2", url = "https://example.com/source2")
    PreviewWrapper {
        AppstoreSettingsScreen(
            sources = listOf(
                sourceA,
                sourceB
            ),
            collections = mapOf(
                sourceA to mapOf(
                    AppType.Watchapp to listOf(
                        AppstoreCollection(
                            sourceId = sourceA.id,
                            title = "Featured Apps",
                            slug = "featured-apps",
                            type = AppType.Watchapp,
                            enabled = true
                        )
                    )
                )
            ),
            onSourceRemoved = {},
            onSourceAdded = { _, _ -> },
            onSourceEnableChange = { _, _ -> },
            onCollectionEnabledChanged = { _, _ -> },
        )
    }
}
