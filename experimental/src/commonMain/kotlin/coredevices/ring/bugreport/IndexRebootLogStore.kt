package coredevices.ring.bugreport

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.haversine.KMPHaversineDebugRebootReason
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

@Serializable
data class IndexRebootLogEntry(
    val timestamp: Instant,
    val code: UInt,
    val context: UInt,
    val description: String?,
) {
    val reason: String
        get() = "${description ?: "UNKNOWN"} (code=0x${code.hex()}, context=0x${context.hex()})"

    private fun UInt.hex() = toString(16).padStart(8, '0')
}

class IndexRebootLogStore(private val settings: Settings) {
    companion object {
        const val MAX_ENTRIES = 15
        private const val KEY = "index_reboot_log"
        private val logger = Logger.withTag("IndexRebootLogStore")
        private val json = Json { ignoreUnknownKeys = true }
    }

    fun entries(): List<IndexRebootLogEntry> = settings.getStringOrNull(KEY)?.let { raw ->
        try {
            json.decodeFromString<List<IndexRebootLogEntry>>(raw)
        } catch (e: Exception) {
            logger.w(e) { "Discarding unreadable reboot log" }
            null
        }
    } ?: emptyList()

    fun record(timestamp: Instant, reasons: List<KMPHaversineDebugRebootReason>) {
        if (reasons.isEmpty()) return
        val fresh = reasons.map { IndexRebootLogEntry(timestamp, it.code, it.context, it.description) }.asReversed()
        settings.putString(KEY, json.encodeToString((fresh + entries()).take(MAX_ENTRIES)))
    }
}
