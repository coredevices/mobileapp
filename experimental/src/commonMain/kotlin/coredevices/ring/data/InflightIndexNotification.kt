package coredevices.ring.data

import coredevices.mcp.data.SemanticResult
import kotlin.time.Duration
import kotlin.time.Instant

sealed interface InflightIndexNotification {
    val id: Int
    val pressedTimestamp: IndexTimestamp

    data class Transferring(
        override val id: Int,
        override val pressedTimestamp: IndexTimestamp
    ): InflightIndexNotification

    data class Transcribing(
        override val id: Int,
        override val pressedTimestamp: IndexTimestamp
    ): InflightIndexNotification

    data class AgentRunning(
        override val id: Int,
        override val pressedTimestamp: IndexTimestamp,
        val userText: String
    ): InflightIndexNotification

    data class AgentComplete(
        override val id: Int,
        override val pressedTimestamp: IndexTimestamp,
        val recordingId: Long,
        val userText: String,
        val pressToRXLatency: Duration?,
        val actionsTaken: List<SemanticResult>,
        val shortcutAction: NoteShortcutType
    ): InflightIndexNotification

    data class Error(
        override val id: Int,
        override val pressedTimestamp: IndexTimestamp,
        val message: String
    ): InflightIndexNotification
    data class Discarded(
        override val id: Int,
        override val pressedTimestamp: IndexTimestamp
    ): InflightIndexNotification
}

data class IndexTimestamp(
    val timestamp: Instant,
    val source: Source
) {
    enum class Source {
        RemoteDevice,
        LocalDevice
    }
}