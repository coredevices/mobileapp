package coredevices.pebble.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.apps
import coredevices.database.AppstoreCollectionDao
import coredevices.database.AppstoreSource
import coredevices.database.AppstoreSourceDao
import coredevices.pebble.PebbleDeepLinkHandler
import coredevices.pebble.Platform
import coredevices.pebble.account.PebbleAccount
import coredevices.pebble.rememberLibPebble
import coredevices.pebble.services.AppStoreHome
import coredevices.pebble.services.AppStoreHomeResult
import coredevices.pebble.services.PebbleWebServices
import coredevices.pebble.services.StoreCategory
import coredevices.ui.PebbleElevatedButton
import coredevices.util.CoreConfigFlow
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.locker.SystemApps
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.util.getTempFilePath
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import rememberOpenDocumentLauncher
import theme.coreDarkGreen
import theme.coreOrange
import kotlin.uuid.Uuid

const val REBBLE_LOGIN_URI = "https://boot.rebble.io"

private val logger = Logger.withTag("LockerScreen")

class LockerViewModel(
    private val pebbleWebServices: PebbleWebServices,
    private val storeSourceDao: AppstoreSourceDao,
) : ViewModel() {
    val storeHomeAllFeeds = mutableStateMapOf<AppType, List<AppStoreHomeResult>>()
    val storeSearchResults = MutableStateFlow<List<CommonApp>>(emptyList())
    val searchState = SearchState()
    val type = mutableStateOf(AppType.Watchface)
    val showIncompatible = mutableStateOf(false)
    val showScaled = mutableStateOf(true)
    var storeIsRefreshing by mutableStateOf(false)
    var lockerIsRefreshing by mutableStateOf(false)

    fun refreshStore(platform: WatchType, useCache: Boolean): Deferred<Unit> {
        storeIsRefreshing = true
        val finishedAll: List<Deferred<Unit>> = AppType.entries.map {
            val finished = CompletableDeferred<Unit>()
            viewModelScope.launch {
                val result = withContext(Dispatchers.IO) {
                    pebbleWebServices.fetchAppStoreHome(it, platform, enabledOnly = true, useCache = useCache)
                }
                if (!result.isEmpty()) {
                    storeHomeAllFeeds[it] = result
                }
                finished.complete(Unit)
            }
            finished
        }
        return viewModelScope.async {
            finishedAll.awaitAll()
            storeIsRefreshing = false
        }
    }

    fun maybeRefreshStore(watchType: WatchType) {
        viewModelScope.launch {
            val currentEnabledStores = storeSourceDao.getAllEnabledSources().map { it.id }
            val loadedStores = storeHomeAllFeeds.values.firstOrNull()?.map { it.source.id }
            if (loadedStores == null || loadedStores != currentEnabledStores) {
                logger.v { "refreshing store" }
                refreshStore(watchType, useCache = true)
            }
        }
    }

    fun searchStore(search: String, watchType: WatchType, platform: Platform, appType: AppType) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                pebbleWebServices.searchAppStore(search, appType, watchType)
            }
            storeSearchResults.value = result.mapNotNull { (source, app) ->
                app.asCommonApp(watchType, platform, source)
            }.filter {
                it.type == appType
            }
