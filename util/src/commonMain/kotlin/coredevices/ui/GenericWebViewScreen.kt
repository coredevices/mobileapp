package coredevices.ui

import CoreNav
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.back
import org.jetbrains.compose.resources.stringResource

private val logger = Logger.withTag("GenericWebViewScreen")

@Composable
fun GenericWebViewScreen(
    coreNav: CoreNav,
    title: String,
    url: String,
) {
    val interceptor = remember {
        object : PebbleWebviewUrlInterceptor {
            override var navigator: PebbleWebviewNavigator? = null

            override fun onIntercept(url: String, navigator: PebbleWebviewNavigator): Boolean {
                // Allow all URLs to load
                return true
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = coreNav::goBack) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                },
            )
        }
    ) { paddingValues ->
        logger.v { "Loading WebView with URL: $url" }
        PebbleWebview(
            url = url,
            interceptor = interceptor,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}