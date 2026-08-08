package coredevices.ring.service

import co.touchlab.kermit.Logger
import coredevices.ring.data.entity.room.ClickActionBinding
import coredevices.ring.database.MusicControlMode
import coredevices.ring.database.Preferences
import coredevices.ring.database.reservedClickCounts
import coredevices.ring.database.room.repository.ClickActionRepository
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** What a debounced button sequence should trigger. */
internal sealed interface ButtonDispatch {
    data object Ignore : ButtonDispatch
    data class Media(val presses: List<ButtonPress>) : ButtonDispatch
    data class Custom(val clickCount: Int) : ButtonDispatch
}

/**
 * Precedence rule for a debounced sequence, kept pure so it can be tested without the platform
 * media calls. Media control wins over a custom binding — a binding on a media-owned count
 * would never fire, which is why the settings UI locks those counts.
 */
internal fun resolveButtonDispatch(
    presses: List<ButtonPress>,
    reservedByMedia: Set<Int>,
): ButtonDispatch {
    // A long press means a recording gesture, owned by the recording pipeline.
    if (presses.isEmpty() || presses.any { it != ButtonPress.Short }) return ButtonDispatch.Ignore
    val clickCount = presses.size
    return when {
        clickCount in reservedByMedia -> ButtonDispatch.Media(presses)
        clickCount in ClickActionBinding.CLICK_COUNT_RANGE -> ButtonDispatch.Custom(clickCount)
        else -> ButtonDispatch.Ignore
    }
}

class IndexButtonActionHandler(
    private val prefs: Preferences,
    sequenceRecorder: IndexButtonSequenceRecorder,
    private val clickActionRepository: ClickActionRepository,
    private val clickActionExecutor: ClickActionExecutor,
    private val scope: RecordingBackgroundScope,
) {
    companion object {
        private val logger = Logger.withTag("IndexButtonActionHandler")
    }
    private val sequenceEvents = sequenceRecorder.sequenceEvents()

    private val actions = mapOf<List<ButtonPress>, suspend () -> Unit>(
        listOf(ButtonPress.Short) to {
            if (prefs.musicControlMode.value == MusicControlMode.SingleClick) {
                onPlayPause()
            }
        },
        listOf(ButtonPress.Short, ButtonPress.Short) to {
            when (prefs.musicControlMode.value) {
                MusicControlMode.DoubleClick -> onPlayPause()
                MusicControlMode.SingleClick -> onNextTrack()
                else -> {}
            }
        },
        listOf(ButtonPress.Short, ButtonPress.Short, ButtonPress.Short) to {
            if (prefs.musicControlMode.value == MusicControlMode.DoubleClick) {
                onNextTrack()
            }
        },
    )

    suspend fun handleButtonActions() {
        sequenceEvents.collect { buttonPresses ->
            val reserved = prefs.musicControlMode.value.reservedClickCounts()
            when (val dispatch = resolveButtonDispatch(buttonPresses, reserved)) {
                ButtonDispatch.Ignore -> {}
                is ButtonDispatch.Media -> {
                    actions[dispatch.presses]?.invoke()
                    logger.i { "Handled media action for ${dispatch.presses.size} click(s)" }
                }
                is ButtonDispatch.Custom -> {
                    // Dispatched off the collector: an action can take seconds (agent
                    // inference, MCP connects) and awaiting it here would delay or drop the
                    // next gesture, media control included.
                    scope.launch { runClickAction(dispatch.clickCount) }
                }
            }
        }
    }

    private suspend fun runClickAction(clickCount: Int) {
        val binding = clickActionRepository.enabledBindingFor(clickCount) ?: return
        try {
            clickActionExecutor.execute(binding)
            logger.i { "Handled click action for $clickCount click(s): ${binding.action}" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Click action for $clickCount click(s) failed" }
        }
    }
}
