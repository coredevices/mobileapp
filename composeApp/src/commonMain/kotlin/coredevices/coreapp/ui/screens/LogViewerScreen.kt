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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
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
        var watchAppFilter by remember { mutableStateOf(false) }
        var lineWrap by remember { mutableStateOf(false) }
        val filteredLogLines by remember {
            derivedStateOf {
                if (watchAppFilter) {
                    logLines?.filter { line ->
                        "PKJS" in line || "PebbleKit" in line
                    }
                } else {
                    logLines
                }
            }
        }
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
                    title = { Text("Logs") },
                    navigationIcon = {
                        IconButton(onClick = coreNav::goBack) {
                            Icon(
                                Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { lineWrap = !lineWrap }) {
                            Icon(
                                Icons.Default.WrapText,
                                contentDescription = if (lineWrap) "Disable line wrap" else "Enable line wrap",
                                tint = if (lineWrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { watchAppFilter = !watchAppFilter }) {
                            Icon(
                                Icons.Default.Watch,
                                contentDescription = if (watchAppFilter) "Show all logs" else "Show watch app logs",
                                tint = if (watchAppFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
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
                                    val prefix = if (watchAppFilter) "pebble-watch-logs" else "pebble-app-logs"
                                    val filename = "$prefix-$timestamp.log"
                                    val sharePath = Path(getLogsCacheDir() + "/$filename")
                                    SystemFileSystem.delete(sharePath, mustExist = false)
                                    if (watchAppFilter && filteredLogLines != null) {
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
                            Text(
                                text = line,
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
