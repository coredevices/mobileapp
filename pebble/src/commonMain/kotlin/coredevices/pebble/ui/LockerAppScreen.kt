package coredevices.pebble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
import coredevices.database.AppstoreSource
import coredevices.database.AppstoreSourceDao
import coredevices.pebble.Platform
import coredevices.pebble.rememberLibPebble
import coredevices.pebble.services.AppstoreService
import coredevices.ui.PebbleElevatedButton
import io.ktor.http.URLProtocol
import io.ktor.http.parseUrl
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.KnownPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.locker.AppCapability
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.locker.SystemApps
import io.rebble.libpebblecommon.metadata.WatchType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private val logger = Logger.withTag("LockerAppScreen")

class LockerAppViewModel(
//    private val pebbleWebServices: RealPebbleWebServices,
    private val platform: Platform,
) : ViewModel(), KoinComponent {
//    var storeEntries by mutableStateOf<List<AppVariant>?>(null)
    var addedToLocker by mutableStateOf(false)
    var selectedStoreEntry by mutableStateOf<CommonApp?>(null)

    fun loadAppFromStore(id: String, watchType: WatchType, source: AppstoreSource) {
        val service = get<AppstoreService> { parametersOf(source) }
        viewModelScope.launch {
            val result = service.fetchAppStoreApp(id, watchType)?.data?.firstOrNull()
//            if (result != null) {
//                storeEntries = getAppVariants(Uuid.parse(result.uuid), watchType, sources)
//            }
            selectedStoreEntry = result?.asCommonApp(
                watchType,
                platform,
                source,
                service.cachedCategoriesOrDefaults(AppType.fromString(result.type))
            )
        }
    }

//    private suspend fun getAppVariants(
//        uuid: Uuid,
//        watchType: WatchType,
//        storeSources: List<Pair<String, AppstoreSource>>? = null,
//    ): List<AppVariant> {
//        val sources = storeSources ?: pebbleWebServices.searchUuidInSources(uuid)
//        return sources.map { (id, source) ->
//            val service = get<AppstoreService> { parametersOf(source) }
//            //TODO: Search by uuid instead of id to get all variants, id is source-specific except for OG apps
//            viewModelScope.async(Dispatchers.IO) {
//                val result = service.fetchAppStoreApp(id, watchType)
//                val categories = result?.data?.firstOrNull()?.let { service.fetchCategories(AppType.fromString(it.type)!!) }
//                result?.data?.map { appEntry ->
//                    appEntry.asCommonApp(watchType, platform, source, categories)?.let { app ->
//                        AppVariant(
//                            source = source,
//                            app = app
//                        )
//                    }
//                } ?: emptyList()
//            }
//        }.awaitAll().flatten().filterNotNull().sortedBy { it.app.version ?: "0" }.reversed()
//    }
}

//data class AppVariant(
//    val source: AppstoreSource,
//    val app: CommonApp,
//)

@Composable
private fun appstoreSourceFromId(id: Int?): AppstoreSource? {
    val storeSourceDao: AppstoreSourceDao = koinInject()
    val storeSource by produceState<AppstoreSource?>(null, id) {
        value = id?.let { storeSourceDao.getSourceById(it) }
    }
    return storeSource
}

