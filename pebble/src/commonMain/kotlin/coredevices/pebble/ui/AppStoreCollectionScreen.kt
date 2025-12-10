package coredevices.pebble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import coredevices.database.AppstoreSourceDao
import coredevices.pebble.Platform
import coredevices.pebble.services.AppstoreService
import io.rebble.libpebblecommon.connection.KnownPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.metadata.WatchType
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf

class AppStoreCollectionScreenViewModel(
    val libPebble: LibPebble,
    val platform: Platform,
    val appstoreSourceDao: AppstoreSourceDao,
    appstoreSourceId: Int,
    val path: String,
    val appType: AppType?,
): ViewModel(), KoinComponent {
    val logger = Logger.withTag("AppStoreCollectionScreenVM")
    val loading = MutableStateFlow(true)
    val loadedApps = mutableStateListOf<CommonApp>()
    val watchType = viewModelScope.async {
        libPebble.watches.map {
            it.sortedWith(PebbleDeviceComparator).filterIsInstance<KnownPebbleDevice>()
                .firstOrNull()
        }.map { it?.watchType?.watchType}.firstOrNull() ?: WatchType.DIORITE
    }
    val reachedMax = mutableStateOf(false)
    val appstoreService = viewModelScope.async {
        val source = appstoreSourceDao.getSourceById(appstoreSourceId)!!
        get<AppstoreService> { parametersOf(source) }
    }

    val categories = viewModelScope.async {
        appType?.let {
            appstoreService.await().fetchAppStoreHome(appType, watchType.await())?.categories
        } ?: run {
            buildList {
                addAll(
                    appstoreService.await().fetchAppStoreHome(AppType.Watchface, watchType.await())?.categories ?: emptyList()
                )
                addAll(
                    appstoreService.await().fetchAppStoreHome(AppType.Watchapp, watchType.await())?.categories ?: emptyList()
                )
            }
        }
    }

    init {
        loadMoreApps()
    }

    fun loadMoreApps() {
        viewModelScope.launch {
            if (reachedMax.value) {
                return@launch
            }
            loading.value = true
            val skip = loadedApps.size
            appstoreService.await().fetchAppStoreCollection(
                path,
                appType,
                hardwarePlatform = watchType.await(),
                offset = skip
            )?.let { newApps ->
                loading.value = false
                logger.d { "Fetched ${newApps.data.size} more apps for collection $path (offset = $skip)" }
                loadedApps.addAll(newApps.data.mapNotNull {
                    it.asCommonApp(
                        watchType.await(),
                        platform,
                        appstoreService.await().source,
                        categories.await()
                    )
                })
                if (newApps.data.isEmpty()) {
                    logger.d { "Reached max apps for collection $path" }
                    reachedMax.value = true
                }
            } ?: run {
                logger.e { "Failed to fetch apps for collection $path" }
                loading.value = false
            }
        }
    }
}

@Composable
fun AppStoreCollectionScreen(
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
    sourceId: Int,
    path: String, // e.g. "collection/most-loved"
    title: String,
    appType: AppType?,
) {
    val viewModel = koinViewModel<AppStoreCollectionScreenViewModel> {
        parametersOf(
            sourceId,
            path,
            appType
        )
    }
    val loading by viewModel.loading.collectAsState()
    val apps = viewModel.loadedApps
    val reachedMax by viewModel.reachedMax
    AppStoreCollectionScreen(
        navBarNav = navBarNav,
        topBarParams = topBarParams,
        collectionTitle = title,
        loading = loading,
        apps = apps,
        reachedMax = reachedMax,
        onLoadMore = {
            viewModel.loadMoreApps()
        }
    )
}

@Composable
fun AppStoreCollectionScreen(
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
    collectionTitle: String,
    loading: Boolean,
    apps: List<CommonApp>,
    reachedMax: Boolean,
    onLoadMore: () -> Unit
) {
    LaunchedEffect(collectionTitle) {
        topBarParams.title(collectionTitle)
        topBarParams.canGoBack(true)
        topBarParams.actions {}
        topBarParams.searchAvailable(false)
    }
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize()) {
        if (loading && apps.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 132.dp)
            ) {
                appStoreGridItems(
                    apps = apps,
                    navBarNav = navBarNav,
                    topBarParams = topBarParams,
                )
                if (!reachedMax) {
                    item(span = { GridItemSpan(maxLineSpan) }, contentType = "load_more") {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.background)
                                .align(Alignment.Center)
                                .padding(vertical = 16.dp)
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .align(Alignment.Center),
                                )
                            } else {
                                Button(
                                    onClick = {
                                        onLoadMore()
                                    },
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                ) {
                                    Text("Load More")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun LazyGridScope.appStoreGridItems(
    apps: List<CommonApp>,
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
) {
    items(
        items = apps,
        key = { it.uuid }
    ) { entry ->
        NativeWatchfaceCard(
            entry,
            navBarNav,
            false,
            topBarParams,
        )
    }
}