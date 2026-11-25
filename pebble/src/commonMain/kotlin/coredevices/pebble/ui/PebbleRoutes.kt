package coredevices.pebble.ui

import CommonRoutes
import CoreNav
import CoreRoute
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.rebble.libpebblecommon.locker.AppType
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * All routes which sit between the top bar/nav bar.
 */
interface NavBarRoute

object PebbleRoutes {
    @Serializable
    data object WatchHomeRoute : CoreRoute

    @Serializable
    data class FirmwareSideloadRoute(val identifier: String) : CoreRoute

    @Serializable
    data class ContactDeveloperRoute(
        val appId: String,
        val appTitle: String,
    ) : CoreRoute
}

fun CommonRoutes.LockerAppRoute.toNavBarRoute(): PebbleNavBarRoutes.LockerAppRoute =
    PebbleNavBarRoutes.LockerAppRoute(uuid = uuid, storedId = null, storeSource = null)

@Stable
interface NavBarNav {
    fun navigateTo(route: CoreRoute)
    fun navigateTo(route: NavBarRoute)
    fun goBack()
}

object NoOpNavBarNav : NavBarNav {
    override fun navigateTo(route: CoreRoute) {}
    override fun navigateTo(route: NavBarRoute) {}
    override fun goBack() {}
}

object PebbleNavBarRoutes {
    @Serializable
    data object WatchesRoute : NavBarRoute

    @Serializable
    data object WatchfacesRoute : NavBarRoute

    @Serializable
    data class LockerAppRoute(
        val uuid: String?,
        val storedId: String?,
        val storeSource: Int?,
    ) : NavBarRoute

    @Serializable
    data object NotificationsRoute : NavBarRoute

    @Serializable
    data object IndexRoute : NavBarRoute

    @Serializable
    data object HealthRoute : NavBarRoute

    @Serializable
    data class AppstoreSettingsRoute(
        val addSourceName: String? = null,
        val addSourceUrl: String? = null,
    ) : NavBarRoute

    @Serializable
    data class NotificationAppRoute(val packageName: String) : NavBarRoute

    @Serializable
    data object WatchSettingsRoute : NavBarRoute

    @Serializable
    data object BatterySettingsRoute : NavBarRoute

    @Serializable
    data object PermissionsRoute : NavBarRoute

    @Serializable
    data object CalendarsRoute : NavBarRoute

    @Serializable
    data object WeatherRoute : NavBarRoute

    @Serializable
    data object ComplicationsRoute : NavBarRoute

    @Serializable
    data object PreferredSourcesRoute : NavBarRoute

    @Serializable
    data object PermissionUsageRoute : NavBarRoute

    @Serializable
    data object AppDataUsageRoute : NavBarRoute

    @Serializable
    data object AppActivityRoute : NavBarRoute

    @Serializable
    data class ActionLogRoute(val callerUuid: String?, val pluginUuid: String?) : NavBarRoute

    @Serializable
    data class PermissionHoldersRoute(val permission: String) : NavBarRoute

    @Serializable
    data object CannedRepliesRoute : NavBarRoute

    @Serializable
    data object NavBarTabsRoute : NavBarRoute

    @Serializable
    data class AppNotificationViewerRoute(val packageName: String, val channelId: String?) :
        NavBarRoute

    @Serializable
    data class ContactNotificationViewerRoute(val contactId: String) : NavBarRoute

    @Serializable
    data class AppStoreCollectionRoute(val sourceId: Int, val path: String, val title: String, val appType: String? = null) : NavBarRoute

    @Serializable
    data class MyCollectionRoute(val appType: String) : NavBarRoute

    @Serializable
    data class TopChartsRoute(val sourceId: Int, val appType: String) : NavBarRoute

    @Serializable
    data object OfflineModelsRoute : NavBarRoute

    @Serializable
    data class WatchSettingsCategoryRoute(val section: String, val topLevelType: String) : NavBarRoute

    /** [uuid] reads the installed pbw; [pbwUrl] fetches a store app's. */
    @Serializable
    data class NativePluginRoute(val uuid: String) : NavBarRoute

