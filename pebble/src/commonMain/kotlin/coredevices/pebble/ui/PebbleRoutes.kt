package coredevices.pebble.ui

import CoreNav
import CoreRoute
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
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
    data class WatchappSettingsRoute(
        val watchIdentifier: String,
        val title: String,
        val url: String
    ) : CoreRoute
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
    data object AppstoreSettingsRoute : NavBarRoute

    @Serializable
    data class NotificationAppRoute(val packageName: String) : NavBarRoute

    @Serializable
    data object WatchSettingsRoute : NavBarRoute

    @Serializable
    data object PermissionsRoute : NavBarRoute

    @Serializable
    data object CalendarsRoute : NavBarRoute

    @Serializable
    data object WeatherRoute : NavBarRoute

    @Serializable
    data class AppStoreRoute(val appType: String?, val deepLinkId: String?) : NavBarRoute

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
    data class OfflineModelsRoute(val openSttDialog: Boolean = false) : NavBarRoute
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
    indexScreen: @Composable (TopBarParams, NavBarNav) -> Unit,
    viewModel: WatchHomeViewModel,
) {
    composableWithAnimations<PebbleNavBarRoutes.AppStoreRoute>(viewModel) {
        val route: PebbleNavBarRoutes.AppStoreRoute = it.toRoute()
        AppStoreScreen(nav, route.appType?.let { AppType.fromString(it) }, topBarParams, route.deepLinkId)
    }
    composableWithAnimations<PebbleNavBarRoutes.WatchesRoute>(viewModel) {
        WatchesScreen(nav, topBarParams)
    }
    composableWithAnimations<PebbleNavBarRoutes.WatchRoute>(viewModel) {
        val route: PebbleNavBarRoutes.WatchRoute = it.toRoute()
        WatchScreen(nav, topBarParams, route.identifier)
    }
    composableWithAnimations<PebbleNavBarRoutes.WatchfacesRoute>(viewModel) {
        LockerScreen(
            nav,
            topBarParams,
        )
    }
    composableWithAnimations<PebbleNavBarRoutes.LockerAppRoute>(viewModel) {
        val route: PebbleNavBarRoutes.LockerAppRoute = it.toRoute()
        LockerAppScreen(
            topBarParams,
            route.uuid?.let { Uuid.parse(it) },
            nav,
            route.storedId,
            route.storeSource,
        )
    }
    composableWithAnimations<PebbleNavBarRoutes.NotificationsRoute>(viewModel) {
        NotificationsScreen(topBarParams, nav)
    }
    composableWithAnimations<PebbleNavBarRoutes.IndexRoute>(viewModel) {
        indexScreen(topBarParams, nav)
    }
    composableWithAnimations<PebbleNavBarRoutes.NotificationAppRoute>(viewModel) {
        val route: PebbleNavBarRoutes.NotificationAppRoute = it.toRoute()
        NotificationAppScreen(topBarParams, route.packageName, nav)
    }
    composableWithAnimations<PebbleNavBarRoutes.WatchSettingsRoute>(viewModel) {
        WatchSettingsScreen(nav, topBarParams)
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
            appType = route.appType?.let { AppType.fromString(it) },
        )
    }
    composableWithAnimations<PebbleNavBarRoutes.MyCollectionRoute>(viewModel) {
        val route: PebbleNavBarRoutes.MyCollectionRoute = it.toRoute()
        MyCollectionScreen(
            navBarNav = nav,
            topBarParams = topBarParams,
            appType = AppType.fromString(route.appType)!!,
        )
    }
    composable<PebbleNavBarRoutes.CalendarsRoute> {
        CalendarScreen(nav, topBarParams)
    }
    composable<PebbleNavBarRoutes.WeatherRoute> {
        WeatherScreen(nav, topBarParams)
    }
    composable<PebbleNavBarRoutes.AppstoreSettingsRoute> {
        AppstoreSettingsScreen(nav, topBarParams)
    }
    composable<PebbleNavBarRoutes.OfflineModelsRoute> {
        val route: PebbleNavBarRoutes.OfflineModelsRoute = it.toRoute()
        ModelManagementScreen(nav, topBarParams, route.openSttDialog)
    }
}

fun NavGraphBuilder.addPebbleRoutes(coreNav: CoreNav, indexScreen: @Composable (TopBarParams, NavBarNav) -> Unit) {
    composable<PebbleRoutes.WatchHomeRoute> {
        WatchHomeScreen(coreNav, indexScreen)
    }
    composable<PebbleRoutes.FirmwareSideloadRoute> {
        val route: PebbleRoutes.FirmwareSideloadRoute = it.toRoute()
        DebugFirmwareSideload(route.identifier, coreNav)
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
}