@Composable
fun LockerAppScreen(topBarParams: TopBarParams, uuid: Uuid?, navBarNav: NavBarNav, storeId: String?, storeSourceId: Int?) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val scope = rememberCoroutineScope()
        val libPebble = rememberLibPebble()
        val lastConnectedWatch = lastConnectedWatch()
        val runningApp by (lastConnectedWatch as? ConnectedPebbleDevice)?.runningApp?.collectAsState(
            null
        ) ?: mutableStateOf(null)
        val appIsRunning = runningApp == uuid
        val connected = lastConnectedWatch is ConnectedPebbleDevice
        val watchType = lastConnectedWatch?.watchType?.watchType ?: WatchType.DIORITE
        val viewModel = koinViewModel<LockerAppViewModel>()
        var showRemoveConfirmDialog by remember { mutableStateOf(false) }
        var loadingToWatch by remember { mutableStateOf(false) }

        val lockerEntry = loadLockerEntry(uuid, watchType)
        val entry = remember(lockerEntry, viewModel.selectedStoreEntry) {
            lockerEntry ?: viewModel.selectedStoreEntry
        }
        val storeSource = appstoreSourceFromId(storeSourceId)
        val platform: Platform = koinInject()
        val urlLauncher = LocalUriHandler.current
        val nativeLockerAddUtil: NativeLockerAddUtil = koinInject()

        LaunchedEffect(storeId, storeSource, watchType) {
            if (storeId != null && storeSource != null) {
                viewModel.loadAppFromStore(storeId, watchType, storeSource)
            }
        }

        LaunchedEffect(entry) {
            topBarParams.searchAvailable(null)
            topBarParams.actions {
                if (showRemoveConfirmDialog && entry != null) {
                    AlertDialog(
                        onDismissRequest = { showRemoveConfirmDialog = false },
                        title = { Text("Remove ${entry.title}?") },
                        text = { Text("Are you sure you want to remove this app from your Pebble?") },
                        confirmButton = {
                            TextButton(onClick = {
                                // Don't use local scope: that will die because we moved back
                                GlobalScope.launch {
                                    showRemoveConfirmDialog = false
                                    logger.d { "removing app ${entry.uuid}" }
                                    topBarParams.showSnackbar("Removing ${entry.title}")
                                    withContext(Dispatchers.Main) {
                                        navBarNav.goBack()
                                    }
                                    val removed = libPebble.removeApp(entry.uuid)
                                    nativeLockerAddUtil.removeFromLocker(storeSource, entry.uuid)
                                    logger.d { "removed = $removed" }
                                    topBarParams.showSnackbar("Removed ${entry.title}")
                                }
                            }) { Text("Remove") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showRemoveConfirmDialog = false
                            }) { Text("Cancel") }
                        }
                    )
                }
                val showRemove = entry?.commonAppType is CommonAppType.Locker
                if (showRemove) {
                    TopBarIconButtonWithToolTip(
                        onClick = { showRemoveConfirmDialog = true },
                        icon = Icons.Filled.Delete,
                        description = "Remove",
                        enabled = !showRemoveConfirmDialog,
                    )
                }
            }
            topBarParams.title(entry?.type?.name ?: "")
        }
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 5.dp)
        ) {
            entry?.let { entry ->
                if (entry.commonAppType is CommonAppType.Store && entry.commonAppType.headerImageUrl != null) {
                    AsyncImage(
                        model = entry.commonAppType.headerImageUrl,
                        contentDescription = "banner",
                        modifier = Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.FillWidth,
                    )
                }
                Row {
                    AppImage(
                        entry = entry,
                        modifier = Modifier.padding(10.dp).clip(RoundedCornerShape(12.dp)),
                        size = 140.dp,
                    )
                    Column(modifier = Modifier.padding(horizontal = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (entry.type == AppType.Watchapp) {
                                AsyncImage(
                                    model = entry.listImageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                            }
                            Text(
                                text = entry.title,
                                fontSize = 22.sp,
                                modifier = Modifier.padding(vertical = 5.dp),
                            )
                        }
                        if (entry.hearts != null) {
                            Row(modifier = Modifier.padding(vertical = 5.dp)) {
                                Icon(
                                    Icons.Outlined.Favorite,
                                    contentDescription = "Hearts",
                                )
                                Text(
                                    text = "${entry.hearts}",
                                    modifier = Modifier.padding(start = 5.dp),
                                )
                            }
                        }
                        if (entry.type == AppType.Watchapp) {
                            entry.category?.let {
                                Spacer(Modifier.height(5.dp))
                                FilterChip(
                                    true,
                                    onClick = {
                                        if (entry.categorySlug != null && storeSource != null) {
                                            navBarNav.navigateTo(
                                                PebbleNavBarRoutes.AppStoreCollectionRoute(
                                                    sourceId = storeSource.id,
                                                    path = "category/${entry.categorySlug}",
                                                    title = entry.category
                                                )
                                            )
                                        }
                                    },
                                    label = { Text(entry.category) }
                                )
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        val watchName = lastConnectedWatch?.displayName() ?: ""
                        val onWatchText = if (entry.isCompatible && entry.isSynced()) {
                            if (appIsRunning) {
                                "Running On Watch $watchName"
                            } else {
                                "On Watch $watchName"
                            }
                        } else if (!entry.isCompatible) {
                            "Not Compatible with $watchName"
                        } else if (entry.commonAppType !is CommonAppType.Store) {
                            "Not On Watch $watchName"
                        } else {
                            null
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            entry.CompatibilityWarning(topBarParams)
                            if (onWatchText != null) {
                                Text(
                                    onWatchText,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 5.dp),
                                )
                            }
                        }
                    }
                }

                val connectedIdentifier = lastConnectedWatch?.identifier
                val showStartApp =
                    entry.isCompatible && entry.commonAppType.canStartApp() && !appIsRunning
                            && !viewModel.addedToLocker
                if (showStartApp) {
                    val loadToWatchText = if (entry.isSynced()) {
                        ""
                    } else {
                        "Load To Watch & "
                    }
                    val text = if (entry.type == AppType.Watchapp) {
                        "${loadToWatchText}Start App"
                    } else {
                        "${loadToWatchText}Start Watchface"
                    }
                    PebbleElevatedButton(
                        text = text,
                        onClick = {
                            // Global scope because it could take a second to download/sync/load app
                            GlobalScope.launch {
                                if (connectedIdentifier != null) {
                                    loadingToWatch = true
                                    libPebble.launchApp(
                                        entry,
                                        topBarParams,
                                        connectedIdentifier
                                    )
                                    loadingToWatch = false
                                }
                            }
                        },
                        enabled = connected && !loadingToWatch,
                        icon = Icons.Default.PlayCircle,
                        contentDescription = text,
                        primaryColor = true,
                        modifier = Modifier.padding(5.dp),
                    )
                }
                // Only show for store, right now (until we figure out populating data or locker)
                if (entry.capabilities.isNotEmpty() && entry.commonAppType is CommonAppType.Store) {
                    FlowRow(modifier = Modifier.padding(5.dp)) {
                        entry.capabilities.forEach { permission ->
                            PermissionItem(permission, entry, topBarParams)
                        }
                    }
                }
                if (entry.commonAppType is CommonAppType.Store && !viewModel.addedToLocker) {
//                    val hasUuidConflict = remember(viewModel.storeEntries) { // One store has multiple entries with same uuid
//                        viewModel.storeEntries?.let {
//                            it.distinctBy { it.source.url }.size < it.size
//                        } ?: false
//                    }
//                    var variantsExpanded by remember { mutableStateOf(false) }
                    Row(Modifier.padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        PebbleElevatedButton(
                            text = "Add To Watch",
                            onClick = {
                                // Global scope because it could take a second to download/sync/load app
                                GlobalScope.launch {
                                    val watch = lastConnectedWatch as? ConnectedPebbleDevice
                                    viewModel.addedToLocker = true
                                    val addResult = nativeLockerAddUtil.addAppToLocker(entry.commonAppType, entry.commonAppType.storeSource)
                                    if (!addResult) {
                                        topBarParams.showSnackbar("Failed to add app")
                                        return@launch
                                    }
                                    if (watch != null) {
                                        libPebble.launchApp(
                                            entry = entry,
                                            topBarParams = topBarParams,
                                            connectedIdentifier = watch.identifier,
                                        )
                                    }
                                }
                            },
                            icon = Icons.Default.Add,
                            contentDescription = "Add To Watch",
                            primaryColor = true,
                            modifier = Modifier.padding(end = 8.dp),
                        )
//                        if (viewModel.storeEntries != null && (viewModel.storeEntries?.size ?: 1) > 1) {
//                            ExposedDropdownMenuBox(
//                                expanded = variantsExpanded,
//                                onExpandedChange = { variantsExpanded = it },
//                                modifier = Modifier.weight(1f)
//                            ) {
//                                val version = entry.version?.let { "v$it" } ?: "Unknown version"
//                                val textContent = if (hasUuidConflict) {
//                                    "${entry.commonAppType.storeSource.title}: ${entry.title} ($version)"
//                                } else {
//                                    "${entry.commonAppType.storeSource.title} ($version)"
//                                }
//                                TextField(
//                                    value = textContent,
//                                    onValueChange = { },
//                                    readOnly = true,
//                                    singleLine = true,
//                                    trailingIcon = {
//                                        ExposedDropdownMenuDefaults.TrailingIcon(
//                                            expanded = variantsExpanded
//                                        )
//                                    },
//                                    modifier = Modifier
//                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
//                                )
//                                ExposedDropdownMenu(
//                                    expanded = variantsExpanded,
//                                    onDismissRequest = { variantsExpanded = false }
//                                ) {
//                                    logger.d { "Showing ${viewModel.storeEntries!!.size} variants in dropdown" }
//                                    viewModel.storeEntries!!.forEach { variant ->
//                                        DropdownMenuItem(
//                                            text = {
//                                                if (hasUuidConflict) {
//                                                    Text("${variant.source.title}: ${variant.app.title} (${variant.app.version?.let { "v$it" } ?: "Unknown version"})")
//                                                } else {
//                                                    Text("${variant.source.title} (${variant.app.version?.let { "v$it" } ?: "Unknown version"})")
//                                                }
//                                            },
//                                            onClick = {
//                                                viewModel.selectedStoreEntry = variant.app
//                                                variantsExpanded = false
//                                            }
//                                        )
//                                    }
//                                }
//                            }
//                        }
                    }
                }

                Row {
                    entry.SettingsButton(
                        navBarNav = navBarNav,
                        topBarParams = topBarParams,
                        connected = connected,
                    )
                    if (entry.commonAppType is CommonAppTypeLocal && entry.commonAppType.order > 0) {
                        PebbleElevatedButton(
                            text = "Move To Top",
                            onClick = {
                                scope.launch {
                                    val index = when (entry.type) {
                                        AppType.Watchface -> -1
                                        AppType.Watchapp -> SystemApps.entries.size
                                    }
                                    libPebble.setAppOrder(entry.uuid, index)
                                }
                            },
                            icon = Icons.Default.VerticalAlignTop,
                            contentDescription = "Move To Top",
                            primaryColor = false,
                            modifier = Modifier.padding(5.dp),
                        )
                    }
                }
                val screenshotsToDisplay = remember(entry) {
                    when (entry.commonAppType) {
                        is CommonAppType.Store -> {
//                            when (entry.type) {
//                                AppType.Watchface -> entry.commonAppType.allScreenshotUrls.drop(1)
//                                AppType.Watchapp -> entry.commonAppType.allScreenshotUrls
//                            }
                            entry.commonAppType.allScreenshotUrls.drop(1)
                        }
                        else -> emptyList()
                    }
                }
                if (screenshotsToDisplay.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(horizontal = 5.dp),
                    ) {
                        items(screenshotsToDisplay, key = { it } ) { screenshotUrl ->
                            AsyncImage(
                                model = screenshotUrl,
                                contentDescription = "Screenshot",
                                modifier = Modifier.size(110.dp).clip(RoundedCornerShape(7.dp)),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                if (platform == Platform.Android && entry.androidCompanion != null) {
                    PropertyRow(
                        name = "COMPANION",
                        value = entry.androidCompanion.name,
                        onClick = {
                            urlLauncher.openUri(entry.androidCompanion.url)
                        },
                    )
                }
                val description = viewModel.selectedStoreEntry?.description ?: entry.description
                if (description != null && description.isNotBlank()) {
                    PropertyRow(
                        name = "DESCRIPTION",
                        value = description,
                        multiRow = true,
                    )
                }

                PropertyRow(
                    name = "DEVELOPER",
                    value = entry.developerName,
                    onClick = if (entry.developerId != null && storeSource != null) {
                        {
                            val developerId = entry.developerId
                            navBarNav.navigateTo(PebbleNavBarRoutes.AppStoreCollectionRoute(
                                sourceId = storeSource.id,
                                path = "dev/$developerId",
                                title = "Developer: ${entry.developerName}"
                            ))
                        }
                    } else {
                        null
                    },
                    onClickIcon = Icons.AutoMirrored.Default.ArrowForward,
                )
                entry.category?.let { category ->
                    PropertyRow(name = "CATEGORY", value = category)
                }
                entry.version?.let { version ->
                    val sideloadedText = if (entry.commonAppType is CommonAppType.Locker && entry.commonAppType.sideloaded) {
                        " (sideloaded)"
                    } else {
                        ""
                    }
                    PropertyRow(name = "VERSION", value = "$version$sideloadedText")
                }
                entry.sourceLink?.let { sourceLink ->
                    PropertyRow(
                        name = "SOURCE CODE",
                        value = "External Link",
                        onClick = {
                            val urlParsed = parseUrl(sourceLink)
                            if (urlParsed != null && urlParsed.protocolOrNull in listOf(URLProtocol.HTTP, URLProtocol.HTTPS)) {
                                urlLauncher.openUri(sourceLink)
                            } else {
                                logger.w { "Not opening invalid URL: $sourceLink" }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PropertyRow(
    name: String,
    value: String,
    onClick: (() -> Unit)? = null,
    onClickIcon: ImageVector = Icons.AutoMirrored.Default.Launch,
    multiRow: Boolean = false,
) {
    Row(modifier = Modifier.padding(5.dp).let{
        if (onClick != null) {
            it.then(Modifier.clickable(onClick = onClick))
        } else {
            it
        }
    }) {
        Text(
            text = name,
            color = Color.Gray,
            modifier = Modifier.width(120.dp),
            maxLines = 1
        )
        if (!multiRow) {
            Text(text = value, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
        }
        if (onClick != null) {
            Icon(
                imageVector = onClickIcon,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp).align(Alignment.CenterVertically)
            )
        }
    }
    if (multiRow) {
        Text(text = value, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun PermissionItem(permission: AppCapability, entry: CommonApp, topBarParams: TopBarParams) {
    if (entry.commonAppType is CommonAppType.Locker) {
        // TODO
    } else {
        AssistChip(
            onClick = {
                topBarParams.showSnackbar(permission.description())
            },
            label = { Text(permission.name()) },
            leadingIcon = { Icon(permission.icon(), null) },
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}

fun AppCapability.icon(): ImageVector = when (this) {
    AppCapability.Health -> Icons.Default.HealthAndSafety
    AppCapability.Location -> Icons.Default.LocationSearching
    AppCapability.Timeline -> Icons.Default.LocationOn
}

fun AppCapability.name(): String = when (this) {
    AppCapability.Health -> "Health"
    AppCapability.Location -> "Location"
    AppCapability.Timeline -> "Timeline"
}

fun AppCapability.description(): String = when (this) {
    AppCapability.Health -> "Can access health data"
    AppCapability.Location -> "Can access location"
    AppCapability.Timeline -> "Can create timeline pins"
}

suspend fun LibPebble.launchApp(
    entry: CommonApp,
    topBarParams: TopBarParams,
    connectedIdentifier: PebbleIdentifier
): Boolean {
    logger.d { "launchApp: ${entry.uuid} - ${entry.title}" }
    val typeText = when (entry.type) {
        AppType.Watchface -> "Watchface"
        AppType.Watchapp -> "WatchApp"
    }
    if (!entry.isSynced()) {
        try {
            withTimeout(15.seconds) {
                topBarParams.showSnackbar("Waiting to sync $typeText to watch...")
                setAppOrder(entry.uuid, -1)
            }
        } catch (_: TimeoutCancellationException) {
            logger.w { "timed out waiting for order change to sync to watch" }
            topBarParams.showSnackbar("$typeText failed to sync to watch")
            return false
        }
    }
    if (!waitUntilAppSyncedToWatch(entry.uuid, connectedIdentifier, 15.seconds)) {
        logger.w { "timed out waiting for blobdb item to sync to watch" }
        topBarParams.showSnackbar("$typeText failed to sync to watch")
        return false
    }
    // Give it a small bit of time to settle
    delay(0.5.seconds)
    topBarParams.showSnackbar("Loading $typeText")
    val launched = withTimeoutOrNull(30.seconds) {
        launchApp(entry.uuid)
        true
    }
    logger.d { "launched = $launched" }
    if (launched == null) {
        topBarParams.showSnackbar("$typeText failed to load")
        return false
    }
    return true
}

fun SnackbarHostState.showSnackbar(scope: CoroutineScope, message: String) {
    scope.launch {
        showSnackbar(message)
    }
}

fun CommonApp.hasSettings(): Boolean = when (commonAppType) {
    is CommonAppType.Locker -> commonAppType.configurable
    is CommonAppType.System -> false
    is CommonAppType.Store -> false
}

suspend fun CommonApp.showSettings(
    navBarNav: NavBarNav,
    libPebble: LibPebble,
    topBarParams: TopBarParams,
) {
    when (commonAppType) {
        is CommonAppType.Locker -> {
            val watch = libPebble.watches.value.filterIsInstance<ConnectedPebbleDevice>()
                .firstOrNull()
            //TODO: Handle multiple watches connected, selector?
            if (watch == null) {
                logger.w("No connected watch found, cannot show settings")
                return
            }
            val session = if (watch.currentPKJSSession.value?.uuid == uuid) {
                watch.currentPKJSSession.value
            } else {
                logger.d("Launching app $uuid for pkjs settings...")
                libPebble.launchApp(this, topBarParams, watch.identifier)
                watch.currentPKJSSession.first { it?.uuid == uuid }
            }
            if (session?.uuid != uuid) {
                logger.e("PKJS session UUID mismatch: expected $uuid, got ${session?.uuid}")
            } else {
                logger.d { "Opening app settings for $uuid" }
                val url = session.requestConfigurationUrl()
                url ?: run {
                    logger.e("No configuration URL returned for app $uuid")
                    return
                }
                logger.d { "Got app settings URL" }
                navBarNav.navigateTo(
                    PebbleRoutes.WatchappSettingsRoute(
                        watch.identifier.asString,
                        title = title,
                        url = url
                    )
                )
            }
        }

        is CommonAppType.System -> Unit

        is CommonAppType.Store -> Unit
    }
}