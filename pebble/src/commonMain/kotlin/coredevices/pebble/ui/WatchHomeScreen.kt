package coredevices.pebble.ui

import CommonRoutes
import CoreNav
import CoreRoute
import NoOpCoreNav
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.BrowseGallery
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
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
import coreapp.pebble.generated.resources.watch_home_change_services_message
import coreapp.pebble.generated.resources.watch_home_change_services_title
import coreapp.pebble.generated.resources.watch_home_index_permissions_message
import coreapp.pebble.generated.resources.watch_home_index_permissions_title
import coreapp.pebble.generated.resources.watch_home_more
import coreapp.pebble.generated.resources.watch_home_sideload_firmware_message
import coreapp.pebble.generated.resources.watch_home_sideload_firmware_title
import coreapp.util.generated.resources.Res as UtilRes
import coreapp.util.generated.resources.back
import coreapp.util.generated.resources.cancel
import coreapp.util.generated.resources.continue_action
import coreapp.util.generated.resources.install
import coredevices.libindex.LibIndex
import coredevices.libindex.device.InterviewedIndexDevice
import coredevices.libindex.device.KnownIndexDevice
import coredevices.pebble.PebbleDeepLinkHandler
import coredevices.pebble.Platform
import coredevices.pebble.rememberLibPebble
import coredevices.pebble.rememberWatches
import coredevices.ui.M3Dialog
import coredevices.ui.SecondaryProfileWarningDialog
import coredevices.util.CoreConfig
import coredevices.util.CoreConfigFlow
import coredevices.util.CoreConfigHolder
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import coredevices.util.SecondaryProfileWarning
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
import kotlinx.coroutines.flow.SharingStarted
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
import kotlin.time.Duration.Companion.seconds

fun LibIndex.isAnyRingPaired() = rings.value.any { it is KnownIndexDevice }

fun PebbleDevice.isFullyConnected() = this is KnownPebbleDevice && runningFwVersion != UNKNOWN_WATCH_SERIAL_OR_VERSION

class WatchOnboardingFinished {
    val finished: Channel<Unit> = Channel<Unit>(capacity = 1)
}

class WatchHomeViewModel(
    private val coreConfig: CoreConfigFlow,
    private val libPebble: LibPebble,
    private val libIndex: LibIndex,
) : ViewModel() {
    /** Null until the bar's tabs are known; then [launchTab]. */
    val selectedTab = mutableStateOf<WatchHomeNavTab?>(null)

    fun launchTab(barTabs: List<WatchHomeNavTab>, watches: List<PebbleDevice>): WatchHomeNavTab {
        val automatic = when {
            coreConfig.value.startOnFirstNavBarTab -> null
            coreConfig.value.enableIndex && libIndex.isAnyRingPaired() -> WatchHomeNavTab.Index
            watches.any { it.isFullyConnected() } -> WatchHomeNavTab.WatchFaces
            else -> WatchHomeNavTab.Watches
        }
        return automatic?.takeIf { it in barTabs } ?: barTabs.first()
    }
    val disableNextTransitionAnimation = mutableStateOf(false)
    val healthTrackingEnabled = libPebble.healthSettings.map {
        it.trackingEnabled
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)
}


