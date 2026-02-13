package coredevices.coreapp.util

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import coredevices.ExperimentalDevices
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics
import io.ktor.utils.io.core.append
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.okio.asKotlinxIoRawSink
import kotlinx.io.okio.asKotlinxIoRawSource
import kotlinx.io.okio.asOkioSink
import kotlinx.io.okio.asOkioSource
import kotlinx.io.writeString
import okio.deflate
import okio.inflate
import okio.use
import org.koin.mp.KoinPlatform
import utils.CactusLogger

fun initLogging() {
    Logger.addLogWriter(object : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            if (severity != Severity.Verbose) {
                val log = buildString {
                    append("[$tag] $message")
                }
                Firebase.crashlytics.log(log)
            }
            if (severity != Severity.Debug && severity != Severity.Verbose) {
                throwable?.let { Firebase.crashlytics.recordException(it) }
            }
        }
    })
    Logger.addLogWriter(KoinPlatform.getKoin().get<FileLogWriter>())
    try {
        CactusLogger.setLogWriter(object : LogWriter() {
            override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                Logger.log(severity, tag, throwable, message)
            }
        })
    } catch (e: Exception) {
        Logger.e(e) { "Failed to add LogWriter to CactusLogger" }
    }
}

expect fun generateDeviceSummaryPlatformDetails(): String

fun generateDeviceSummary(experimentalDevices: ExperimentalDevices): String {
    val deviceSummary = generateDeviceSummaryPlatformDetails()
    val experimentalSummary = experimentalDevices.debugSummary()
    return deviceSummary + "\n" + (experimentalSummary ?: "")
}

class FileLogWriter : LogWriter(), AutoCloseable {
    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val logChannel = Channel<LogEntry>(capacity = 1000)

    private data class LogEntry(
        val severity: Severity,
        val message: String,
        val tag: String,
        val throwable: Throwable?
    )

    private class LogContext(
        val path: Path,
        val backupPath: Path,
        var sink: Sink,
    )

    companion object {
        private const val MAX_LOG_FILE_SIZE = 1024 * 1024 * 1.5 // 1.5MB
    }

    private lateinit var logContext: LogContext
    private val logMutex = Mutex()

    init {
        startLogProcessing()
    }

    private fun rollover(path: Path) {
        val backupTime = Clock.System.now()
        // Rename the existing log file to a backup
        val backupPath = Path(getLogsCacheDir() + "/previous.log.gz")
        SystemFileSystem.sink(backupPath).buffered().asOkioSink().deflate().use { sink ->
            SystemFileSystem.source(path).buffered().use { source ->
                source.transferTo(sink.asKotlinxIoRawSink())
            }
        }
        // Create a new log file
        SystemFileSystem.sink(path, append = false).buffered().use { sink ->
            sink.append("Log file rolled over at ${backupTime}\n")
            sink.flush()
        }
    }

    private fun startLogProcessing() {
        logScope.launch {
            val path = Path(getLogsCacheDir() + "/latest.log")
            logMutex.withLock {
                val backupPath = Path(getLogsCacheDir() + "/previous.log.gz")
                SystemFileSystem.createDirectories(Path(getLogsCacheDir()))
                // Roll over log file if it exists and exceeds the size limit
                SystemFileSystem.metadataOrNull(path)?.let {
                    if (it.size > MAX_LOG_FILE_SIZE) {
                        rollover(path)
                    }
                }
                val sink = SystemFileSystem.sink(path, true).buffered()
                logContext = LogContext(path, backupPath, sink)
            }
            try {
                for (logEntry in logChannel) { // Consumes from the channel
                    if (!logScope.isActive) break // Exit if scope is cancelled

                    logMutex.withLock { // Serialize access to the sink
                        SystemFileSystem.metadataOrNull(path)?.let {
                            if (it.size > MAX_LOG_FILE_SIZE) {
                                rollover(path)
                            }
                        }
                        writeLogEntryToFile(logEntry)
                    }
                }
            } finally {
                // This block executes when the channel is closed or the scope is cancelled
                logMutex.withLock {
                    logContext.sink.closeQuietly()
                }
            }
        }
    }

    override fun close() {
        logScope.cancel()
        logChannel.close()
        logContext.sink.closeQuietly()
    }

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val offerResult = logChannel.trySend(LogEntry(severity, message, tag, throwable))
        if (!offerResult.isSuccess) {
            // Handle failure to enqueue (e.g., if channel is closed or full with a bounded channel)
            println("Failed to enqueue log message (channel closed or full): $message")
        }
    }

    private fun writeLogEntryToFile(logEntry: LogEntry) {
        val severityChar = when (logEntry.severity) {
            Severity.Verbose -> 'V'
            Severity.Debug -> 'D'
            Severity.Info -> 'I'
            Severity.Warn -> 'W'
            Severity.Error -> 'E'
            Severity.Assert -> 'A'
        }
        val timestamp = Clock.System.now()
        val tagText = logEntry.tag.ifBlank { "Global" }
        logContext.sink.append("$timestamp [$severityChar] ${tagText}: ${logEntry.message}\n")
        logEntry.throwable?.let {
            logContext.sink.append(it.stackTraceToString() + "\n")
        }
    }

    /**
     * Only for calling when the app is dying, and we really need to log the exception.
     */
    fun logBlockingAndFlush(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?
    ) {
        runBlocking(logScope.coroutineContext) {
            logMutex.withLock {
                writeLogEntryToFile(LogEntry(severity, message, tag, throwable))
                logContext.sink.flush()
            }
        }
    }

    suspend fun dumpLogs(): Path {
        return withContext(logScope.coroutineContext) {
            logMutex.withLock {
                logContext.sink.flush()
                logContext.sink.closeQuietly()
                val dumpFile = Path(getLogsCacheDir() + "/upload.log")
                try {
                    SystemFileSystem.delete(dumpFile, mustExist = false)
                    if (SystemFileSystem.exists(logContext.backupPath)) {
                        SystemFileSystem.source(logContext.backupPath).buffered().asOkioSource().inflate().use { source ->
                            SystemFileSystem.sink(dumpFile, append = false).buffered().use { sink ->
                                sink.transferFrom(source.asKotlinxIoRawSource())
                                sink.writeString("\n")
                            }
                        }
                    }
                    SystemFileSystem.source(logContext.path).buffered().use { source ->
                        SystemFileSystem.sink(dumpFile, append = true).buffered().use { sink ->
                            sink.transferFrom(source)
                        }
                    }
                } catch (e: Exception) {
                    Logger.e(e) { "Error reading log file for bug report" }
                    try {
                        SystemFileSystem.sink(dumpFile, append = false).buffered().use { sink ->
                            sink.append("Error reading log file for bug report: $e / ${e.stackTraceToString()}")
                        }
                    } catch (_: Exception) {}
                } finally {
                    logContext.sink = SystemFileSystem.sink(logContext.path, true).buffered()
                }
                return@withContext dumpFile
            }
        }
    }
}

fun AutoCloseable.closeQuietly() = try {
    close()
} catch (e: Exception) {
}

expect fun getLogsCacheDir(): String