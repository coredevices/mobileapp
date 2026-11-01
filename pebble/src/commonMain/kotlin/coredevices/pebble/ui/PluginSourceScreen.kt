package coredevices.pebble.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coredevices.pebble.services.PluginSourceLoader
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun PluginSourceScreen(topBarParams: TopBarParams, uuid: Uuid?, pbwUrl: String?, script: String, title: String) {
    LaunchedEffect(title) {
        topBarParams.searchAvailable(null)
        topBarParams.actions {}
        topBarParams.title(title)
    }
    val loader: PluginSourceLoader = koinInject()
    val loaded by produceState<Loaded?>(null, uuid, pbwUrl, script) {
        value = Loaded(loader.load(uuid, pbwUrl, script))
    }
    val text = loaded?.text
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            loaded == null -> CircularProgressIndicator()
            text == null -> Text("Couldn't load $script")
            else -> SelectionContainer {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            }
        }
    }
}

private class Loaded(val text: String?)