private val logger = Logger.withTag("WatchHomeScreen")

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WatchHomeScreen(
    coreNav: CoreNav,
    indexScreen: @Composable (TopBarParams, NavBarNav, CoreNav) -> Unit,
    addExperimentalRoutes: NavGraphBuilder.(CoreNav) -> Unit = {},
    isInnerScopedRoute: (CoreRoute) -> Boolean = { false },
) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val scope = rememberCoroutineScope()
        val viewModel = koinViewModel<WatchHomeViewModel>()
        val coreConfigHolder: CoreConfigHolder = koinInject()
        val coreConfig by coreConfigHolder.config.collectAsState()
        val healthEnabledValue = viewModel.healthTrackingEnabled.collectAsState()
        val libPebble = rememberLibPebble()
        val watches = rememberWatches()
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
        if (healthEnabled == null || watches == null) {
            return
        }

        val tabs = WatchHomeNavTab.availableTabs(coreConfig, healthEnabled)
        val barTabs = WatchHomeNavTab.barTabs(coreConfig, tabs)
        val moreTabs = tabs.drop(barTabs.size)
        val currentTab = viewModel.selectedTab.value?.takeIf { it in tabs }
            ?: viewModel.launchTab(barTabs, watches).also { viewModel.selectedTab.value = it }
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
        val imeVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
        val rootBackUiContext = rememberUiContext()
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
        val libIndex = koinInject<LibIndex>()
        LaunchedEffect(Unit) {
            scope.launch {
                libPebble.userFacingErrors.collect { error ->
                    snackbarHostState.showSnackbar(scope, error.message)
                }
            }
        }
        val deepLinkHandler: PebbleDeepLinkHandler = koinInject()
        val pendingFirmwareSideload by deepLinkHandler.pendingFirmwareSideload.collectAsState()
        pendingFirmwareSideload?.let { pending ->
            AlertDialog(
                onDismissRequest = deepLinkHandler::dismissPendingFirmwareSideload,
                title = { Text(stringResource(Res.string.watch_home_sideload_firmware_title)) },
                text = {
                    Text(stringResource(Res.string.watch_home_sideload_firmware_message, pending.fileName))
                },
                confirmButton = {
                    TextButton(onClick = deepLinkHandler::confirmPendingFirmwareSideload) {
                        Text(stringResource(UtilRes.string.install))
                    }
                },
                dismissButton = {
                    TextButton(onClick = deepLinkHandler::dismissPendingFirmwareSideload) {
                        Text(stringResource(UtilRes.string.cancel))
                    }
                },
            )
        }
        val pendingBootConfig by deepLinkHandler.pendingBootConfig.collectAsState()
        pendingBootConfig?.let { pending ->
            AlertDialog(
                onDismissRequest = deepLinkHandler::dismissPendingBootConfig,
                title = { Text(stringResource(Res.string.watch_home_change_services_title)) },
                text = {
                    Text(stringResource(Res.string.watch_home_change_services_message, pending.host))
                },
                confirmButton = {
                    TextButton(onClick = deepLinkHandler::confirmPendingBootConfig) {
                        Text(stringResource(UtilRes.string.continue_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = deepLinkHandler::dismissPendingBootConfig) {
                        Text(stringResource(UtilRes.string.cancel))
                    }
                },
            )
        }
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
                        is PebbleNavBarRoutes.WatchfacesRoute -> WatchHomeNavTab.WatchFaces
                        is PebbleNavBarRoutes.LockerAppRoute -> WatchHomeNavTab.WatchFaces
                        is PebbleNavBarRoutes.AppstoreSettingsRoute -> WatchHomeNavTab.WatchFaces
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
        val settings: Settings = koinInject()

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
                title = { Text(stringResource(Res.string.watch_home_index_permissions_title)) },
                buttons = {
                    TextButton(onClick = {
                        coreConfigHolder.update(coreConfig.copy(enableIndex = false))
                    }) {
                        Text(stringResource(UtilRes.string.cancel))
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
                        Text(stringResource(UtilRes.string.continue_action))
                    }
                },
            ) {
                Text(stringResource(Res.string.watch_home_index_permissions_message))
                Spacer(Modifier.height(8.dp))
                for (permission in missingRequiredPermissions) {
                    Text(permission.name(), fontWeight = FontWeight.Bold)
                    Text(permission.description())
                }
            }
        }

        val secondaryProfileWarning = koinInject<SecondaryProfileWarning>()
        var secondaryProfileWarningPending by remember { mutableStateOf(secondaryProfileWarning.pending) }
        if (secondaryProfileWarningPending) {
            SecondaryProfileWarningDialog { secondaryProfileWarningPending = false }
        }

        val ringsFlow = remember {
            libIndex.rings
        }
        val rings by ringsFlow.collectAsState(initial = libIndex.rings.value)
        var hasSeenWatchOnboarding by remember { mutableStateOf(settings.hasSeenWatchOnboarding())}
        var hasSeenRingOnboarding by remember { mutableStateOf(settings.hasSeenRingOnboarding())}
        var hasSeenPermissionsIntro by remember { mutableStateOf(settings.hasSeenPermissionsIntro()) }
        var startingOnboarding = false
        if (watches.any { it is CommonConnectedDevice } && !hasSeenWatchOnboarding) {
            hasSeenWatchOnboarding = true
            settings.setHasSeenWatchOnboarding(true)
            coreNav.navigateTo(CommonRoutes.WatchOnboardingRoute)
            startingOnboarding = true
        }
        if (rings.any { it is InterviewedIndexDevice && !hasSeenRingOnboarding }) {
            hasSeenRingOnboarding = true
            coreConfigHolder.update(coreConfig.copy(enableIndex = true))
            coreNav.navigateTo(CommonRoutes.RingOnboardingRoute)
            startingOnboarding = true
        }
        // Watch and ring onboarding show this themselves, so only people past one get the dialog.
        if (!startingOnboarding && (hasSeenWatchOnboarding || hasSeenRingOnboarding) &&
            !hasSeenPermissionsIntro
        ) {
            PermissionsIntroDialog(onDismissRequest = {
                hasSeenPermissionsIntro = true
                settings.setHasSeenPermissionsIntro(true)
            })
        }

        Scaffold(
            bottomBar = {
                fun selectTab(route: WatchHomeNavTab) {
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
                }
                NavigationBar(
                    modifier = Modifier.height(navBarHeight),
                ) {
                    barTabs.forEach { route ->
                        NavigationBarItem(
                            selected = viewModel.selectedTab.value == route,
                            onClick = { selectTab(route) },
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
                    if (moreTabs.isNotEmpty()) {
                        var showMore by remember { mutableStateOf(false) }
                        val color = if (currentTab in moreTabs) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        NavigationBarItem(
                            selected = currentTab in moreTabs,
                            onClick = { showMore = true },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        val badgeNum = moreTabs.sumOf { it.badge?.invoke() ?: 0 }
                                        if (badgeNum > 0) {
                                            Badge {
                                                Text(text = "$badgeNum")
                                            }
                                        }
                                    },
                                ) {
                                    Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = color)
                                }
                                DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                                    moreTabs.forEach { route ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(route.title)) },
                                            leadingIcon = { Icon(route.icon, contentDescription = null) },
                                            trailingIcon = {
                                                val badgeNum = route.badge?.invoke() ?: 0
                                                if (badgeNum > 0) {
                                                    Badge {
                                                        Text(text = "$badgeNum")
                                                    }
                                                }
                                            },
                                            onClick = {
                                                showMore = false
                                                selectTab(route)
                                            },
                                        )
                                    }
                                }
                            },
                            label = {
                                Text(stringResource(Res.string.watch_home_more), fontSize = 9.sp, color = color, maxLines = 1)
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
            val currentEntry by pebbleNavHostController.currentBackStackEntryAsState()
            val screen: @Composable (NavBackStackEntry, @Composable (TopBarParams) -> Unit) -> Unit = { entry, content ->
                // We'll probably move the top bar completely to each screen in the future - but
                // that is a big change so that is not done yet
                val topBar = remember(entry) { ScreenTopBarState() }
                val topBarParams = remember(entry) {
                    TopBarParams(
                        searchAvailable = { topBar.searchState = it },
                        actions = { topBar.actions = it },
                        title = { topBar.title = it },
                        overrideGoBack = overrideGoBack,
                        showSnackbar = { scope.launch { snackbarHostState.showSnackbar(message = it) } },
                        scrollToTop = scrollToTopFlow,
                    )
                }
                // Must not depend on the stack's current top: a predictive back composes the revealed entry before popping.
                val canGoBack = entry.destination.id != pebbleNavHostController.graph.startDestinationId
                val isCurrent = currentEntry == entry
                val searchShown = topBar.searchState?.show == true
                BackHandler(enabled = isCurrent && searchShown) {
                    topBar.searchState?.show = false
                    topBar.searchState?.query = ""
                }
                BackHandler(enabled = isCurrent && !searchShown && !canGoBack) {
                    if (!moveCurrentTaskToBackground(rootBackUiContext)) {
                        coreNav.goBack()
                    }
                }
                Column {
                    // Index renders its own header.
                    if (!entry.destination.hasRoute<PebbleNavBarRoutes.IndexRoute>()) {
                        ScreenTopBar(
                            state = topBar,
                            canGoBack = canGoBack,
                            onBack = {
                                if (overrideGoBack.subscriptionCount.value > 0) {
                                    overrideGoBack.tryEmit(Unit)
                                } else {
                                    pebbleNavHostController.popBackStack()
                                }
                            },
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        content(topBarParams)
                    }
                }
            }
            // CoreNav scoped to the inner NavHost: routes flagged by
            // [isInnerScopedRoute] navigate via the inner controller so
            // the chrome's bottom NavigationBar stays visible. Anything
            // else delegates to the outer [coreNav]. Used both by the
            // index screen content and by the experimental detail
            // routes registered below.
            val scopedCoreNav = remember(pebbleNavHostController, coreNav, isInnerScopedRoute) {
                object : CoreNav {
                    override fun navigateTo(route: CoreRoute) {
                        if (route is CommonRoutes.LockerAppRoute) {
                            pebbleNavHostController.navigate(route.toNavBarRoute())
                        } else if (isInnerScopedRoute(route)) {
                            pebbleNavHostController.navigate(route)
                        } else {
                            coreNav.navigateTo(route)
                        }
                    }

                    override fun goBack() {
                        if (pebbleNavHostController.previousBackStackEntry != null) {
                            pebbleNavHostController.popBackStack()
                        } else {
                            coreNav.goBack()
                        }
                    }

                    override fun goBackToPebble() {
                        coreNav.goBackToPebble()
                    }

                    override fun replaceWith(route: CoreRoute) {
                        if (isInnerScopedRoute(route)) {
                            pebbleNavHostController.popBackStack()
                            pebbleNavHostController.navigate(route)
                        } else {
                            coreNav.replaceWith(route)
                        }
                    }
                }
            }
            val navBarNav = remember(pebbleNavHostController, scopedCoreNav) {
                object : NavBarNav {
                    override fun navigateTo(route: CoreRoute) {
                        // Defer to the scoped variant so RingRoutes etc.
                        // stay inside the bottom-nav chrome.
                        scopedCoreNav.navigateTo(route)
                    }

                    override fun navigateTo(route: NavBarRoute) {
                        pebbleNavHostController.navigate(route)
                    }

                    override fun goBack() {
                        if (pebbleNavHostController.previousBackStackEntry != null) {
                            pebbleNavHostController.popBackStack()
                        }
                    }
                }
            }

            // Screens draw their own top bar, including the status bar inset.
            val effectiveInsets = PaddingValues(
                start = windowInsets.calculateStartPadding(LocalLayoutDirection.current),
                end = windowInsets.calculateEndPadding(LocalLayoutDirection.current),
                top = 0.dp,
                bottom = if (imeVisible) 0.dp else windowInsets.calculateBottomPadding(),
            )
            // Wrap each tab's NavHost in SaveableStateHolder to preserve state
            saveableStateHolder.SaveableStateProvider(key = currentTab) {
                NavHost(
                    pebbleNavHostController,
                    startDestination = currentTab.route,
                    modifier = Modifier.padding(effectiveInsets),
                ) {
                    addNavBarRoutes(navBarNav, screen, indexScreen, scopedCoreNav, viewModel)
                    // Register Ring/Index detail routes inside the inner
                    // NavHost so they render with the bottom NavigationBar
                    // still visible.
                    addExperimentalRoutes(scopedCoreNav)
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
fun Settings.setHasSeenRingOnboarding(seen: Boolean) = set(HAS_SEEN_RING_ONBOARDING_SETTINGS_KEY, seen)

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
        PebbleNavBarRoutes.IndexRoute,
        { indexTabBadgeCount() }
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
        /** Every tab in the user's order; tabs missing from the config go last. */
        fun orderedTabs(config: CoreConfig): List<WatchHomeNavTab> =
            entries.sortedBy { tab ->
                config.navBarTabs.orEmpty().indexOf(tab.name).takeIf { it >= 0 } ?: Int.MAX_VALUE
            }

        fun isAvailable(tab: WatchHomeNavTab, config: CoreConfig, healthEnabled: Boolean): Boolean =
            (tab != Index || config.enableIndex) && (tab != Health || healthEnabled)

        fun availableTabs(config: CoreConfig, healthEnabled: Boolean): List<WatchHomeNavTab> =
            orderedTabs(config).filter { isAvailable(it, config, healthEnabled) }

        fun barSlots(config: CoreConfig): Int = config.navBarSlots.coerceIn(2, MAX_BAR_SLOTS)

        /** The leading [tabs] that go in the bar; if they don't all fit, one slot is More. */
        fun barTabs(config: CoreConfig, tabs: List<WatchHomeNavTab>): List<WatchHomeNavTab> =
            tabs.take(if (tabs.size <= barSlots(config)) tabs.size else barSlots(config) - 1)

        const val MAX_BAR_SLOTS = 6
    }
}

@Composable
private fun indexTabBadgeCount(): Int {
    val badge: IndexTabBadge = koinInject()
    return badge.count.collectAsState(0).value
}

@Preview
@Composable
fun WatchHomePreview() {
    PreviewWrapper {
        val viewModel: WatchHomeViewModel = koinInject()
        viewModel.selectedTab.value = WatchHomeNavTab.Watches
        WatchHomeScreen(NoOpCoreNav, { _, _, _ ->})
    }
}
