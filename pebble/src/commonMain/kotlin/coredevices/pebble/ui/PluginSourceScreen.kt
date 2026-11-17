package coredevices.pebble.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import coredevices.pebble.services.PluginSourceLoader
import coredevices.util.rememberUiContext
import coredevices.util.writeToDownloads
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import org.koin.compose.koinInject
import theme.currentColorScheme
import kotlin.uuid.Uuid

@Composable
fun PluginSourceScreen(topBarParams: TopBarParams, uuid: Uuid?, pbwUrl: String?, script: String, title: String) {
    val loader: PluginSourceLoader = koinInject()
    val loaded by produceState<Loaded?>(null, uuid, pbwUrl, script) {
        value = Loaded(loader.load(uuid, pbwUrl, script))
    }
    var wrap by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val uiContext = rememberUiContext()
    val scope = rememberCoroutineScope()
    LaunchedEffect(title) {
        topBarParams.searchAvailable(null)
        topBarParams.actions {
            TopBarIconButtonWithToolTip(
                onClick = { wrap = !wrap },
                icon = Icons.AutoMirrored.Filled.WrapText,
                description = if (wrap) "Don't wrap lines" else "Wrap lines",
            )
            val source = loaded?.text
            TopBarIconButtonWithToolTip(
                onClick = {
                    if (uiContext == null || source == null || saving) return@TopBarIconButtonWithToolTip
                    saving = true
                    scope.launch {
                        val path = Path(SystemTemporaryDirectory, Path(script).name)
                        try {
                            withContext(Dispatchers.IO) {
                                SystemFileSystem.sink(path).buffered().use { it.writeString(source) }
                            }
                            writeToDownloads(uiContext, path, "application/javascript")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Logger.withTag("PluginSourceScreen").w(e) { "Failed to save $script" }
                            topBarParams.showSnackbar("Couldn't save ${path.name}")
                        } finally {
                            runCatching { SystemFileSystem.delete(path, mustExist = false) }
                            saving = false
                        }
                    }
                },
                icon = Icons.Filled.Download,
                description = "Download",
                enabled = uiContext != null && source != null && !saving,
            )
        }
        topBarParams.title(title)
    }
    val text = loaded?.text
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            loaded == null -> CircularProgressIndicator()
            text == null -> Text("Couldn't load $script")
            else -> SelectionContainer {
                val dark = currentColorScheme().isDark
                val highlighted by produceState(AnnotatedString(text), text, dark) {
                    value = AnnotatedString(text)
                    if (text.length > MAX_HIGHLIGHT_LENGTH) return@produceState
                    value = withContext(Dispatchers.Default) { highlightJs(text, dark) }
                }
                Text(
                    highlighted,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                        .padding(12.dp),
                )
            }
        }
    }
}

private class Loaded(val text: String?)

private const val MAX_HIGHLIGHT_LENGTH = 200_000

internal fun highlightJs(source: String, dark: Boolean): AnnotatedString = buildAnnotatedString {
    append(source)
    val highlights = Highlights.Builder(code = source, language = SyntaxLanguage.JAVASCRIPT, theme = SyntaxThemes.atom(dark))
        .build()
        .getHighlights()
    for (highlight in highlights) {
        val (start, end) = highlight.location
        if (highlight !is ColorHighlight || start < 0 || end <= start || end > source.length) continue
        addStyle(SpanStyle(color = Color(0xFF000000 or highlight.rgb.toLong())), start, end)
    }
}
