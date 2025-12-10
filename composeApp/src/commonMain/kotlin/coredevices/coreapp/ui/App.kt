package coredevices.coreapp.ui

import CommonRoutes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.coreapp.ui.navigation.AppNavHost
import coredevices.coreapp.ui.screens.SHOWN_ONBOARDING
import coredevices.pebble.ui.PebbleRoutes
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import theme.AppTheme

@Composable
@Preview
fun App() {
    val navHostController = rememberNavController()
    DisposableEffect(navHostController) {
        val listener = NavController.OnDestinationChangedListener { controller, destination, arguments ->
            val route = destination.route
            Logger.d("Nav: Destination Changed to route='$route'")
        }
        navHostController.addOnDestinationChangedListener(listener)
        onDispose {
            navHostController.removeOnDestinationChangedListener(listener)
        }
    }
    AppTheme {
        val settings: Settings = koinInject()
        val startDestination = if (settings.getBoolean(SHOWN_ONBOARDING, false)) {
            PebbleRoutes.WatchHomeRoute
        } else {
            CommonRoutes.OnboardingRoute
        }
        AppNavHost(navHostController, startDestination)
    }
}