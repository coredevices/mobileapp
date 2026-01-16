package coredevices.pebble.ui

import CoreNav
import NoOpCoreNav
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import coredevices.pebble.account.BootConfigProvider
import coredevices.pebble.account.PebbleAccount
import coredevices.pebble.services.AppStoreHome
import coredevices.pebble.services.RealPebbleWebServices
import coredevices.pebble.services.StoreCollection
import coredevices.ui.M3Dialog
import io.ktor.http.URLProtocol
import io.ktor.http.parseUrl
import io.rebble.libpebblecommon.locker.AppType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class AppstoreSettingsScreenViewModel(
    private val sourceDao: AppstoreSourceDao,
    private val collectionDao: AppstoreCollectionDao,
    private val pebbleWebServices: RealPebbleWebServices,
) : ViewModel() {
    val sources = sourceDao.getAllSources()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    private var homeCacheFaces: List<Pair<AppstoreSource, AppStoreHome?>>? = null
    private var homeCacheApps: List<Pair<AppstoreSource, AppStoreHome?>>? = null

    private suspend fun getCachedFacesHome(): List<Pair<AppstoreSource, AppStoreHome?>> {
        if (homeCacheFaces == null) {
            homeCacheFaces =
                pebbleWebServices.fetchAppStoreHome(AppType.Watchface, null, enabledOnly = false)
        }
        return homeCacheFaces!!
    }

    private suspend fun getCachedAppsHome(): List<Pair<AppstoreSource, AppStoreHome?>> {
        if (homeCacheApps == null) {
            homeCacheApps =
                pebbleWebServices.fetchAppStoreHome(AppType.Watchapp, null, enabledOnly = false)
        }
        return homeCacheApps!!
    }

    val collections =
        sourceDao.getAllSources().combine(collectionDao.getAllCollections()) { sources, _ ->
            buildMap {
                val apps = viewModelScope.async(Dispatchers.IO) {
                    getCachedAppsHome().mapNotNull {
                        it.second?.let { home ->
                            it.first to collectionsToListItems(
                                it.first.id,
                                AppType.Watchapp,
                                home.collections
                            )
                        }
                    }
                }
                val faces = viewModelScope.async(Dispatchers.IO) {
                    getCachedFacesHome().mapNotNull {
                        it.second?.let { home ->
                            it.first to collectionsToListItems(
                                it.first.id,
                                AppType.Watchface,
                                home.collections
                            )
                        }
                    }
                }
                put(AppType.Watchapp, apps.await())
                put(AppType.Watchface, faces.await())
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    private suspend fun collectionsToListItems(
        sourceId: Int,
        type: AppType,
        list: List<StoreCollection>
    ): List<AppstoreCollection> {
        return list.map { col ->
            collectionDao.getCollection(sourceId, col.slug, type)
                ?: col.toAppstoreCollection(
                    sourceId,
                    type,
                    sourceId == sources.first { it.isNotEmpty() }.first().id
                )
        }
    }

    private fun StoreCollection.toAppstoreCollection(
        sourceId: Int,
        type: AppType,
        enabled: Boolean
    ): AppstoreCollection {
        return AppstoreCollection(
            sourceId = sourceId,
            title = name,
            slug = slug,
            type = type,
            enabled = enabled,
        )
    }

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
fun AppstoreSettingsScreen(nav: CoreNav, topBarParams: TopBarParams?) {
    val uriHandler = LocalUriHandler.current
    val viewModel = koinViewModel<AppstoreSettingsScreenViewModel> { parametersOf(uriHandler) }
    val sources by viewModel.sources.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val pebbleAccount: PebbleAccount = koinInject()
    val sourceDao: AppstoreSourceDao = koinInject()
    val scope = rememberCoroutineScope()
    val pebbleLoggedIn = pebbleAccount.loggedIn
    val bootConfig = koinInject<BootConfigProvider>()
    var showLockerImportDialog by remember { mutableStateOf<Int?>(null) }

    if (showLockerImportDialog != null) {
        val isRebble = remember {
            parseUrl(bootConfig.getUrl() ?: "")?.host?.endsWith("rebble.io") == true
        }
        val sourceId = showLockerImportDialog
        if (sourceId != null) {
            LockerImportDialog(
                onDismissRequest = { showLockerImportDialog = null },
                isRebble = isRebble,
                topBarParams = topBarParams,
                onEnabled = {
                    scope.launch {
                        sourceDao.setSourceEnabled(sourceId, true)
                    }
                },
            )
        }
    }

    AppstoreSettingsScreen(
        nav = nav,
        sources = sources,
        collections = collections,
        onSourceRemoved = viewModel::removeSource,
        onSourceAdded = viewModel::addSource,
        onSourceEnableChange = { sourceId, isEnabled ->
            scope.launch {
                if (sources.firstOrNull {
                        parseUrl(it.url)?.host?.endsWith("rebble.io") ?: false
                    }?.id == sourceId && isEnabled) {
                    if (pebbleLoggedIn.value == null) {
                        uriHandler.openUri(REBBLE_LOGIN_URI)
                    } else {
                        showLockerImportDialog = sourceId
                    }
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
    nav: CoreNav,
    sources: List<AppstoreSource>,
    collections: Map<AppType, List<Pair<AppstoreSource, List<AppstoreCollection>>>>?,
    onSourceRemoved: (Int) -> Unit,
    onSourceAdded: (title: String, url: String) -> Unit,
    onSourceEnableChange: (Int, Boolean) -> Unit,
    onCollectionEnabledChanged: (AppstoreCollection, Boolean) -> Unit,
) {
    var createSourceOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appstore Sources") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            nav.goBack()
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
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
                val collections = collections
                    ?.mapValues {
                        it.value.filter { it.first.id == source.id }.flatMap { it.second }
                    }
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
            nav = NoOpCoreNav,
            sources = listOf(
                sourceA,
                sourceB
            ),
            collections = mapOf(
                AppType.Watchapp to listOf(
                    sourceA to listOf(
                        AppstoreCollection(
                            sourceId = sourceA.id,
                            title = "Featured Apps",
                            slug = "featured-apps",
                            type = AppType.Watchapp,
                            enabled = true
                        )
                    )
                ),
                AppType.Watchface to listOf(
                    sourceA to listOf(
                        AppstoreCollection(
                            sourceId = sourceA.id,
                            title = "Featured Faces",
                            slug = "featured-faces",
                            type = AppType.Watchface,
                            enabled = false
                        )
                    ),
                    sourceB to listOf(
                        AppstoreCollection(
                            sourceId = sourceB.id,
                            title = "Top faces",
                            slug = "featured-apps",
                            type = AppType.Watchface,
                            enabled = true
                        )
                    )
                ),
            ),
            onSourceRemoved = {},
            onSourceAdded = { _, _ -> },
            onSourceEnableChange = { _, _ -> },
            onCollectionEnabledChanged = { _, _ -> },
        )
    }
}

@Preview
@Composable
fun PreviewCreateAppstoreSourceDialog() {
    PreviewWrapper {
        CreateAppstoreSourceDialog(
            onDismissRequest = {},
            onSourceCreated = { _, _ -> }
        )
    }
}