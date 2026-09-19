package coredevices.util

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CompletableDeferred

private const val SEEN_KEY = "hasSeenSecondaryProfileWarning"

/** One-time "running in a secondary profile" prompt. */
class SecondaryProfileWarning(
    private val settings: Settings,
    private val isSecondaryProfile: Boolean,
) {
    private val decided = CompletableDeferred<Unit>()

    val pending: Boolean
        get() = isSecondaryProfile && !settings.getBoolean(SEEN_KEY, false)

    fun markSeen() {
        settings.putBoolean(SEEN_KEY, true)
        decided.complete(Unit)
    }

    suspend fun awaitDecision() {
        if (pending) decided.await()
    }
}
