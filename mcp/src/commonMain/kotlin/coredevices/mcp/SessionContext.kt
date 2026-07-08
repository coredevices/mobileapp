package coredevices.mcp

import kotlinx.coroutines.Deferred
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Context for the session a tool call belongs to.
 * @property timeBase the true time of the recording being processed (not upload/processing time),
 * to be used as the base for e.g. relative time calculations. Null when the source couldn't
 * determine it (e.g. the ring wasn't able to report when the recording was made) — tools must
 * not fall back to the current time for relative calculations in that case.
 * @property userMessageText a deferred that will resolve to the text of the user message that
 * triggered this tool call, or null if the message text is not available, e.g. deterministic
 * transcription failed.
 * @property recordingFirestoreId id of the recording this session processes, so tools that
 * create local objects can link them back to it. Null outside a recording (e.g. share sheet).
 */
data class SessionContext(
    val timeBase: Instant?,
    val userMessageText: Deferred<String?>,
    val recordingFirestoreId: String? = null,
)

/** A clock frozen at this instant, for resolving relative/ambiguous times against the recording time. */
fun Instant.asFrozenClock(): Clock = object : Clock {
    override fun now(): Instant = this@asFrozenClock
}
