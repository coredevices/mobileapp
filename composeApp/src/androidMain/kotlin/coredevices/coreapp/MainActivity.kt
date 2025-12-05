package coredevices.coreapp

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.Logger
import com.eygraber.uri.toKmpUriOrNull
import coredevices.coreapp.ui.App
import coredevices.coreapp.ui.navigation.CoreDeepLinkHandler
import coredevices.pebble.PebbleAndroidDelegate
import coredevices.pebble.PebbleAppDelegate
import coredevices.pebble.PebbleDeepLinkHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import theme.CoreAppTheme
import theme.ThemeProvider


class MainActivity : ComponentActivity() {
    private val pebbleDeepLinkHandler: PebbleDeepLinkHandler by inject()
    private val pebbleDelegate: PebbleAndroidDelegate by inject()
    private val themeProvider: ThemeProvider by inject()
    private val pebbleAppDelegate: PebbleAppDelegate by inject()
    private val coreDeepLinkHandler: CoreDeepLinkHandler by inject()

    companion object {
        private val logger = Logger.withTag(MainActivity::class.simpleName!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(themeProvider.theme.value)
        super.onCreate(savedInstanceState)

        lifecycleScope.launch(Dispatchers.Main) {
            pebbleDelegate.initPostPermissions()
        }

        setContent {
            App()
        }

        lifecycleScope.launch {
            themeProvider.theme.collect {
                setTheme(it)
            }
        }
        // Handle initial intent that launches the app
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data?.toKmpUriOrNull()
        logger.d { "handleIntent uri = $uri" }
        uri?.let {
            pebbleDeepLinkHandler.handle(it)
            coreDeepLinkHandler.handle(it)
        }
        setIntent(null)
    }

    override fun onResume() {
        super.onResume()
        pebbleAppDelegate.onAppResumed()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        logger.d { "onConfigurationChanged" }
        super.onConfigurationChanged(newConfig)
        setTheme(themeProvider.theme.value)
    }

    private fun setTheme(theme: CoreAppTheme) {
        val dark = android.R.style.Theme_Material_NoActionBar
        val light = android.R.style.Theme_Material_Light_NoActionBar
        val themeRes = when (theme) {
            CoreAppTheme.Light -> light
            CoreAppTheme.Dark -> dark
            CoreAppTheme.System -> if (isNightMode(this)) {
                dark
            } else {
                light
            }
        }
        setTheme(themeRes)
    }
}

fun isNightMode(context: Context): Boolean {
    val nightModeFlags =
        context.getResources().getConfiguration().uiMode and Configuration.UI_MODE_NIGHT_MASK
    return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}