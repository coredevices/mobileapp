package coredevices.pebble.ui

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
import coredevices.database.AppstoreSource
import io.rebble.libpebblecommon.locker.AppType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
    data object CalendarsRoute : CoreRoute

    @Serializable
    data class WatchappSettingsRoute(
        val watchIdentifier: String,
        val title: String,
        val url: String
    ) : CoreRoute

    @Serializable
    data object AppstoreSettingsRoute : CoreRoute
}

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
    data class WatchRoute(
        val identifier: String,
    ) : NavBarRoute

    @Serializable
    data object WatchfacesRoute : NavBarRoute

    @Serializable
    data object WatchappsRoute : NavBarRoute

    @Serializable
    data class LockerAppRoute(
        val uuid: String,
        val storedId: String?,
        val storeSource: String?,
        val storeSources: String? = null,
    ) : NavBarRoute

    @Serializable
    data object NotificationsRoute : NavBarRoute

    @Serializable
    data class NotificationAppRoute(val packageName: String) : NavBarRoute

    @Serializable
    data object WatchSettingsRoute : NavBarRoute

    @Serializable
    data object PermissionsRoute : NavBarRoute

    @Serializable
    data class AppStoreRoute(val appType: String) : NavBarRoute

    @Serializable
    data class AppNotificationViewerRoute(val packageName: String, val channelId: String?) :
        NavBarRoute

    @Serializable
    data class ContactNotificationViewerRoute(val contactId: String) : NavBarRoute

    @Serializable
    data class AppStoreCollectionRoute(val sourceId: Int, val path: String, val title: String, val appType: AppType? = null) : NavBarRoute
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
    topBarParams: TopBarParams,
    experimentalRoute: CoreRoute?,
    viewModel: WatchHomeViewModel,
) {
    composableWithAnimations<PebbleNavBarRoutes.AppStoreRoute>(viewModel) {
        val route: PebbleNavBarRoutes.AppStoreRoute = it.toRoute()
        AppStoreScreen(nav, AppType.fromString(route.appType)!!, topBarParams)
    }
    composableWithAnimations<PebbleNavBarRoutes.WatchesRoute>(viewModel) {
        WatchesScreen(nav, topBarParams)
    }
    composableWithAnimations<PebbleNavBarRoutes.WatchRoute>(viewModel) {
        val route: PebbleNavBarRoutes.WatchRoute = it.toRoute()
        WatchScreen(nav, topBarParams, route.identifier)
    }
    composableWithAnimations<PebbleNavBarRoutes.WatchappsRoute>(viewModel) {
        LockerScreen(
            nav,
            topBarParams,
            LockerTab.Apps
        )
    }
    composableWithAnimations<PebbleNavBarRoutes.WatchfacesRoute>(viewModel) {
        LockerScreen(
            nav,
            topBarParams,
            LockerTab.Watchfaces
        )
    }
    composableWithAnimations<PebbleNavBarRoutes.LockerAppRoute>(viewModel) {
        val route: PebbleNavBarRoutes.LockerAppRoute = it.toRoute()
        LockerAppScreen(
            topBarParams,
            Uuid.parse(route.uuid),
            nav,
            route.storedId,
            route.storeSource?.let { Json.decodeFromString<AppstoreSource>(route.storeSource) },
            route.storeSources?.let {
                Json.decodeFromString<List<Pair<String, AppstoreSource>>>(route.storeSources)
            },
        )
    }
    composableWithAnimations<PebbleNavBarRoutes.NotificationsRoute>(viewModel) {
        NotificationsScreen(topBarParams, nav)
    }
    composableWithAnimations<PebbleNavBarRoutes.NotificationAppRoute>(viewModel) {
        val route: PebbleNavBarRoutes.NotificationAppRoute = it.toRoute()
        NotificationAppScreen(topBarParams, route.packageName, nav)
    }
    composableWithAnimations<PebbleNavBarRoutes.WatchSettingsRoute>(viewModel) {
        WatchSettingsScreen(nav, topBarParams, experimentalRoute)
    }
    composableWithAnimations<PebbleNavBarRoutes.PermissionsRoute>(viewModel) {
        PermissionsScreen(nav, topBarParams)
    }
    composableWithAnimations<PebbleNavBarRoutes.AppNotificationViewerRoute>(viewModel) {
        val route: PebbleNavBarRoutes.AppNotificationViewerRoute = it.toRoute()
        AppNotificationViewerScreen(
            topBarParams = topBarParams,
            nav = nav,
            packageName = route.packageName,
            channelId = route.channelId,
        )
    }
    composableWithAnimations<PebbleNavBarRoutes.ContactNotificationViewerRoute>(viewModel) {
        val route: PebbleNavBarRoutes.ContactNotificationViewerRoute = it.toRoute()
        ContactNotificationViewerScreen(
            topBarParams = topBarParams,
            nav = nav,
            contactId = route.contactId,
        )
    }
    composableWithAnimations<PebbleNavBarRoutes.AppStoreCollectionRoute>(viewModel) {
        val route: PebbleNavBarRoutes.AppStoreCollectionRoute = it.toRoute()
        AppStoreCollectionScreen(
            navBarNav = nav,
            topBarParams = topBarParams,
            sourceId = route.sourceId,
            path = route.path,
            title = route.title,
            appType = route.appType,
        )
    }
}

fun NavGraphBuilder.addPebbleRoutes(coreNav: CoreNav, experimentalRoute: CoreRoute?) {
    composable<PebbleRoutes.WatchHomeRoute> {
        WatchHomeScreen(coreNav, experimentalRoute)
    }
    composable<PebbleRoutes.FirmwareSideloadRoute> {
        val route: PebbleRoutes.FirmwareSideloadRoute = it.toRoute()
        DebugFirmwareSideload(route.identifier, coreNav)
    }
    composable<PebbleRoutes.CalendarsRoute> {
        CalendarScreen(coreNav)
    }
    composable<PebbleRoutes.WatchappSettingsRoute> {
        val route: PebbleRoutes.WatchappSettingsRoute = it.toRoute()
        WatchappSettingsScreen(
            coreNav = coreNav,
            watchIdentifier = route.watchIdentifier,
            title = route.title,
            url = route.url,
        )
    }
    composable<PebbleRoutes.AppstoreSettingsRoute> {
        AppstoreSettingsScreen(coreNav)
    }
}