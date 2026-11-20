package coredevices.ring.service

import co.touchlab.kermit.Logger
import coredevices.haversine.ButtonSequenceDebouncer
import coredevices.haversine.TransferStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.time.Clock
import kotlin.time.Instant

class IndexButtonSequenceRecorder(scope: RecordingBackgroundScope) {
    companion object {
        private val logger = Logger.withTag("IndexButtonSequenceRecorder")
    }

    private val debouncer = ButtonSequenceDebouncer(scope)

    fun onTransferStatus(status: TransferStatus) {
        debouncer.onTransferStatus(status)
    }

    /**
     * Returns a flow of button press sequences, debounced to get the
     * final sequence after successive rapid transfers.
     */
    fun sequenceEvents(): Flow<ButtonSequenceEvent> =
        debouncer.buttonGestures
            .onEach { logger.d { "Debounced gesture: $it" } }
            .map { ButtonSequenceEvent(it.sequence.trim().parseAsButtonSequence(), it.releaseTimestamp ?: Clock.System.now()) }
            .filter { it.presses.isNotEmpty() }
}

data class ButtonSequenceEvent(val presses: List<ButtonPress>, val releasedAt: Instant)

enum class ButtonPress {
    Short,
    Long
}

fun List<ButtonPress>.toButtonSequenceString(): String = joinToString(" ") { it.name.lowercase() }

fun String.parseAsButtonSequence(): List<ButtonPress> {
    return this.split(" ").mapNotNull {
        when (it.lowercase()) {
            "short" -> ButtonPress.Short
            "long" -> ButtonPress.Long
            else -> null
        }
    }
}