//            logger.v { "result: $result" }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LockerScreen(
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val viewModel = koinViewModel<LockerViewModel>()
        val scope = rememberCoroutineScope()
        val libPebble = rememberLibPebble()
        val lastConnectedWatch = lastConnectedWatch()
        val runningApp by (lastConnectedWatch as? ConnectedPebbleDevice)?.runningApp?.collectAsState(
            null
        ) ?: mutableStateOf(null)
        val watchType = lastConnectedWatch?.watchType?.watchType ?: WatchType.DIORITE
        val appContext = koinInject<AppContext>()
        val pebbleAccount = koinInject<PebbleAccount>()
        val loggedIn by pebbleAccount.loggedIn.collectAsState()
        val launchInstallAppDialog = rememberOpenDocumentLauncher {
            it?.firstOrNull()?.let { file ->
                val tempAppPath = getTempFilePath(appContext, "temp.pbw")
                SystemFileSystem.sink(tempAppPath).use { sink ->
                    file.source.use { source ->
                        source.transferTo(sink)
                    }
                }
                scope.launch {
                    libPebble.sideloadApp(tempAppPath)
                }
            }
        }
        val openInstallAppDialog = remember(launchInstallAppDialog) {
            {
                launchInstallAppDialog(listOf("*/*"))
            }
        }
        val platform: Platform = koinInject()
        val title = stringResource(Res.string.apps)
        val coreConfigFlow: CoreConfigFlow = koinInject()
        val coreConfig by coreConfigFlow.flow.collectAsState()
        val mainListState = rememberLazyListState()
        val searchListState = rememberLazyListState()
        val collectionsDao: AppstoreCollectionDao = koinInject()
        val collections by collectionsDao.getAllCollectionsFlow().collectAsState(null)
        if (collections == null) {
            return
        }
        val storeHomeForType = viewModel.storeHomeAllFeeds[viewModel.type.value]
        val storeHome = remember(storeHomeForType, collections, viewModel.type.value) {
            val collectionRules = collections!!.groupBy { it.sourceId }
            storeHomeForType?.map { (source, home) ->
                val homeFiltered = home.let {
                    it.copy(collections = it.collections.filter { col ->
                        collectionRules[source.id]?.firstOrNull { it.slug == col.slug && it.type == viewModel.type.value }?.enabled ?: false
                    })
                }
                val categories = homeFiltered.categories.filter{
                    it.slug != "index"
                }.chunked(2)
                StoreHomeDisplay(source, homeFiltered, categories)
            } ?: emptyList()
        }

        LaunchedEffect(Unit) {
            topBarParams.searchAvailable(viewModel.searchState)
            topBarParams.actions {
                TopBarIconButtonWithToolTip(
                    onClick = openInstallAppDialog,
                    icon = Icons.Filled.UploadFile,
                    description = "Sideload App",
                )
            }
            topBarParams.title(title)
            if (coreConfig.useNativeAppStore) {
                viewModel.maybeRefreshStore(watchType)
            }
            launch {
                topBarParams.scrollToTop.collect {
                    val listState = if (viewModel.searchState.query.isNotEmpty()) {
                        searchListState
                    } else {
                        mainListState
                    }
                    if (listState.firstVisibleItemIndex > 0) {
                        listState.animateScrollToItem(0)
                    } else if (viewModel.searchState.show) {
                        viewModel.searchState.show = false
                        viewModel.searchState.typing = false
                        viewModel.searchState.query = ""
                    } else {
                        viewModel.type.value = AppType.Watchface
                    }
                }
            }
        }
        val initialLockerSync: PebbleDeepLinkHandler = koinInject()
        val initialLockerSyncInProgress by initialLockerSync.initialLockerSync.collectAsState()
        LaunchedEffect(initialLockerSyncInProgress) {
            if (initialLockerSyncInProgress) {
                topBarParams.showSnackbar("Loading Locker")
            }
        }

        val uriHandler = LocalUriHandler.current

        if (coreConfig.useNativeAppStore) {
            LaunchedEffect(viewModel.searchState.query, viewModel.type.value) {
                if (viewModel.searchState.query.isNotEmpty()) {
                    viewModel.searchStore(viewModel.searchState.query, watchType, platform, viewModel.type.value)
                }
            }
        }
        val lockerEntries = loadLockerEntries(
            type = viewModel.type.value,
            searchQuery = viewModel.searchState.query,
            watchType = watchType,
            showIncompatible = viewModel.showIncompatible.value,
            showScaled = viewModel.showScaled.value,
        )
        val activeWatchface = loadActiveWatchface(watchType)
        if (lockerEntries == null || activeWatchface == null) {
            // Don't render the screen at all until we've read the locker from db
            // (otherwise scrolling can get really confused while it's momentarily empty)
            return
        }

        Scaffold(
            floatingActionButton = {
                if (loggedIn != null && !coreConfig.useNativeAppStore) {
                    FloatingActionButton(
                        onClick = {
                            navBarNav.navigateTo(
                                PebbleNavBarRoutes.AppStoreRoute(
                                    appType = viewModel.type.value.code,
                                    deepLinkId = null,
                                )
                            )
                        }
                    ) {
                        Icon(Icons.Filled.Add, "Add")
                    }
                }
            },
        ) {
            Column {
                AppsFilterRow(
                    watchType = watchType,
                    selectedType = viewModel.type,
                    showIncompatible = viewModel.showIncompatible,
                    showScaled = viewModel.showScaled,
                )
                if (viewModel.searchState.query.isNotEmpty() && coreConfig.useNativeAppStore) {
                    val resultQuery = remember(lockerEntries, viewModel.showIncompatible.value, viewModel.showScaled.value) {
                        viewModel.storeSearchResults.map { searchResults ->
                            lockerEntries + searchResults.filter { searchResult ->
                                !lockerEntries.any { lockerEntry -> searchResult.uuid == lockerEntry.uuid }
                                        && (viewModel.showIncompatible.value || searchResult.isCompatible)
                                        && (viewModel.showScaled.value || searchResult.isNativelyCompatible)
                            }
                        }
                    }
                    val results by resultQuery.collectAsState(initial = emptyList())

                    Column {
                        SearchResultsList(
                            results = results,
                            navBarNav = navBarNav,
                            topBarParams = topBarParams,
                            lazyListState = searchListState,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    PullToRefreshBox(isRefreshing = viewModel.storeIsRefreshing || viewModel.lockerIsRefreshing, onRefresh = {
                        viewModel.lockerIsRefreshing = true
                        logger.v { "set isRefreshing to true" }
                        val lockerFinished = libPebble.requestLockerSync()
                        viewModel.refreshStore(watchType, useCache = false)
                        scope.launch {
                            lockerFinished.await()
                            viewModel.lockerIsRefreshing = false
                        }
                    }) {
                        if (coreConfig.useNativeAppStore) {
                            val myApps by remember(lockerEntries) {
                                derivedStateOf {
                                    lockerEntries.filter {
                                                it.showOnMainLockerScreen() &&
                                                (it.uuid != activeWatchface.uuid || it.uuid != lockerEntries.first().uuid)
                                    }
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                state = mainListState,
                            ) {
                                @Composable
                                fun Carousel(
                                    title: String,
                                    items: List<CommonApp>,
                                    highlightInLocker: Boolean,
                                    onClick: (() -> Unit)? = null
                                ) {
                                    AppCarousel(
                                        title = title,
                                        items = items,
                                        navBarNav = navBarNav,
                                        topBarParams = topBarParams,
                                        highlightInLocker = highlightInLocker,
                                        onClick = onClick,
                                    )
                                }
                                if (viewModel.type.value == AppType.Watchface) {
                                    item(
                                        contentType = "active_watchface",
                                        key = "active_watchface"
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                            Text(
                                                "Active",
                                                fontSize = 24.sp,
                                                modifier = Modifier.padding(
                                                    top = 5.dp,
                                                    bottom = 8.dp
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(7.dp))
                                            Row(
                                                modifier = Modifier
                                                    .height(IntrinsicSize.Max)
                                                    .clickable {
                                                        navBarNav.navigateTo(
                                                            PebbleNavBarRoutes.LockerAppRoute(
                                                                uuid = activeWatchface.uuid.toString(),
                                                                storedId = activeWatchface.storeId,
                                                                storeSource = activeWatchface.appstoreSource?.id,
                                                            )
                                                        )
                                                    }.padding(vertical = 8.dp),
                                            ) {
                                                AppImage(
                                                    activeWatchface,
                                                    modifier = Modifier.clip(RoundedCornerShape(10.dp)),
                                                    size = NATIVE_SCREENSHOT_HEIGHT,
                                                )
                                                Column(
//                                                verticalArrangement = Arrangement.Center,
                                                    modifier = Modifier.fillMaxHeight()
                                                        .padding(start = 8.dp),
                                                ) {
                                                    Text(
                                                        activeWatchface.title,
                                                        fontSize = 20.sp,
                                                        modifier = Modifier.padding(0.dp)
                                                    )
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        activeWatchface.CompatibilityWarning(
                                                            topBarParams
                                                        )
                                                        Text(
                                                            activeWatchface.developerName,
                                                            fontSize = 12.sp,
                                                            color = Color.Gray,
                                                            modifier = Modifier.padding(0.dp)
                                                                .weight(1f)
                                                        )
                                                    }
                                                    if (activeWatchface.hasSettings()) {
                                                        activeWatchface.SettingsButton(
                                                            navBarNav,
                                                            topBarParams,
                                                            lastConnectedWatch is ConnectedPebbleDevice
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                item(contentType = "app_carousel", key = "collection_my-apps") {
                                    val myAppsToDisplay = remember(myApps) {
                                        myApps.take(20)
                                    }
                                    Carousel(
                                        viewModel.type.value.myCollectionName(),
                                        myAppsToDisplay,
                                        highlightInLocker = false,
                                        onClick = {
                                            navBarNav.navigateTo(
                                                PebbleNavBarRoutes.MyCollectionRoute(
                                                    appType = viewModel.type.value.code,
                                                )
                                            )
                                        })
                                }

                                storeHome.forEach {
                                    val home = it.home
                                    val source = it.source
                                    item(
                                        contentType = "source_title",
                                        key = "source_${source.id}"
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                source.title,
                                                modifier = Modifier.padding(
                                                    horizontal = 16.dp,
                                                    vertical = 16.dp
                                                ).weight(1f),
                                                style = MaterialTheme.typography.headlineMedium,
                                            )
                                            IconButton(
                                                onClick = {
                                                    navBarNav.navigateTo(PebbleNavBarRoutes.AppstoreSettingsRoute)
                                                },
                                            ) {
                                                Icon(
                                                    Icons.Default.Tune,
                                                    contentDescription = "Configure Feeds"
                                                )
                                            }
                                        }
                                    }
                                    if (viewModel.type.value == AppType.Watchapp) {
                                        it.categories.forEachIndexed { i, categories ->
                                            item(
                                                contentType = "categories",
                                                key = "collection_${source.id}_categories_$i",
                                            ) {
                                                Column {
                                                    Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                                                        CategoryItem(
                                                            source = source,
                                                            category = categories.first(),
                                                            navBarNav = navBarNav,
                                                        )
                                                        categories.getOrNull(1)?.let {
                                                            CategoryItem(
                                                                source = source,
                                                                category = it,
                                                                navBarNav = navBarNav,
                                                            )
                                                        }
                                                    }
                                                    if (i == it.categories.lastIndex) {
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    items(
                                        home.collections,
                                        contentType = { "app_carousel_collection" },
                                        key = { "collection_${source.id}_${it.slug}" }) { collection ->
                                        val collectionApps =
                                            remember(
                                                home,
                                                collection,
                                                watchType,
                                                lockerEntries,
                                                viewModel.showScaled,
                                            ) {
                                                collection.applicationIds.mapNotNull { appId ->
                                                    home.applications.find { app ->
                                                        app.id == appId
                                                    }?.asCommonApp(
                                                        watchType,
                                                        platform,
                                                        source,
                                                        home.categories
                                                    )
                                                }.filter {
                                                    it.type == viewModel.type.value &&
                                                            (viewModel.showScaled.value || it.isNativelyCompatible)
                                                }
                                                    .distinctBy { it.uuid }
                                            }
                                        Carousel(
                                            collection.name,
                                            collectionApps,
                                            highlightInLocker = true,
                                            onClick = {
                                                navBarNav.navigateTo(
                                                    PebbleNavBarRoutes.AppStoreCollectionRoute(
                                                        sourceId = source.id,
                                                        path = "collection/${collection.slug}",
                                                        title = collection.name,
                                                        appType = viewModel.type.value.code,
                                                    )
                                                )
                                            })
                                    }
                                }
                            }
                        } else {
                            val legacyOnWatch by remember(lockerEntries) {
                                derivedStateOf {
                                    lockerEntries.filter {
                                        it.isSynced() && it.showOnMainLockerScreen()
                                    }
                                }
                            }
                            val legacyNotOnWatch by remember(lockerEntries) {
                                derivedStateOf {
                                    lockerEntries.filter {
                                        !it.isSynced() && it.showOnMainLockerScreen()
                                    }
                                }
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(4.dp),
                            ) {
                                if (loggedIn == null) {
                                    item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                                        PebbleElevatedButton(
                                            text = "Login to Rebble to see App Store and Locker",
                                            onClick = {
                                                uriHandler.openUri(REBBLE_LOGIN_URI)
                                            },
                                            primaryColor = true,
                                            modifier = Modifier.padding(5.dp)
                                                .align(Alignment.Center),
                                        )
                                    }
                                }
                                val watchName = lastConnectedWatch?.displayName() ?: ""
                                if (legacyOnWatch.isNotEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("On Watch $watchName") }
                                    items(
                                        items = legacyOnWatch,
                                        key = { it.uuid }
                                    ) { entry ->
                                        LegacyWatchfaceCard(
                                            entry,
                                            navBarNav,
                                            runningApp == entry.uuid,
                                            topBarParams,
                                        )
                                    }
                                }
                                if (legacyNotOnWatch.isNotEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Not On Watch $watchName") }
                                    items(
                                        items = legacyNotOnWatch,
                                        key = { it.uuid }
                                    ) { entry ->
                                        LegacyWatchfaceCard(
                                            entry,
                                            navBarNav,
                                            runningApp == entry.uuid,
                                            topBarParams,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class StoreHomeDisplay(
    val source: AppstoreSource,
    val home: AppStoreHome,
    val categories: List<List<StoreCategory>>,
)

@Composable
fun RowScope.CategoryItem(
    source: AppstoreSource,
    category: StoreCategory,
    navBarNav: NavBarNav,
) {
    val color = category.color.toColorKmp()
    Box(
        modifier = Modifier.padding(2.dp)
            .weight(1f)
            .clickable {
                navBarNav.navigateTo(
                    PebbleNavBarRoutes.AppStoreCollectionRoute(
                        sourceId = source.id,
                        path = "category/${category.slug}",
                        title = category.name,
                        appType = AppType.Watchapp.code,
                    )
                )
            }.background(color)
            .clip(RoundedCornerShape(6.dp)),
    ) {
        Text(
            category.name,
            modifier = Modifier.padding(9.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Color.Black,
        )
    }
}

fun String.toColorKmp(): Color {
    val hex = this.replace("#", "")
    val colorLong = hex.toLong(16)

    return if (hex.length == 6) {
        // Prepend FF for Alpha
        Color(colorLong or 0xFF000000)
    } else {
        Color(colorLong)
    }
}

@Composable
fun SearchResultsList(
    results: List<CommonApp>,
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val storeApps = results.filter { it.commonAppType is CommonAppType.Store }
    val lockerApps = results.filter { it.commonAppType is CommonAppType.Locker || it.commonAppType is CommonAppType.System }
    val scope = rememberCoroutineScope()
    LazyColumn(modifier, lazyListState) {
        if (lockerApps.isNotEmpty()) {
            item {
                Text(
                    "From my apps",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(
                items = lockerApps,
                key = { it.uuid }
            ) { entry ->
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
                    highlightInLocker = false,
                )
            }
        }
        if (storeApps.isNotEmpty()) {
            item {
                Text(
                    "From the store",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(
                items = storeApps,
                key = { it.uuid }
            ) { entry ->
                NativeWatchfaceListItem(
                    entry,
                    onClick = {
                        scope.launch {
//                            val sources = withContext(Dispatchers.IO) { pebbleWebServices.searchUuidInSources(entry.uuid) }
//                            val (bestId, bestSource) = withContext(Dispatchers.IO) {
//                                sources.maxByOrNull { (id, source) ->
//                                    pebbleWebServices.fetchAppStoreApp(id, null, source.url)
//                                        ?.data
//                                        ?.firstOrNull()
//                                        ?.latestRelease?.version ?: "0"
//                                } ?: (null to null)
//                            }
                            navBarNav.navigateTo(
                                PebbleNavBarRoutes.LockerAppRoute(
                                    uuid = entry.uuid.toString(),
                                    storedId = entry.storeId,
                                    storeSource = entry.appstoreSource?.id,
//                                    storeSources = Json.encodeToString(sources)
                                )
                            )
                        }
                    },
                    topBarParams = topBarParams,
                    highlightInLocker = false,
                )
            }
        }
    }
}

@Composable
fun AppCarousel(
    title: String,
    items: List<CommonApp>,
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
    highlightInLocker: Boolean,
    onClick: (() -> Unit)? = null,
) {
    if (items.isEmpty()) {
        logger.d { "Not showing collection $title as it has no items" }
        return
    }
    Column {
        Row(modifier = Modifier.padding(horizontal = 16.dp)
            .let {
            if (onClick != null) {
                it.clickable { onClick() }
            } else {
                it
            }
        }, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 24.sp, modifier = Modifier.padding(vertical = 8.dp))
            Spacer(modifier = Modifier.width(7.dp))
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Default.ArrowForward, contentDescription = "See all", modifier = Modifier)
            }
        }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(horizontal = 5.dp),
        ) {
            items(items, key = { it.uuid } ) { entry ->
                NativeWatchfaceCard(
                    entry,
                    navBarNav,
                    width = 100.dp,
                    topBarParams = topBarParams,
                    highlightInLocker = highlightInLocker,
                )
            }
        }
    }
}


@Preview
@Composable
fun LockerScreenPreviewWrapper() {
    PreviewWrapper {
        LockerScreen(
            navBarNav = NoOpNavBarNav,
            topBarParams = WrapperTopBarParams,
        )
    }
}

val testApps = listOf(
    CommonApp(
        title = "Sample Watchface",
        developerName = "Dev Name",
        uuid = Uuid.parse("123e4567-e89b-12d3-a456-426614174000"),
        androidCompanion = null,
        commonAppType = CommonAppType.Locker(
            sideloaded = false,
            configurable = true,
            sync = true,
            order = 0,
        ),
        type = AppType.Watchface,
        category = "Fun",
        version = "1.0",
        listImageUrl = null,
        screenshotImageUrl = null,
        isCompatible = true,
        hearts = 42,
        description = "A sample watchface for preview purposes.",
        isNativelyCompatible = true,
        developerId = "123",
        categorySlug = "fun",
        storeId = "6962e51d29173c0009b18f8e",
        sourceLink = "https://example.com",
        appstoreSource = null,
        capabilities = emptyList(),
    ),
    CommonApp(
        title = "Another Watchface",
        developerName = "Another Dev",
        uuid = Uuid.parse("223e4567-e89b-12d3-a456-426614174000"),
        androidCompanion = null,
        commonAppType = CommonAppType.Locker(
            sideloaded = true,
            configurable = false,
            sync = false,
            order = 1,
        ),
        type = AppType.Watchface,
        category = "Utility",
        version = "2.1",
        listImageUrl = null,
        screenshotImageUrl = null,
        isCompatible = true,
        hearts = 7,
        description = "Another sample watchface for preview purposes.",
        isNativelyCompatible = true,
        developerId = "123",
        categorySlug = "fun",
        storeId = "6962e51d29173c0009b18f8f",
        sourceLink = "https://example.com",
        appstoreSource = null,
        capabilities = emptyList(),
    ),
    CommonApp(
        title = "Third Watchface",
        developerName = "Third Dev",
        uuid = Uuid.parse("323e4567-e89b-12d3-a456-426614174000"),
        androidCompanion = null,
        commonAppType = CommonAppType.Locker(
            sideloaded = false,
            configurable = true,
            sync = true,
            order = 2,
        ),
        type = AppType.Watchface,
        category = "Sport",
        version = "3.3",
        listImageUrl = null,
        screenshotImageUrl = null,
        isCompatible = false,
        hearts = 15,
        description = "Yet another sample watchface for preview purposes.",
        isNativelyCompatible = true,
        developerId = "123",
        categorySlug = "fun",
        storeId = "6962e51d29173c0009b18f8d",
        sourceLink = "https://example.com",
        appstoreSource = null,
        capabilities = emptyList(),
    )
)

private val NATIVE_SCREENSHOT_HEIGHT = 100.dp

@Composable
fun NativeWatchfaceCard(
    entry: CommonApp,
    navBarNav: NavBarNav,
    width: Dp,
    topBarParams: TopBarParams,
    highlightInLocker: Boolean,
) {
    Card(
        modifier = Modifier.padding(3.dp)
            .width(width)
            .clickable {
                navBarNav.navigateTo(
                    PebbleNavBarRoutes.LockerAppRoute(
                        uuid = entry.uuid.toString(),
                        storedId = entry.storeId,
                        storeSource = entry.appstoreSource?.id,
                    )
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                val imageModifier =
                    Modifier.padding(6.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(9.dp))
                AppImage(
                    entry,
                    modifier = imageModifier,
                    size = NATIVE_SCREENSHOT_HEIGHT,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 5.dp, end = 5.dp),
            ) {
                if (entry.type == AppType.Watchapp) {
                    AsyncImage(
                        model = entry.listImageUrl,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).padding(end = 3.dp)
                    )
                }
                Text(
                    entry.title,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(modifier = Modifier.align(Alignment.CenterHorizontally).padding(start = 5.dp, end = 5.dp)) {
                if (highlightInLocker) {
                    val inMyCollection = entry.inMyCollection()
                    if (inMyCollection) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAddCheck,
                            contentDescription = "In My Collection",
                            modifier = Modifier.size(19.dp)
                                .padding(top = 1.dp, bottom = 5.dp),
                            tint = coreDarkGreen,
                        )
                    }
                }
                Text(
                    entry.developerName,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 7.dp)
                        .weight(1f),
                )
                entry.CompatibilityWarning(topBarParams)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyWatchfaceCard(
    entry: CommonApp,
    navBarNav: NavBarNav,
    running: Boolean,
    topBarParams: TopBarParams,
) {
    ElevatedCard(
        elevation = CardDefaults.cardElevation(
            defaultElevation = 5.dp
        ),
        modifier = Modifier.padding(7.dp)
            .clickable {
                navBarNav.navigateTo(
                    PebbleNavBarRoutes.LockerAppRoute(
                        uuid = entry.uuid.toString(),
                        storedId = null,
                        storeSource = null
                    )
                )
            }.border(
                width = 2.dp,
                color = if (running) coreOrange else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                val modifier =
                    Modifier.padding(top = 12.dp, bottom = 8.dp).align(Alignment.Center)
                if (entry.isCompatible) {
                    AppImage(
                        entry,
                        modifier = modifier,
                        size = 120.dp,
                    )
                    if (!entry.isNativelyCompatible) {
                        IconButton(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(7.dp)
                                .size(30.dp),
                            onClick = {
                                topBarParams.showSnackbar("Not natively compatible, but can be scaled")
                            },
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = "Not natively compatible, but can be scaled",
                                modifier = Modifier.fillMaxSize(),
                                tint = coreOrange,
                            )
                        }
                    }
                } else {
                    Box(modifier = modifier, contentAlignment = Alignment.Center) {
                        Text("Not Compatible", fontSize = 15.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            Text(
                entry.title,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .padding(vertical = 5.dp, horizontal = 2.dp),
                fontWeight = FontWeight.Bold,
            )
            Text(
                entry.developerName,
                color = Color.Gray,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .padding(top = 2.dp, bottom = 5.dp),
            )
        }
    }
}

@Composable
fun NativeWatchfaceListItem(
    entry: CommonApp,
    onClick: () -> Unit,
    topBarParams: TopBarParams,
    highlightInLocker: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(vertical = 8.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppImage(
            entry,
            modifier = Modifier.width(62.dp).clip(RoundedCornerShape(8.dp)),
            size = 74.dp,
        )
        Column(
            modifier = Modifier.padding(start = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.type == AppType.Watchapp) {
                    AsyncImage(model = entry.listImageUrl, contentDescription = null, modifier = Modifier.size(21.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    entry.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                )
            }
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (highlightInLocker) {
                        val inMyCollection = entry.inMyCollection()
                        if (inMyCollection) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAddCheck,
                                contentDescription = "In My Collection",
                                modifier = Modifier.size(16.dp)
                                    .padding(top = 1.dp, end = 6.dp, bottom = 5.dp),
                                tint = coreDarkGreen,
                            )
                        }
                    }
                    entry.CompatibilityWarning(topBarParams)
                }
                if (entry.description != null) {
                    Text(
                        text = entry.description,
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 3,
                        lineHeight = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun AppImage(entry: CommonApp, modifier: Modifier, size: Dp) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val placeholder = remember {
        ColorPainter(placeholderColor)
    }
    val context = LocalPlatformContext.current
    val model = remember(entry) {
        when (entry.commonAppType) {
            is CommonAppType.Locker, is CommonAppType.Store -> {
                val url = when (entry.type) {
                    AppType.Watchapp -> entry.screenshotImageUrl
                    AppType.Watchface -> entry.screenshotImageUrl
                }
                ImageRequest.Builder(context)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .data(url)
                    .build()
            }

            is CommonAppType.System -> {
                val resUri = when (entry.commonAppType.app) {
                    SystemApps.Settings -> "drawable/settings.png"
                    SystemApps.Music -> "drawable/music.png"
                    SystemApps.Notifications -> "drawable/notifications.png"
                    SystemApps.Alarms -> "drawable/alarms.png"
                    SystemApps.Workout -> "drawable/workout.png"
                    SystemApps.Watchfaces -> "drawable/watchfaces.png"
                    SystemApps.Health -> "drawable/health.png"
                    SystemApps.Weather -> "drawable/weather.png"
                    SystemApps.Tictoc -> "drawable/tictoc.png"
                    SystemApps.Kickstart -> "drawable/kickstart.png"
                }
                Res.getUri(resUri)
            }
        }
    }

    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = modifier.height(size)
            .widthIn(max = size),
//            .background(Color.Green),
        placeholder = placeholder,
        onError = { e ->
            logger.w(e.result.throwable) { "Error loading app image for ${entry.uuid}" }
        },
//                contentScale = ContentScale.Fit,
    )
}