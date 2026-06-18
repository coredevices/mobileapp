package io.rebble.libpebblecommon.calls

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.StatusBarNotification
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.NotificationConfigFlow
import io.rebble.libpebblecommon.util.PrivateLogger
import io.rebble.libpebblecommon.util.obfuscate
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class CallDoNotDisturbFilter(
    private val notificationConfig: NotificationConfigFlow,
    private val privateLogger: PrivateLogger,
    private val currentInterruptionFilter: () -> Int,
) {
    companion object {
        private val logger = Logger.withTag("CallDoNotDisturbFilter")
        private val RANKING_WAIT = 500.milliseconds
        private val RANKING_TTL = 5_000.milliseconds
        private const val PHONE_MATCH_MIN_DIGITS = 7
        private const val CALLER_NAME_MATCH_MIN_CHARS = 3
    }

    private data class CallRanking(
        val key: String,
        val packageName: String,
        val title: String?,
        val text: String?,
        val matchesInterruptionFilter: Boolean?,
        val recordedAt: TimeSource.Monotonic.ValueTimeMark,
    )

    private var latestCallRanking: CallRanking? = null

    fun recordCallNotification(
        sbn: StatusBarNotification,
        matchesInterruptionFilter: Boolean?,
    ) {
        val notification = sbn.notification
        recordCallRanking(
            key = sbn.key,
            packageName = sbn.packageName,
            title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            matchesInterruptionFilter = matchesInterruptionFilter,
        )
    }

    internal fun recordCallRanking(
        key: String,
        packageName: String,
        title: String? = null,
        text: String? = null,
        matchesInterruptionFilter: Boolean?,
    ) {
        latestCallRanking = CallRanking(
            key = key,
            packageName = packageName,
            title = title,
            text = text,
            matchesInterruptionFilter = matchesInterruptionFilter,
            recordedAt = TimeSource.Monotonic.markNow(),
        )
    }

    fun updateCallRanking(
        key: String,
        matchesInterruptionFilter: Boolean,
    ) {
        val ranking = latestCallRanking?.takeIfFresh() ?: return
        if (ranking.key != key) return
        latestCallRanking = ranking.copy(
            matchesInterruptionFilter = matchesInterruptionFilter,
            recordedAt = TimeSource.Monotonic.markNow(),
        )
    }

    fun clearCallNotification(sbn: StatusBarNotification) {
        if (latestCallRanking?.key == sbn.key) {
            latestCallRanking = null
        }
    }

    fun clearRecordedCallRanking() {
        latestCallRanking = null
    }

    suspend fun shouldSuppressIncomingCall(
        contactName: String?,
        contactNumber: String,
    ): Boolean {
        if (!notificationConfig.value.respectDoNotDisturb) return false
        if (!isDoNotDisturbActive()) return false

        waitForRecentRanking(contactName, contactNumber)

        val ranking = latestCallRanking
            ?.takeIfFresh()
            ?.takeIf { it.matchesCall(contactName, contactNumber) }
        val shouldSuppress = ranking?.matchesInterruptionFilter != true
        if (shouldSuppress) {
            logger.d {
                "Suppressing call during DND: ${contactName.obfuscate(privateLogger)} / " +
                    contactNumber.obfuscate(privateLogger) +
                    " (ranking=${ranking?.packageName ?: "missing"})"
            }
        }
        if (ranking != null) {
            latestCallRanking = null
        }
        return shouldSuppress
    }

    private suspend fun waitForRecentRanking(contactName: String?, contactNumber: String) {
        waitUntil(RANKING_WAIT) {
            latestCallRanking
                ?.takeIfFresh()
                ?.takeIf { it.matchesCall(contactName, contactNumber) }
                ?.matchesInterruptionFilter != null || !isDoNotDisturbActive()
        }
    }

    private suspend fun waitUntil(timeout: Duration, condition: () -> Boolean) {
        withTimeoutOrNull(timeout) {
            while (!condition()) {
                delay(50.milliseconds)
            }
        }
    }

    private fun CallRanking.takeIfFresh(): CallRanking? =
        takeIf { it.recordedAt.elapsedNow() <= RANKING_TTL }

    private fun CallRanking.matchesCall(contactName: String?, contactNumber: String): Boolean {
        val callNumber = contactNumber.normalizedPhoneDigits()
        val notificationNumbers = listOfNotNull(title, text).map { it.normalizedPhoneDigits() }
        if (callNumber.matchesAnyPhoneNumber(notificationNumbers)) return true

        val callNames = listOfNotNull(contactName?.normalizedCallerName(), contactNumber.normalizedCallerName())
            .filter { it.isMeaningfulCallerName() }
        val notificationNames = listOfNotNull(title?.normalizedCallerName(), text?.normalizedCallerName())
            .filter { it.isMeaningfulCallerName() }
        return notificationNames.any { notificationName ->
            callNames.any { callName ->
                notificationName == callName ||
                    notificationName.contains(callName) ||
                    callName.contains(notificationName)
            }
        }
    }

    private fun String.normalizedPhoneDigits(): String =
        filter { it.isDigit() }

    private fun String.matchesAnyPhoneNumber(others: List<String>): Boolean {
        if (length < PHONE_MATCH_MIN_DIGITS) return false
        return others.any { other ->
            other.length >= PHONE_MATCH_MIN_DIGITS &&
                (endsWith(other) || other.endsWith(this))
        }
    }

    private fun String.normalizedCallerName(): String =
        lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun String.isMeaningfulCallerName(): Boolean =
        length >= CALLER_NAME_MATCH_MIN_CHARS &&
            this != "unknown" &&
            this != "unknown number" &&
            this != "private number"

    private fun isDoNotDisturbActive(): Boolean =
        currentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL
}