    @Serializable
    data class PluginSourceRoute(val uuid: String?, val pbwUrl: String?, val script: String, val title: String) : NavBarRoute
}

inline fun <reified T : Any> NavGraphBuilder.composableWithAnimations(
    viewModel: WatchHomeViewModel,
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T>(
        enterTransition = {
            if (viewModel.disableNextTransitionAnimation.value) {
                EnterTransition.None
            } else {
                null
            }
        },
        exitTransition = {
            if (viewModel.disableNextTransitionAnimation.value) {
                ExitTransition.None
            } else {
                null
            }
        },
        popEnterTransition = {
            if (viewModel.disableNextTransitionAnimation.value) {
                EnterTransition.None
            } else {
                null
            }
        },
        popExitTransition = {
            if (viewModel.disableNextTransitionAnimation.value) {
                ExitTransition.None
            } else {
                null
            }
        },
    ) {
        content(it)
    }
}

fun NavGraphBuilder.addNavBarRoutes(
    nav: NavBarNav,
    screen: @Composable (NavBackStackEntry, @Composable (TopBarParams) -> Unit) -> Unit,
    indexScreen: @Composable (TopBarParams, NavBarNav, CoreNav) -> Unit,
    /** A CoreNav scoped to the inner controller so RingRoute navigations
     *  stay inside the bottom-nav chrome. Forwarded to [indexScreen]. */
    scopedCoreNav: CoreNav,
    viewModel: WatchHomeViewModel,
) {
    composableWithAnimations<PebbleNavBarRoutes.WatchesRoute>(viewModel) {
        screen(it) { topBarParams ->
            WatchesScreen(nav, topBarParams)
        }
    }
    composableWithAnimations<PebbleNavBarRoutes.WatchfacesRoute>(viewModel) {
        screen(it) { topBarParams ->
            LockerScreen(
                nav,
                topBarParams,
            )
        }
    }
    composableWithAnimations<PebbleNavBarRoutes.LockerAppRoute>(viewModel) {
        screen(it) { topBarParams ->
            val route: PebbleNavBarRoutes.LockerAppRoute = it.toRoute()
            LockerAppScreen(
                topBarParams,
                route.uuid?.let { Uuid.parse(it) },
                nav,
                route.storedId,
                route.storeSource,
            )
        }
    }
    composableWithAnimations<PebbleNavBarRoutes.NotificationsRoute>(viewModel) {
        screen(it) { topBarParams ->
            NotificationsScreen(topBarParams, nav)
        }
    }
    composableWithAnimations<PebbleNavBarRoutes.HealthRoute>(viewModel) {
        screen(it) { topBarParams ->
            HealthScreen(topBarParams, nav)
        }
    }
    composableWithAnimations<PebbleNavBarRoutes.IndexRoute>(viewModel) {
        screen(it) { topBarParams ->
            indexScreen(topBarParams, nav, scopedCoreNav)
        }
    }
    composableWithAnimations<PebbleNavBarRoutes.NotificationAppRoute>(viewModel) {
        screen(it) { topBarParams ->
            val route: PebbleNavBarRoutes.NotificationAppRoute = it.toRoute()
            NotificationAppScreen(topBarParams, route.packageName, nav)
        }
    }
    composableWithAnimations<PebbleNavBarRoutes.WatchSettingsRoute>(viewModel) {
        screen(it) { topBarParams ->
            WatchSettingsScreen(nav, topBarParams)
        }
    }
    composableWithAnimations<PebbleNavBarRoutes.BatterySettingsRoute>(viewModel) {
        screen(it) { topBarParams ->
            BatterySettingsScreen(nav, topBarParams)
        }
    }
    composableWithAnimations<PebbleNavBarRoutes.PermissionsRoute>(viewModel) {
        screen(it) { topBarParams ->
            PermissionsScreen(nav, topBarParams)
        }
    }
    composableWithAnimations<PebbleNavBarRoutes.AppNotificationViewerRoute>(viewModel) {
        screen(it) { topBarParams ->
            val route: PebbleNavBarRoutes.AppNotificationViewerRoute = it.toRoute()
            AppNotificationViewerScreen(
                topBarParams = topBarParams,
                nav = nav,
                packageName = route.packageName,
                channelId = route.channelId,
            )
        }
    }
    composableWithAnimations<PebbleNavBarRoutes.ContactNotificationViewerRoute>(viewModel) {
        screen(it) { topBarParams ->
            val route: PebbleNavBarRoutes.ContactNotificationViewerRoute = it.toRoute()
            ContactNotificationViewerScreen(
                topBarParams = topBarParams,
                nav = nav,
                contactId = route.contactId,
            )
        }
    }
    composableWithAnimations<PebbleNavBarRoutes.AppStoreCollectionRoute>(viewModel) {
        screen(it) { topBarParams ->
            val route: PebbleNavBarRoutes.AppStoreCollectionRoute = it.toRoute()
            AppStoreCollectionScreen(
                navBarNav = nav,
                topBarParams = topBarParams,
                sourceId = route.sourceId,
                path = route.path,
                title = route.title,
                appType = route.appType?.let { AppType.fromString(it) },
            )
        }
    }
    composableWithAnimations<PebbleNavBarRoutes.TopChartsRoute>(viewModel) {
        screen(it) { topBarParams ->
            val route: PebbleNavBarRoutes.TopChartsRoute = it.toRoute()
            TopChartsScreen(
                navBarNav = nav,
                topBarParams = topBarParams,
                sourceId = route.sourceId,
                type = AppType.fromString(route.appType) ?: AppType.Watchface,
            )
        }
    }
    composableWithAnimations<PebbleNavBarRoutes.MyCollectionRoute>(viewModel) {
        screen(it) { topBarParams ->
            val route: PebbleNavBarRoutes.MyCollectionRoute = it.toRoute()
            MyCollectionScreen(
                navBarNav = nav,
                topBarParams = topBarParams,
                appType = AppType.fromString(route.appType)!!,
            )
        }
    }
    composable<PebbleNavBarRoutes.CalendarsRoute> {
        screen(it) { topBarParams ->
            CalendarScreen(nav, topBarParams)
        }
    }
    composable<PebbleNavBarRoutes.WeatherRoute> {
        screen(it) { topBarParams ->
            WeatherScreen(nav, topBarParams)
        }
    }
    composable<PebbleNavBarRoutes.ComplicationsRoute> {
        screen(it) { topBarParams ->
            ComplicationsScreen(nav, topBarParams)
        }
    }
    composable<PebbleNavBarRoutes.PreferredSourcesRoute> {
        screen(it) { topBarParams ->
            PreferredSourcesScreen(nav, topBarParams)
        }
    }
    composable<PebbleNavBarRoutes.PermissionUsageRoute> {
        screen(it) { topBarParams ->
            PermissionUsageScreen(nav, topBarParams)
        }
    }
    composable<PebbleNavBarRoutes.AppDataUsageRoute> {
        screen(it) { topBarParams ->
            AppDataUsageScreen(nav, topBarParams)
        }
    }
    composable<PebbleNavBarRoutes.AppActivityRoute> {
        screen(it) { topBarParams ->
            AppActivityScreen(nav, topBarParams)
        }
    }
    composable<PebbleNavBarRoutes.ActionLogRoute> {
        screen(it) { topBarParams ->
            val route: PebbleNavBarRoutes.ActionLogRoute = it.toRoute()
            ActionLogScreen(nav, topBarParams, route.callerUuid?.let(Uuid::parse), route.pluginUuid?.let(Uuid::parse))
        }
    }
    composable<PebbleNavBarRoutes.PermissionHoldersRoute> {
        screen(it) { topBarParams ->
            val route: PebbleNavBarRoutes.PermissionHoldersRoute = it.toRoute()
            PermissionHoldersScreen(nav, topBarParams, route.permission)
        }
    }
    composable<PebbleNavBarRoutes.CannedRepliesRoute> {
        screen(it) { topBarParams ->
            CannedRepliesScreen(nav, topBarParams)
        }
    }
    composable<PebbleNavBarRoutes.NavBarTabsRoute> {
        screen(it) { topBarParams ->
            NavBarTabsScreen(topBarParams)
        }
    }
    composable<PebbleNavBarRoutes.AppstoreSettingsRoute> {
        screen(it) { topBarParams ->
            val route: PebbleNavBarRoutes.AppstoreSettingsRoute = it.toRoute()
            AppstoreSettingsScreen(nav, topBarParams, route.addSourceName, route.addSourceUrl)
        }
    }
    composable<PebbleNavBarRoutes.OfflineModelsRoute> {
        screen(it) { topBarParams ->
            ModelManagementScreen(nav, topBarParams)
        }
    }
    composableWithAnimations<PebbleNavBarRoutes.WatchSettingsCategoryRoute>(viewModel) {
        screen(it) { topBarParams ->
            val route: PebbleNavBarRoutes.WatchSettingsCategoryRoute = it.toRoute()
            WatchSettingsCategoryScreen(
                nav,
                topBarParams,
                Section.valueOf(route.section),
                TopLevelType.valueOf(route.topLevelType),
            )
        }
    }
    composableWithAnimations<PebbleNavBarRoutes.NativePluginRoute>(viewModel) {
        screen(it) { topBarParams ->
            val route: PebbleNavBarRoutes.NativePluginRoute = it.toRoute()
            NativePluginScreen(nav, topBarParams, Uuid.parse(route.uuid))
        }
    }
    composableWithAnimations<PebbleNavBarRoutes.PluginSourceRoute>(viewModel) {
        screen(it) { topBarParams ->
            val route: PebbleNavBarRoutes.PluginSourceRoute = it.toRoute()
            PluginSourceScreen(
                topBarParams = topBarParams,
                uuid = route.uuid?.let { id -> Uuid.parse(id) },
                pbwUrl = route.pbwUrl,
                script = route.script,
                title = route.title,
            )
        }
    }
}

