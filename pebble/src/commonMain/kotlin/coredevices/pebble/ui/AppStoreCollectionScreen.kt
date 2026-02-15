package coredevices.pebble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.filter
import co.touchlab.kermit.Logger
import coredevices.database.AppstoreSourceDao
import coredevices.pebble.Platform
import coredevices.pebble.services.AppstoreService
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.metadata.WatchType
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
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
    val showIncompatible = mutableStateOf(false)
    val showScaled = mutableStateOf(true)
    val logger = Logger.withTag("AppStoreCollectionScreenVM")
    var loadedApps by mutableStateOf<Flow<PagingData<CommonApp>>?>(null)
    private var loadedAppsWatchType: WatchType? = null
    val appstoreService = viewModelScope.async {
        val source = appstoreSourceDao.getSourceById(appstoreSourceId)!!
        get<AppstoreService> { parametersOf(source) }
    }

    private fun load(watchType: WatchType) {
        viewModelScope.launch {
            val service = appstoreService.await()
            val appTypeForFetch = when {
                path.contains("category") -> null
                else -> appType
            }
            loadedApps = Pager(
                config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                pagingSourceFactory = {
                    service.fetchAppStoreCollection(
                        path,
                        appTypeForFetch,
                        watchType,
                    )
                },
            ).flow.cachedIn(viewModelScope)
        }
    }

    fun maybeLoad(watchType: WatchType) {
        if (loadedApps == null || loadedAppsWatchType != watchType) {
            loadedAppsWatchType = watchType
            load(watchType)
        }
    }
}

@Composable
fun AppStoreCollectionScreen(
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
    sourceId: Int,
    path: String,
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
    val lastConnectedWatch = lastConnectedWatch()
    val watchType = lastConnectedWatch?.watchType?.watchType ?: WatchType.DIORITE
    LaunchedEffect(watchType) {
        viewModel.maybeLoad(watchType)
    }
    val apps = remember(viewModel.loadedApps, viewModel.showScaled.value, viewModel.showIncompatible.value) {
        viewModel.loadedApps?.map {
            it.filter { app ->
                if (!viewModel.showScaled.value && !app.isNativelyCompatible) {
                    false
                } else if (!viewModel.showIncompatible.value && !app.isCompatible) {
                    false
                } else {
                    true
                }
            }
        }
    }?.collectAsLazyPagingItems()
    LaunchedEffect(title) {
        topBarParams.title(title)
        topBarParams.actions {}
        topBarParams.searchAvailable(null)
    }
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize()) {
        Column {
            AppsFilterRow(
                watchType = watchType,
                selectedType = null,
                showIncompatible = viewModel.showIncompatible,
                showScaled = viewModel.showScaled,
            )
            if (apps == null || apps.itemCount == 0) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                when (appType) {
                    AppType.Watchface, null -> {
                        LazyVerticalGrid(
                            columns = GridCells.FixedSize(120.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            items(
                                count = apps.itemCount,
                                key = apps.itemKey { it.storeId ?: it.uuid }
                            ) { index ->
                                val entry = apps[index]!!
                                NativeWatchfaceCard(
                                    entry,
                                    navBarNav,
                                    width = 120.dp,
                                    topBarParams = topBarParams,
                                    highlightInLocker = true,
                                )
                            }
                        }
                    }
                    AppType.Watchapp -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(4.dp),
                        ) {
                            items(
                                count = apps.itemCount,
                                key = apps.itemKey { it.storeId ?: it.uuid }
                            ) { index ->
                                val entry = apps[index]!!
                                NativeWatchfaceListItem(
                                    entry,
                                    onClick = {
                                        navBarNav.navigateTo(
                                            PebbleNavBarRoutes.LockerAppRoute(
                                                uuid = entry.uuid.toString(),
                                                storedId = entry.storeId,
                                                storeSource = entry.appstoreSource?.id,
                                            )
                                        )
                                    },
                                    topBarParams = topBarParams,
                                    highlightInLocker = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
