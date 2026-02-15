package coredevices.coreapp.ui.navigation

import BugReportButton
import CommonRoutes
import CoreNav
import CoreRoute
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.navigation.NavDeepLink
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.NavUri
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import co.touchlab.kermit.Logger
import coredevices.EnableExperimentalDevices
import coredevices.ExperimentalDevices
import coredevices.coreapp.ui.screens.BugReportScreen
import coredevices.coreapp.ui.screens.BugReportsListScreen
import coredevices.coreapp.ui.screens.OnboardingScreen
import coredevices.coreapp.ui.screens.ViewBugReportScreen
import coredevices.pebble.PebbleDeepLinkHandler
import coredevices.pebble.ui.PebbleRoutes
import coredevices.pebble.ui.addPebbleRoutes
import coredevices.ui.GenericWebViewScreen
import coredevices.util.CommonBuildKonfig
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val logger = Logger.withTag("AppNavHost")

@Composable
fun AppNavHost(navController: NavHostController, startDestination: Any) {
    val deepLinks: CoreDeepLinkHandler = koinInject()
    val pebbleDeepLinks: PebbleDeepLinkHandler = koinInject()
    val scope = rememberCoroutineScope()
    LaunchedEffect(navController) { // React to NavController changes
        // This ensures the graph is available before doing any deep link
        snapshotFlow { navController.graph }
            .filterNotNull() // Wait until the graph is not null
            .first()
            .let {
                // Now that we know the graph is set up, start collecting deep links
                scope.launch {
                    deepLinks.navigateToDeepLink.collect { route ->
                        logger.d { "navigateToDeepLink $route" }
                        try {
                            if (route is NavUri) {
                                navController.navigate(route)
                            } else {
                                navController.navigate(route)
                            }
                        } catch (e: IllegalArgumentException) {
                            logger.w(e) { "Failed to navigate to $route" }
                        }
                    }
                }
                scope.launch {
                    pebbleDeepLinks.navigateToPebbleDeepLink.collect { route ->
                        if (route == null) {
                            return@collect
                        }
                        val isOnWatchHome = navController.currentBackStackEntry?.destination?.hasRoute<PebbleRoutes.WatchHomeRoute>() == true
                        // Pebble deep links need us to navigate to WatchHomeScreen - but only if we
                        // aren't already there.
                        if (!isOnWatchHome) {
                            logger.d { "Navigating to WatchHomeRoute for pebble deep link" }
                            navController.navigate(PebbleRoutes.WatchHomeRoute)
                        }
                    }
                }
            }
    }
    val coreNav = remember {
        object : CoreNav {
            override fun navigateTo(route: CoreRoute) {
                if (route == PebbleRoutes.WatchHomeRoute) {
                    navController.popBackStack(CommonRoutes.OnboardingRoute, true)
                }
                navController.navigate(route)
            }

            override fun goBack() {
                navController.popBackStack()
            }

            override fun goBackToPebble() {
                navController.popBackStack(PebbleRoutes.WatchHomeRoute, inclusive = false)
            }
        }
    }
    val experimentalDevices: ExperimentalDevices = koinInject()
    NavHost(navController, startDestination = startDestination) {
        experimentalDevices.addExperimentalRoutes(this, coreNav)
        addPebbleRoutes(coreNav, indexScreen = { topBarParams, navBarNav ->
            experimentalDevices.IndexScreen(coreNav, topBarParams)
        })
        if (CommonBuildKonfig.QA) {
            composable<CommonRoutes.BugReport>(
                deepLinks = listOf(
                    NavDeepLink("pebblecore://deep-link/bug-report?pebble={pebble}")
                )
            ) {
                val route: CommonRoutes.BugReport = it.toRoute()
                BugReportScreen(
                    coreNav = coreNav,
                    pebble = route.pebble,
                    recordingPath = route.recordingPath,
                )
            }
            composable<CommonRoutes.AlphaTestInstructionsRoute> {
                GenericWebViewScreen(
                    coreNav = coreNav,
                    title = "Alpha Test Instructions",
                    url = "https://ndocs.repebble.com/alpha-tester-guide",
                )
            }
            composable<CommonRoutes.ViewMyBugReportsRoute> {
                BugReportsListScreen(
                    coreNav = coreNav,
                )
            }
            composable<CommonRoutes.ViewBugReportRoute>(
                deepLinks = listOf(
                    NavDeepLink("pebblecore://deep-link/view-bug-report?conversationId={conversationId}")
                )
            ) {
                val route: CommonRoutes.ViewBugReportRoute = it.toRoute()
                ViewBugReportScreen(
                    coreNav = coreNav,
                    conversationId = route.conversationId,
                )
            }
            composable<CommonRoutes.RoadmapChangelogRoute> {
                GenericWebViewScreen(
                    coreNav = coreNav,
                    title = "What's new in the app",
                    url = "https://ndocs.repebble.com/changelog",
                )
            }
            composable<CommonRoutes.PebbleOsChangelogRoute> {
                GenericWebViewScreen(
                    coreNav = coreNav,
                    title = "What’s new in PebbleOS",
                    url = "https://ndocs.repebble.com/pebbleos-changelog",
                )
            }
            composable<CommonRoutes.TroubleshootingRoute> {
                GenericWebViewScreen(
                    coreNav = coreNav,
                    title = "Getting Started & Troubleshooting",
                    url = "https://ndocs.repebble.com/getting-started",
                )
            }
            composable<CommonRoutes.OnboardingRoute> {
                OnboardingScreen(
                    coreNav = coreNav,
                )
            }
        }
    }
}

@Composable
fun experimentsEnabled(): Boolean {
    val enableExperimentalDevices: EnableExperimentalDevices = koinInject()
    val enableExperiments by enableExperimentalDevices.enabled.collectAsState()
    return enableExperiments
}