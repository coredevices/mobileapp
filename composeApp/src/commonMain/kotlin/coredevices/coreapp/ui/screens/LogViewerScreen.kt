package coredevices.coreapp.ui.screens

import CoreNav
import PlatformShareLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import coredevices.coreapp.util.FileLogWriter
import coredevices.coreapp.util.getLogsCacheDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import org.koin.compose.koinInject

private val logger = Logger.withTag("LogViewerScreen")

@Composable
fun LogViewerScreen(
    coreNav: CoreNav,
) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val scope = rememberCoroutineScope()
        val fileLogWriter: FileLogWriter = koinInject()
        val platformShareLauncher: PlatformShareLauncher = koinInject()
        var logLines by remember { mutableStateOf<List<String>?>(null) }
        var loading by remember { mutableStateOf(true) }
        var autoRefresh by remember { mutableStateOf(false) }
        var pebblekitFilter by remember { mutableStateOf(false) }
        var lineWrap by remember { mutableStateOf(false) }
        var hideTimestamps by remember { mutableStateOf(false) }
        var menuExpanded by remember { mutableStateOf(false) }
        val filteredLogLines by remember {
            derivedStateOf {
                if (pebblekitFilter) {
                    logLines?.filter { line ->
                        "PKJS" in line || "PebbleKit" in line
                    }
                } else {
                    logLines
                }
            }
        }
        val logLineRegex = remember { Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z?) (\[[A-Z]] .+?): (.*)$""") }
        val timestampColor = MaterialTheme.colorScheme.outline
        val severityColors = mapOf(
            "[D]" to MaterialTheme.colorScheme.outline, // subdued
            "[I]" to Color(0xFF42A5F5), // blue
            "[W]" to Color(0xFFFFD54F), // yellow
            "[E]" to MaterialTheme.colorScheme.error,
            "[A]" to MaterialTheme.colorScheme.error,
            "[V]" to MaterialTheme.colorScheme.outline,
        )
        val defaultSeverityColor = MaterialTheme.colorScheme.onSurface
        val listState = rememberLazyListState()
        val horizontalScrollState = rememberScrollState()
        val isAtBottom by remember {
            derivedStateOf {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = listState.layoutInfo.totalItemsCount
                totalItems == 0 || lastVisible >= totalItems - 1
            }
        }

        fun loadFullLogs() {
            scope.launch {
                loading = true
                try {
                    val dumpPath = fileLogWriter.dumpLogs()
                    val content = withContext(Dispatchers.Default) {
                        SystemFileSystem.source(dumpPath).buffered().use { source ->
                            source.readString()
                        }
                    }
                    logLines = content.lines()
                } catch (e: Exception) {
                    logger.e(e) { "Failed to read logs" }
                    logLines = listOf("Error reading logs: ${e.message}")
                }
                loading = false
            }
        }

        fun refreshLatestLog() {
            scope.launch {
                try {
                    val latestPath = Path(getLogsCacheDir() + "/latest.log")
                    val content = withContext(Dispatchers.Default) {
                        SystemFileSystem.source(latestPath).buffered().use { source ->
                            source.readString()
                        }
                    }
                    logLines = content.lines()
                } catch (e: Exception) {
                    logger.e(e) { "Failed to refresh logs" }
                }
                loading = false
            }
        }

        LaunchedEffect(Unit) {
            loadFullLogs()
        }

        // Scroll to bottom when logs change or line wrap is toggled
        LaunchedEffect(filteredLogLines, lineWrap) {
            filteredLogLines?.let {
                if (it.isNotEmpty()) {
                    listState.scrollToItem(it.lastIndex)
                }
            }
        }

        // Auto-refresh every second when enabled and scrolled to bottom
        LaunchedEffect(autoRefresh, isAtBottom) {
            if (autoRefresh && isAtBottom) {
                while (true) {
                    delay(1000)
                    refreshLatestLog()
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Log Viewer") },
                    navigationIcon = {
                        IconButton(onClick = coreNav::goBack) {
                            Icon(
                                Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    val dumpPath = fileLogWriter.dumpLogs()
                                    val now = Clock.System.now()
                                        .toLocalDateTime(TimeZone.currentSystemDefault())
                                    val timestamp = now.toString()
                                        .replace(':', '-')
                                        .replace('T', '-')
                                        .substringBefore('.')
                                    val prefix = if (pebblekitFilter) "pebblekit-logs" else "pebble-app-logs"
                                    val filename = "$prefix-$timestamp.log"
                                    val sharePath = Path(getLogsCacheDir() + "/$filename")
                                    SystemFileSystem.delete(sharePath, mustExist = false)
                                    if (pebblekitFilter && filteredLogLines != null) {
                                        SystemFileSystem.sink(sharePath).buffered().use { sink ->
                                            sink.writeString(filteredLogLines!!.joinToString("\n"))
                                        }
                                    } else {
                                        SystemFileSystem.source(dumpPath).buffered().use { source ->
                                            SystemFileSystem.sink(sharePath).buffered().use { sink ->
                                                sink.transferFrom(source)
                                            }
                                        }
                                    }
                                    platformShareLauncher.share(null, sharePath, "text/plain")
                                } catch (e: Exception) {
                                    logger.e(e) { "Failed to save logs" }
                                }
                            }
                        }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share logs"
                            )
                        }
                        IconButton(
                            onClick = { loadFullLogs() },
                            enabled = !autoRefresh,
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                        IconButton(onClick = {
                            autoRefresh = !autoRefresh
                            if (!autoRefresh) {
                                loadFullLogs()
                            }
                        }) {
                            Icon(
                                if (autoRefresh) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (autoRefresh) "Disable auto-refresh" else "Enable auto-refresh"
                            )
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "More options"
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Line wrap") },
                                    onClick = { lineWrap = !lineWrap },
                                    trailingIcon = { Checkbox(checked = lineWrap, onCheckedChange = null) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Hide timestamps") },
                                    onClick = { hideTimestamps = !hideTimestamps },
                                    trailingIcon = { Checkbox(checked = hideTimestamps, onCheckedChange = null) },
                                )
                                DropdownMenuItem(
                                    text = { Text("PebbleKit/PKJS only") },
                                    onClick = { pebblekitFilter = !pebblekitFilter },
                                    trailingIcon = { Checkbox(checked = pebblekitFilter, onCheckedChange = null) },
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (!isAtBottom) SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            filteredLogLines?.let {
                                if (it.isNotEmpty()) {
                                    listState.animateScrollToItem(it.lastIndex)
                                }
                            }
                        }
                    },
                ) {
                    Icon(
                        Icons.Default.VerticalAlignBottom,
                        contentDescription = "Scroll to bottom"
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (loading && filteredLogLines == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (!lineWrap) Modifier.horizontalScroll(horizontalScrollState) else Modifier)
                            .padding(horizontal = 8.dp)
                    ) {
                        items(filteredLogLines ?: emptyList()) { line ->
                            val match = logLineRegex.matchEntire(line)
                            Text(
                                text = if (match != null) {
                                    val (timestamp, severityAndLogger, message) = match.destructured
                                    val severityKey = severityAndLogger.substring(0, 3)
                                    buildAnnotatedString {
                                        if (!hideTimestamps) {
                                            withStyle(SpanStyle(color = timestampColor)) {
                                                append(timestamp)
                                            }
                                            append(" ")
                                        }
                                        withStyle(SpanStyle(color = severityColors[severityKey] ?: defaultSeverityColor)) {
                                            append(severityAndLogger)
                                        }
                                        append(": ")
                                        append(message)
                                    }
                                } else {
                                    buildAnnotatedString { append(line) }
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                maxLines = if (lineWrap) Int.MAX_VALUE else 1,
                                softWrap = lineWrap,
                            )
                        }
                    }
                }
            }
        }
    }
}
