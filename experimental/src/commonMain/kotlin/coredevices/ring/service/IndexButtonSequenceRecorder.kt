package coredevices.ring.service

import co.touchlab.kermit.Logger
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach

class IndexButtonSequenceRecorder {
    companion object Companion {
        const val SEQUENCE_SHORT = "short"
        const val SEQUENCE_LONG = "long"
        const val EVENT_DEBOUNCE_MS = 700L //TODO: check transfer buffer count as heuristic so we dont need this so high
    }
    private val _sequenceEventFlow = MutableSharedFlow<List<ButtonPress>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun recordSequence(sequence: String) {
        val buttonPresses = sequence.trim().parseAsButtonSequence()
        if (buttonPresses.isNotEmpty()) {
            _sequenceEventFlow.tryEmit(buttonPresses)
        }
    }

    fun recordNoSequence() {
        _sequenceEventFlow.tryEmit(emptyList())
    }

    /**
     * Returns a flow of button press sequences, debounced to get the
     * final sequence after successive rapid transfers.
     */
    @OptIn(FlowPreview::class)
    fun sequenceEvents() =
        _sequenceEventFlow
            .asSharedFlow()
            .onEach { Logger.withTag("IndexButtonSequenceRecorder").d { "Undebounced: $it" } }
            .debounce(EVENT_DEBOUNCE_MS)
            .filter { it.isNotEmpty() }
            .onEach { Logger.withTag("IndexButtonSequenceRecorder").d { "Debounced: $it" } }
}

enum class ButtonPress {
    Short,
    Long
}

fun String.parseAsButtonSequence(): List<ButtonPress> {
    return this.split(" ").mapNotNull {
        when (it.lowercase()) {
            "short" -> ButtonPress.Short
            "long" -> ButtonPress.Long
            else -> null
        }
    }
}