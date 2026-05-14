package coredevices.util.transcription

import android.os.Process
import android.os.Process.THREAD_PRIORITY_URGENT_AUDIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


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

actual suspend fun getFreeMemoryMB(): Long {
    // get available memory in MB
    val runtime = Runtime.getRuntime()
    val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val maxMemory = runtime.maxMemory() / (1024 * 1024)
    return maxMemory - usedMemory
}

actual val PLATFORM_MIN_TRANSCRIPTION_MEMORY_MB: Long = 20