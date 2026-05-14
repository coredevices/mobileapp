package coredevices.pebble.ui

import CommonRoutes
import CoreNav
import CoreRoute
import NoOpCoreNav
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.BrowseGallery
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopSearchBar
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.apps
import coreapp.pebble.generated.resources.devices
import coreapp.pebble.generated.resources.health
import coreapp.pebble.generated.resources.index
import coreapp.pebble.generated.resources.notifications
import coreapp.pebble.generated.resources.settings
import coreapp.util.generated.resources.back
import coredevices.libindex.LibIndex
import coredevices.libindex.device.InterviewedIndexDevice
import coredevices.pebble.PebbleDeepLinkHandler
import coredevices.pebble.Platform
import coredevices.pebble.rememberLibPebble
import coredevices.ui.M3Dialog
import coredevices.util.CoreConfigFlow
import coredevices.util.CoreConfigHolder
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import coredevices.util.description
import coredevices.util.name
import coredevices.util.rememberUiContext
import io.rebble.libpebblecommon.connection.CommonConnectedDevice
import io.rebble.libpebblecommon.connection.KnownPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.PebbleDevice
import io.rebble.libpebblecommon.connection.UNKNOWN_WATCH_SERIAL_OR_VERSION
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun LibPebble.haveSeenFullyConnectedWatch() = watches.value.any {
    it.isFullyConnected()
}

fun PebbleDevice.isFullyConnected() = this is KnownPebbleDevice && runningFwVersion != UNKNOWN_WATCH_SERIAL_OR_VERSION

class WatchOnboardingFinished {
    val finished: Channel<Unit> = Channel<Unit>(capacity = 1)
}

class WatchHomeViewModel(
    coreConfig: CoreConfigFlow,
    libPebble: LibPebble,
) : ViewModel() {
    val selectedTab = mutableStateOf(if (libPebble.haveSeenFullyConnectedWatch()) {
        WatchHomeNavTab.WatchFaces
    } else {
        WatchHomeNavTab.Watches
    })
    private val actionsFlow = MutableStateFlow<@Composable RowScope.() -> Unit>({})
    private val searchStateFlow = MutableStateFlow<SearchState?>(null)
    private val titleFlow = MutableStateFlow("")
    private val canGoBackFlow = MutableStateFlow(false)
    val disableNextTransitionAnimation = mutableStateOf(false)
    val indexEnabled = coreConfig.flow.map {
        it.enableIndex
    }.stateIn(viewModelScope, SharingStarted.Lazily, coreConfig.value.enableIndex)
    val healthTrackingEnabled = libPebble.healthSettings.map {
        it.trackingEnabled
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)
    val preferHealthTab = coreConfig.flow.map {
        it.preferHealthTab
    }.stateIn(viewModelScope, SharingStarted.Lazily, coreConfig.value.preferHealthTab)
    val paramsFlow = combine(actionsFlow, searchStateFlow, titleFlow, canGoBackFlow) { actions, searchState, title, canGoBack ->
        Params(actions, searchState, title, canGoBack)
    }.debounce(50.milliseconds)

    fun setActions(actions: @Composable RowScope.() -> Unit) {
        actionsFlow.value = actions
    }
    fun setTitle(title: String) {
        titleFlow.value = title
    }
    fun setSearchState(searchState: SearchState?) {
        searchStateFlow.value = searchState
    }
    fun setCanGoBack(canGoBack: Boolean) {
        canGoBackFlow.value = canGoBack
    }
}

data class Params(
    val actions: @Composable RowScope.() -> Unit = {},
    val searchState: SearchState? = null,
    val title: String = "",
    val canGoBack: Boolean = false,
)

