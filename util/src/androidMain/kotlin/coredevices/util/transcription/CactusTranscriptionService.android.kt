package coredevices.util.transcription

import android.os.Process
import android.os.Process.THREAD_PRIORITY_URGENT_AUDIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


actual suspend fun withHighPriorityThread(block: suspend () -> Unit) {
    withContext(Dispatchers.Default.limitedParallelism(1)) {
        val originalPriority = Process.getThreadPriority(Process.myTid())
        Process.setThreadPriority(THREAD_PRIORITY_URGENT_AUDIO)
        try {
            block()
        } finally {
            Process.setThreadPriority(originalPriority)
        }
    }
}

actual suspend fun getFreeMemoryMB(): Long = withContext(Dispatchers.IO) {
    parseMemAvailableMB(File("/proc/meminfo").readText())
        ?: throw IllegalStateException("MemAvailable missing from /proc/meminfo")
}

/** The kernel's estimate of memory usable without swapping, the same figure ActivityManager reports. */
internal fun parseMemAvailableMB(meminfo: String): Long? =
    meminfo.lineSequence()
        .firstOrNull { it.startsWith("MemAvailable:") }
        ?.substringAfter(':')
        ?.trim()
        ?.substringBefore(' ')
        ?.toLongOrNull()
        ?.let { it / 1024 }

actual val PLATFORM_MIN_TRANSCRIPTION_MEMORY_MB: Long = 40