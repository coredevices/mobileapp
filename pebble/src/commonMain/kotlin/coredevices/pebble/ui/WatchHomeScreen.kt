package coredevices.pebble.ui

import CoreNav
import CoreRoute
import NoOpCoreNav
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.AutoAwesomeMotion
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.apps
import coreapp.pebble.generated.resources.devices
import coreapp.pebble.generated.resources.faces
import coreapp.pebble.generated.resources.index
import coreapp.pebble.generated.resources.notifications
import coreapp.pebble.generated.resources.settings
import coreapp.util.generated.resources.back
import coredevices.pebble.PebbleDeepLinkHandler
import coredevices.pebble.Platform
import coredevices.pebble.rememberLibPebble
import coredevices.util.CompanionDevice
import coredevices.util.CoreConfigFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

class WatchHomeViewModel(coreConfig: CoreConfigFlow) : ViewModel() {
    val selectedTab = mutableStateOf(WatchHomeNavTab.Watches)
    val searchQuery = mutableStateOf(SearchState(query = "", typing = false))
    val showSearch = mutableStateOf(false)
    val searchAvailable = mutableStateOf(false)
    val actions = mutableStateOf<@Composable RowScope.() -> Unit>({})
    val title = mutableStateOf("")
    val canGoBack = mutableStateOf(false)
    val disableNextTransitionAnimation = mutableStateOf(false)
    val indexEnabled = coreConfig.flow.map {
        it.enableIndex
    }.stateIn(viewModelScope, SharingStarted.Lazily, coreConfig.value.enableIndex)
}

private val logger = Logger.withTag("WatchHomeScreen")

@Composable
fun WatchHomeScreen(coreNav: CoreNav, indexScreen: @Composable (TopBarParams, NavBarNav) -> Unit) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val scope = rememberCoroutineScope()
        val viewModel = koinViewModel<WatchHomeViewModel>()
        val indexEnabled = viewModel.indexEnabled.collectAsState()

        // Create a SaveableStateHolder to preserve state for each tab
        val saveableStateHolder = rememberSaveableStateHolder()

        // Create NavControllers for each tab
        val watchesNavController = rememberNavController()
        val watchfacesNavController = rememberNavController()
        val watchappsNavController = rememberNavController()
        val notificationsNavController = rememberNavController()
        val indexNavController = rememberNavController()
        val settingsNavController = rememberNavController()

        val navControllers = remember(
            watchesNavController,
            watchfacesNavController,
            watchappsNavController,
            notificationsNavController,
            indexNavController,
            settingsNavController
        ) {
            mapOf(
                WatchHomeNavTab.Watches to watchesNavController,
                WatchHomeNavTab.WatchFaces to watchfacesNavController,
                WatchHomeNavTab.WatchApps to watchappsNavController,
                WatchHomeNavTab.Notifications to notificationsNavController,
                WatchHomeNavTab.Index to indexNavController,
                WatchHomeNavTab.Settings to settingsNavController,
            )
        }

        val currentTab = viewModel.selectedTab.value
        val pebbleNavHostController = navControllers[currentTab]!!

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
        val goBack = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
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
        }
        val companionDevice: CompanionDevice = koinInject()

        Scaffold(
            topBar = {
                Crossfade(
                    modifier = Modifier.animateContentSize(),
                    targetState = viewModel.showSearch.value,
                    label = "Search"
                ) { showSearch ->
                    val focusRequester = remember { FocusRequester() }
                    val focusManager = LocalFocusManager.current
                    val keyboardController = LocalSoftwareKeyboardController.current
                    val onSearchDone = {
                        viewModel.searchQuery.value =
                            viewModel.searchQuery.value.copy(typing = false)
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                    if (showSearch) {
                        LaunchedEffect(focusRequester) {
                            focusRequester.requestFocus()
                        }
                        // TODO SearchBar
                        OutlinedTextField(
                            value = viewModel.searchQuery.value.query,
                            onValueChange = {
                                viewModel.searchQuery.value = SearchState(query = it, typing = true)
                            },
                            label = { Text("Search") },
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.showSearch.value = false
                                    viewModel.searchQuery.value =
                                        SearchState(query = "", typing = false)
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
                            modifier = Modifier.fillMaxWidth()
                                .padding(TopAppBarDefaults.windowInsets.asPaddingValues())
                                .focusRequester(focusRequester),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearchDone() }),
                        )
                    } else {
                        TopAppBar(
                            navigationIcon = {
                                if (viewModel.canGoBack.value) {
                                    IconButton(onClick = { goBack.tryEmit(Unit) }) {
                                        Icon(
                                            Icons.AutoMirrored.Default.ArrowBack,
                                            contentDescription = stringResource(coreapp.util.generated.resources.Res.string.back)
                                        )
                                    }
                                }
                            },
                            title = {
                                Text(
                                    text = viewModel.title.value,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 28.sp,
                                    maxLines = 1,
                                )
                            },
                            actions = {
                                viewModel.actions.value(this)
                                if (viewModel.searchAvailable.value) {
                                    TopBarIconButtonWithToolTip(
                                        onClick = { viewModel.showSearch.value = true },
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
                    WatchHomeNavTab.navBarEntries(indexEnabled.value).forEach { route ->
                        NavigationBarItem(
                            selected = viewModel.selectedTab.value == route,
                            onClick = {
                                if (viewModel.selectedTab.value == route) {
                                    pebbleNavHostController.popBackStack(route.route::class, false)
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
            val topBarParams = remember(viewModel.searchQuery.value) {
                TopBarParams(
                    searchState = viewModel.searchQuery.value,
                    searchAvailable = {
                        logger.d { "searchAvailable()" }
                        viewModel.searchAvailable.value = it
                        viewModel.searchQuery.value = SearchState(query = "", typing = false)
                        viewModel.showSearch.value = false
                    },
                    actions = { viewModel.actions.value = it },
                    title = { viewModel.title.value = it },
                    canGoBack = { viewModel.canGoBack.value = it },
                    goBack = goBack,
                    showSnackbar = { scope.launch { snackbarHostState.showSnackbar(message = it) } },
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
            }
        }
    }
}

enum class WatchHomeNavTab(
    val title: StringResource,
    val icon: ImageVector,
    val route: NavBarRoute,
    val badge: (@Composable () -> Int)? = null,
) {
    WatchFaces(Res.string.faces, Icons.Filled.BrowseGallery, PebbleNavBarRoutes.WatchfacesRoute),
    WatchApps(Res.string.apps, Icons.Filled.AutoAwesomeMotion, PebbleNavBarRoutes.WatchappsRoute),
    Watches(Res.string.devices, Icons.Outlined.Watch, PebbleNavBarRoutes.WatchesRoute),
    Notifications(
        Res.string.notifications,
        Icons.Outlined.Notifications,
        PebbleNavBarRoutes.NotificationsRoute
    ),
    Index(
        Res.string.index,
        Icons.AutoMirrored.Outlined.Notes,
        PebbleNavBarRoutes.IndexRoute
    ),
    Settings(
        Res.string.settings,
        Icons.Outlined.Tune,
        PebbleNavBarRoutes.WatchSettingsRoute,
        { settingsBadgeTotal() });

    companion object {
        fun navBarEntries(indexEnabled: Boolean): List<WatchHomeNavTab> {
            return if (indexEnabled) {
                entries.filter { it != Notifications }
            } else {
                entries.filter { it != Index }
            }
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