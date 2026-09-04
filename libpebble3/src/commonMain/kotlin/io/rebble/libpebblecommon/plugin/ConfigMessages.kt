package io.rebble.libpebblecommon.plugin

import kotlinx.coroutines.flow.Flow

/**
 * A direct line between an open config page and the script that owns it — a PKJS app or a
 * plugin. Deliberately separate from sources and actions: those are the consumer-facing surface
 * (watch, LLM), whereas a config page needs to show live values and apply a change the moment
 * the user makes it, without waiting for a save.
 *
 * Payloads are opaque JSON; only the script and its own page need to agree on them.
 */
interface ConfigMessageTarget {
    /** Messages the script pushes at the page, unprompted. */
    val configMessages: Flow<String>

    /**
     * A config dialogue is stateful across messages, so a script that is otherwise started per
     * request stays up between [openConfigSession] and [closeConfigSession].
     */
    suspend fun openConfigSession() {}

    suspend fun closeConfigSession() {}

    /** Deliver a message from the page. Returns the script's reply, or null if it didn't answer. */
    suspend fun onConfigMessage(json: String): String?
}