private val logger = Logger.withTag("WatchHomeScreen")

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WatchHomeScreen(coreNav: CoreNav, indexScreen: @Composable (TopBarParams, NavBarNav) -> Unit) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val scope = rememberCoroutineScope()
        val viewModel = koinViewModel<WatchHomeViewModel>()
        val indexEnabled = viewModel.indexEnabled.collectAsState()
        val healthEnabledValue = viewModel.healthTrackingEnabled.collectAsState()
        val preferHealthTab = viewModel.preferHealthTab.collectAsState()
        val watchOnboardingFinished: WatchOnboardingFinished = koinInject()

        // Create a SaveableStateHolder to preserve state for each tab
        val saveableStateHolder = rememberSaveableStateHolder()

        // Create NavControllers for each tab
        val watchesNavController = rememberNavController()
        val watchfacesNavController = rememberNavController()
        val notificationsNavController = rememberNavController()
        val indexNavController = rememberNavController()
        val healthNavController = rememberNavController()
        val settingsNavController = rememberNavController()

        val navControllers = remember(
            watchesNavController,
            watchfacesNavController,
            notificationsNavController,
            indexNavController,
            healthNavController,
            settingsNavController
        ) {
            mapOf(
                WatchHomeNavTab.Watches to watchesNavController,
                WatchHomeNavTab.WatchFaces to watchfacesNavController,
                WatchHomeNavTab.Notifications to notificationsNavController,
                WatchHomeNavTab.Index to indexNavController,
                WatchHomeNavTab.Health to healthNavController,
                WatchHomeNavTab.Settings to settingsNavController,
            )
        }
        val healthEnabled = healthEnabledValue.value
        if (healthEnabled == null) {
            return
        }

        val currentEntries = WatchHomeNavTab.navBarEntries(indexEnabled.value, healthEnabled, preferHealthTab.value)
        val currentTab = viewModel.selectedTab.value.let { tab ->
            if (tab in currentEntries) tab else currentEntries.first().also { viewModel.selectedTab.value = it }
        }
        val pebbleNavHostController = navControllers[currentTab]!!

        LaunchedEffect(Unit) {
            watchOnboardingFinished.finished.receiveAsFlow().collect {
                logger.d { "Onboarding finished - switching to apps tab" }
                viewModel.selectedTab.value = WatchHomeNavTab.WatchFaces
            }
        }

        DisposableEffect(pebbleNavHostController) {
            val listener =
                NavController.OnDestinationChangedListener { controller, destination, arguments ->
                    val route = destination.route
                    logger.d("NavBarNav: Destination Changed to route='$route'")
                    scope.launch {
                        // Reset animations after they have had time to start
                        delay(50)
                        viewModel.disableNextTransitionAnimation.value = false
                    }
                    viewModel.setCanGoBack(pebbleNavHostController.previousBackStackEntry != null)
                }
            pebbleNavHostController.addOnDestinationChangedListener(listener)
            onDispose {
                pebbleNavHostController.removeOnDestinationChangedListener(listener)
            }
        }
        val overrideGoBack = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
        val scrollToTopFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
        val systemNavBarBottomHeight =
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val platform = koinInject<Platform>()
        val navBarHeight = remember(systemNavBarBottomHeight, platform) {
            when (platform) {
                Platform.Android -> {
                    val offset = if (systemNavBarBottomHeight > 25.dp) 10.dp else 0.dp
                    systemNavBarBottomHeight + 70.dp - offset
                }

                Platform.IOS -> 90.dp
            }
        }
        val snackbarHostState = remember { SnackbarHostState() }
        val libPebble = rememberLibPebble()
        val libIndex = koinInject<LibIndex>()
        LaunchedEffect(Unit) {
            scope.launch {
                libPebble.userFacingErrors.collect { error ->
                    snackbarHostState.showSnackbar(scope, error.message)
                }
            }
        }
        val deepLinkHandler: PebbleDeepLinkHandler = koinInject()
        LaunchedEffect(Unit) {
            scope.launch {
                deepLinkHandler.snackBarMessages.collect { message ->
                    snackbarHostState.showSnackbar(scope, message)
                }
            }
            scope.launch {
                deepLinkHandler.navigateToPebbleDeepLink.collect {
                    if (it == null || it.consumed) {
                        return@collect
                    }
                    it.consumed = true
                    logger.v { "navigateToPebbleDeepLink: $it" }
                    val tab = when (it.route) {
                        is PebbleNavBarRoutes.LockerAppRoute -> WatchHomeNavTab.WatchFaces
                        is PebbleNavBarRoutes.IndexRoute -> WatchHomeNavTab.Index
                        is PebbleNavBarRoutes.WatchesRoute -> WatchHomeNavTab.Watches
                        else -> null
                    }
                    if (tab != null) {
                        val controller = navControllers[tab]!!
                        viewModel.selectedTab.value = tab
                        if (controller.waitUntilReady(1.seconds)) {
                            logger.v { "Deep link route: ${it.route}" }
                            // Pop back to the tab's start destination so we never end
                            // up with a nested instance of the same screen.
                            controller.popBackStack(tab.route, inclusive = false)
                            if (it.route != tab.route) {
                                controller.navigate(it.route)
                            }
                        }
                    }
                }
            }
        }
        val params by viewModel.paramsFlow.collectAsState(Params())
        val settings: Settings = koinInject()

        val coreConfigHolder: CoreConfigHolder = koinInject()
        val coreConfig by coreConfigHolder.config.collectAsState()
        val permissionRequester: PermissionRequester = koinInject()
        val missingPermissions = permissionRequester.missingPermissions.collectAsState()
        val missingRequiredPermissions by remember(missingPermissions) {
            derivedStateOf {
                missingPermissions.value.filter {
                    it in listOf(
                        Permission.SetAlarms,
                        Permission.Reminders
                    )
                }
            }
        }
        if (
            coreConfig.enableIndex &&
            !coreConfig.indexPermissionsConfirmed &&
            missingRequiredPermissions.isNotEmpty()
        ) {
            val uiContext = rememberUiContext()
            M3Dialog(
                onDismissRequest = {
                    coreConfigHolder.update(coreConfig.copy(enableIndex = false))
                },
                title = { Text("Index Permissions") },
                buttons = {
                    TextButton(onClick = {
                        coreConfigHolder.update(coreConfig.copy(enableIndex = false))
                    }) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        coreConfigHolder.update(coreConfig.copy(indexPermissionsConfirmed = true))
                        if (uiContext != null) {
                            scope.launch {
                                for (permission in missingRequiredPermissions) {
                                    permissionRequester.requestPermission(permission, uiContext)
                                }
                            }
                        }
                    }) {
                        Text("Continue")
                    }
                },
            ) {
                Text("Index requires additional permissions to function.\n" +
                        "Please grant the following permissions:")
                Spacer(Modifier.height(8.dp))
                for (permission in missingRequiredPermissions) {
                    Text(permission.name(), fontWeight = FontWeight.Bold)
                    Text(permission.description())
                }
            }
        }

        val watchesFlow = remember {
            libPebble.watches
                .map { it.sortedWith(PebbleDeviceComparator) }
        }
        val watches by watchesFlow.collectAsState(
            initial = libPebble.watches.value.sortedWith(
                PebbleDeviceComparator
            )
        )
        val ringsFlow = remember {
            libIndex.rings
        }
        val rings by ringsFlow.collectAsState(initial = libIndex.rings.value)
        var hasSeenWatchOnboarding by remember { mutableStateOf(settings.hasSeenWatchOnboarding())}
        var hasSeenRingOnboarding by remember { mutableStateOf(settings.hasSeenRingOnboarding())}
        if (watches.any { it is CommonConnectedDevice } && !hasSeenWatchOnboarding) {
            hasSeenWatchOnboarding = true
            settings.setHasSeenWatchOnboarding(true)
            coreNav.navigateTo(CommonRoutes.WatchOnboardingRoute)
        }
        if (rings.any { it is InterviewedIndexDevice && !hasSeenRingOnboarding }) {
            hasSeenRingOnboarding = true
            coreConfigHolder.update(coreConfig.copy(enableIndex = true))
            settings.setHasSeenRingOnboarding(true)
            coreNav.navigateTo(CommonRoutes.RingOnboardingRoute)
        }

        Scaffold(
            topBar = {
                Crossfade(
                    modifier = Modifier.animateContentSize(),
                    targetState = params.searchState?.show == true,
                    label = "Search"
                ) { showSearch ->
                    val focusRequester = remember { FocusRequester() }
                    val focusManager = LocalFocusManager.current
                    val keyboardController = LocalSoftwareKeyboardController.current
                    val onSearchDone = {
                        params.searchState?.typing = false
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                    if (showSearch) {
                        LaunchedEffect(focusRequester) {
                            focusRequester.requestFocus()
                        }
                        TopSearchBar(
                            state = rememberSearchBarState(),
                            inputField = {
                                SearchBarDefaults.InputField(
                                    query = params.searchState?.query ?: "",
                                    onQueryChange = {
                                        params.searchState?.query = it
                                        params.searchState?.typing = true
                                    },
                                    onSearch = {
                                        onSearchDone()
                                    },
                                    expanded = false,
                                    onExpandedChange = { },
                                    placeholder = { Text("Search") },
                                    modifier = Modifier.focusRequester(focusRequester),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            params.searchState?.show = false
                                            params.searchState?.query = ""
                                        }) {
                                            Icon(
                                                Icons.Outlined.Close,
                                                contentDescription = "Clear search"
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        IconButton(onClick = onSearchDone) {
                                            Icon(
                                                Icons.Outlined.Search,
                                                contentDescription = "Search"
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    } else {
                        TopAppBar(
                            navigationIcon = {
                                AnimatedVisibility(
                                    visible = params.canGoBack,
                                    enter = fadeIn() + expandHorizontally(),
                                    exit = fadeOut() + shrinkHorizontally()
                                ) {
                                    IconButton(onClick = {
                                        if (overrideGoBack.subscriptionCount.value > 0) {
                                            overrideGoBack.tryEmit(Unit)
                                        } else {
                                            pebbleNavHostController.popBackStack()
                                        }
                                    }) {
                                        Icon(
                                            Icons.AutoMirrored.Default.ArrowBack,
                                            contentDescription = stringResource(coreapp.util.generated.resources.Res.string.back)
                                        )
                                    }
                                }
                            },
                            title = {
                                Text(
                                    text = params.title,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 28.sp,
                                    maxLines = 1,
                                )
                            },
                            actions = {
                                params.actions(this)
                                if (params.searchState != null) {
                                    TopBarIconButtonWithToolTip(
                                        onClick = { params.searchState?.show = true },
                                        icon = Icons.Filled.Search,
                                        description = "Search",
                                    )
                                }
                            }
                        )
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.height(navBarHeight),
                ) {
                    WatchHomeNavTab.navBarEntries(indexEnabled.value, healthEnabled, preferHealthTab.value).forEach { route ->
                        NavigationBarItem(
                            selected = viewModel.selectedTab.value == route,
                            onClick = {
                                if (viewModel.selectedTab.value == route) {
                                    val popped = pebbleNavHostController.popBackStack(route.route::class, false)
                                    if (!popped) {
                                        scrollToTopFlow.tryEmit(Unit)
                                    }
                                } else {
                                    // Disable animations when switching between tabs
                                    viewModel.disableNextTransitionAnimation.value = true
                                }
                                viewModel.selectedTab.value = route
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (route.badge != null) {
                                            val badgeNum = route.badge()
                                            if (badgeNum > 0) {
                                                Badge {
                                                    Text(text = "$badgeNum")
                                                }
                                            }
                                        }
                                    },
                                ) {
                                    Icon(
                                        route.icon,
                                        contentDescription = null,
                                        tint = if (viewModel.selectedTab.value == route) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            label = {
                                Text(
                                    stringResource(route.title),
                                    fontSize = 9.sp,
                                    color = if (viewModel.selectedTab.value == route) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent
                            ),
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { windowInsets ->
            val topBarParams = remember(pebbleNavHostController) {
                TopBarParams(
                    searchAvailable = { viewModel.setSearchState(it) },
                    actions = { viewModel.setActions(it) },
                    title = { viewModel.setTitle(it) },
                    overrideGoBack = overrideGoBack,
                    showSnackbar = { scope.launch { snackbarHostState.showSnackbar(message = it) } },
                    scrollToTop = scrollToTopFlow,
                )
            }
            val navBarNav = remember(pebbleNavHostController) {
                object : NavBarNav {
                    override fun navigateTo(route: CoreRoute) {
                        coreNav.navigateTo(route)
                    }

                    override fun navigateTo(route: NavBarRoute) {
                        pebbleNavHostController.navigate(route)
                    }

                    override fun goBack() {
                        pebbleNavHostController.popBackStack()
                    }
                }
            }

            // Wrap each tab's NavHost in SaveableStateHolder to preserve state
            saveableStateHolder.SaveableStateProvider(key = currentTab) {
                NavHost(
                    pebbleNavHostController,
                    startDestination = currentTab.route,
                    modifier = Modifier.padding(windowInsets),
                ) {
                    addNavBarRoutes(navBarNav, topBarParams, indexScreen, viewModel)
                }
                // Handle back button when search bar is visible
                // Placed AFTER NavHost so it registers later and takes priority
                BackHandler(enabled = params.searchState?.show == true) {
                    params.searchState?.show = false
                    params.searchState?.query = ""
                }
            }
        }
    }
}

private const val HAS_SEEN_WATCH_ONBOARDING_SETTINGS_KEY = "hasSeenWatchOnboarding"
private const val HAS_SEEN_RING_ONBOARDING_SETTINGS_KEY = "hasSeenRingOnboarding"
private fun Settings.hasSeenWatchOnboarding() = getBoolean(HAS_SEEN_WATCH_ONBOARDING_SETTINGS_KEY, false)
private fun Settings.setHasSeenWatchOnboarding(seen: Boolean) = set(HAS_SEEN_WATCH_ONBOARDING_SETTINGS_KEY, seen)
private fun Settings.hasSeenRingOnboarding() = getBoolean(HAS_SEEN_RING_ONBOARDING_SETTINGS_KEY, false)
private fun Settings.setHasSeenRingOnboarding(seen: Boolean) = set(HAS_SEEN_RING_ONBOARDING_SETTINGS_KEY, seen)

/**
 * NavController crashes if we navigate before it is ready
 */
suspend fun NavHostController.waitUntilReady(timeout: Duration): Boolean {
    return withTimeoutOrNull(timeout) {
        while (true) {
            val hasGraph = try {
                graph != null
            } catch (_: IllegalStateException) {
                false
            }
            if (hasGraph) {
                return@withTimeoutOrNull true
            }
            delay(25)
        }
        false
    } ?: false
}

enum class WatchHomeNavTab(
    val title: StringResource,
    val icon: ImageVector,
    val route: NavBarRoute,
    val badge: (@Composable () -> Int)? = null,
) {
    WatchFaces(Res.string.apps, Icons.Filled.BrowseGallery, PebbleNavBarRoutes.WatchfacesRoute),
    Index(
        Res.string.index,
        Icons.AutoMirrored.Outlined.Notes,
        PebbleNavBarRoutes.IndexRoute
    ),
    Watches(Res.string.devices, Icons.Outlined.Watch, PebbleNavBarRoutes.WatchesRoute),
    Health(Res.string.health, Icons.AutoMirrored.Filled.DirectionsRun, PebbleNavBarRoutes.HealthRoute),
    Notifications(
        Res.string.notifications,
        Icons.Outlined.Notifications,
        PebbleNavBarRoutes.NotificationsRoute
    ),
    Settings(
        Res.string.settings,
        Icons.Outlined.Tune,
        PebbleNavBarRoutes.WatchSettingsRoute,
        { settingsBadgeTotal() });

    companion object {
        fun navBarEntries(
            indexEnabled: Boolean,
            healthEnabled: Boolean,
            preferHealthTab: Boolean,
        ): List<WatchHomeNavTab> {
            val tabs = mutableListOf(WatchFaces)
            if (indexEnabled) tabs.add(Index)
            tabs.add(Watches)
            if (healthEnabled && (!indexEnabled || preferHealthTab)) tabs.add(Health)
            if (!indexEnabled || !healthEnabled || !preferHealthTab) tabs.add(Notifications)
            tabs.add(Settings)
            return tabs
        }
    }
}

@Preview
@Composable
fun WatchHomePreview() {
    PreviewWrapper {
        val viewModel: WatchHomeViewModel = koinInject()
        viewModel.selectedTab.value = WatchHomeNavTab.Watches
        WatchHomeScreen(NoOpCoreNav,  { _, _ ->})
    }
}

@Composable
fun TopBarIconButtonWithToolTip(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
) {
    val tooltipState = remember { TooltipState(isPersistent = false) }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(description)
            }
        },
        state = tooltipState
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = icon,
                contentDescription = description
            )
        }
    }
}