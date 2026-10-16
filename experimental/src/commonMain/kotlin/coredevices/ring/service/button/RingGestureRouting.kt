package coredevices.ring.service.button

import coredevices.ring.database.MusicControlMode
import coredevices.ring.database.SecondaryMode
import coredevices.ring.service.ButtonPress

enum class GestureKind { Music, Recording }

enum class RingGesture(val sequence: List<ButtonPress>, val kind: GestureKind) {
    Click(listOf(ButtonPress.Short), GestureKind.Music),
    DoubleClick(listOf(ButtonPress.Short, ButtonPress.Short), GestureKind.Music),
    TripleClick(listOf(ButtonPress.Short, ButtonPress.Short, ButtonPress.Short), GestureKind.Music),
    Hold(listOf(ButtonPress.Long), GestureKind.Recording),
    ClickHold(listOf(ButtonPress.Short, ButtonPress.Long), GestureKind.Recording);

    companion object {
        fun forSequence(sequence: List<ButtonPress>): RingGesture? =
            entries.firstOrNull { it.sequence == sequence }
    }
}

/** Where a gesture sends its input. [Music] destinations are only valid for
 *  [GestureKind.Music] gestures, [Recording] destinations only for [GestureKind.Recording]. */
sealed interface GestureDestination {
    sealed interface Music : GestureDestination
    sealed interface Recording : GestureDestination

    data object PlayPause : Music
    data object NextTrack : Music
    /** Cloud agent that falls back to the on-device one when the cloud can't be reached. */
    data object IndexAgent : Recording
    /** On-device agent only. */
    data object LocalAgent : Recording
    /** Cloud agent only; needs a connection and a signed-in user. */
    data object RemoteAgent : Recording
    data object WebSearch : Recording
    data object WebhookOnly : Recording
    /** Transcribes the recording and puts the text on the phone's clipboard; no agent runs. */
    data object CopyToClipboard : Recording
    data object Nothing : Music, Recording
}

fun RingGesture.accepts(destination: GestureDestination): Boolean = when (kind) {
    GestureKind.Music -> destination is GestureDestination.Music
    GestureKind.Recording -> destination is GestureDestination.Recording
}

/** True while the local agent is the only Index agent any gesture reaches, so what it can't run
 *  is out of reach too. */
fun Map<RingGesture, GestureDestination>.routesOnlyToLocalAgent(): Boolean =
    GestureDestination.LocalAgent in values &&
        GestureDestination.IndexAgent !in values &&
        GestureDestination.RemoteAgent !in values

fun musicRoutesFor(mode: MusicControlMode): Map<RingGesture, GestureDestination.Music> = when (mode) {
    MusicControlMode.Disabled -> mapOf(
        RingGesture.Click to GestureDestination.Nothing,
        RingGesture.DoubleClick to GestureDestination.Nothing,
        RingGesture.TripleClick to GestureDestination.Nothing,
    )
    MusicControlMode.SingleClick -> mapOf(
        RingGesture.Click to GestureDestination.PlayPause,
        RingGesture.DoubleClick to GestureDestination.NextTrack,
        RingGesture.TripleClick to GestureDestination.Nothing,
    )
    MusicControlMode.DoubleClick -> mapOf(
        RingGesture.Click to GestureDestination.Nothing,
        RingGesture.DoubleClick to GestureDestination.PlayPause,
        RingGesture.TripleClick to GestureDestination.NextTrack,
    )
}

fun recordingRouteFor(mode: SecondaryMode): GestureDestination.Recording = when (mode) {
    SecondaryMode.Disabled, SecondaryMode.McpSandbox -> GestureDestination.IndexAgent
    SecondaryMode.Search -> GestureDestination.WebSearch
    SecondaryMode.IndexWebhook -> GestureDestination.WebhookOnly
}

fun migratedRoutes(
    musicControlMode: MusicControlMode,
    secondaryMode: SecondaryMode,
): Map<RingGesture, GestureDestination> = musicRoutesFor(musicControlMode) + mapOf(
    RingGesture.Hold to GestureDestination.IndexAgent,
    RingGesture.ClickHold to recordingRouteFor(secondaryMode),
)