fun NavGraphBuilder.addPebbleRoutes(
    coreNav: CoreNav,
    indexScreen: @Composable (TopBarParams, NavBarNav, CoreNav) -> Unit,
    /** Adds the Index/Ring detail routes (RecordingDetails, ObjectDetails,
     *  FullFeed, etc.) to the inner NavHost so they keep the bottom
     *  NavigationBar visible. The receiver is the inner NavGraphBuilder;
     *  the [CoreNav] parameter is a wrapper that routes those routes
     *  through the inner controller. Default empty for callers that don't
     *  need experimental devices. */
    addExperimentalRoutes: NavGraphBuilder.(CoreNav) -> Unit = {},
    /** Tells [WatchHomeScreen] which [CoreRoute]s should be navigated via
     *  the inner controller (keeping the bottom nav) vs. the outer one
     *  (popping out of the chrome). Default: nothing inner-scoped. */
    isInnerScopedRoute: (CoreRoute) -> Boolean = { false },
) {
    composable<PebbleRoutes.WatchHomeRoute> {
        WatchHomeScreen(coreNav, indexScreen, addExperimentalRoutes, isInnerScopedRoute)
    }
    composable<PebbleRoutes.FirmwareSideloadRoute> {
        val route: PebbleRoutes.FirmwareSideloadRoute = it.toRoute()
        DebugFirmwareSideload(route.identifier, coreNav)
    }
    composable<CommonRoutes.WatchappSettingsRoute> {
        val route: CommonRoutes.WatchappSettingsRoute = it.toRoute()
        WatchappSettingsScreen(
            coreNav = coreNav,
            uuid = route.uuid,
            title = route.title,
        )
    }
    composable<PebbleRoutes.ContactDeveloperRoute> {
        val route: PebbleRoutes.ContactDeveloperRoute = it.toRoute()
        ContactDeveloperScreen(
            coreNav = coreNav,
            appId = route.appId,
            appTitle = route.appTitle,
        )
    }
}
