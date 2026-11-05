package io.rebble.libpebblecommon.calls

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.time.Duration.Companion.seconds

/**
 * A call notification posted by the calling app. It is ringing while it offers an answer action.
 */
data class NotificationCall(
    val contactName: String?,
    val contactNumber: String?,
    val ringing: Boolean,
)

/**
 * Drives [Call] state from call notifications, for apps whose calls never reach the system
 * telephony or Telecom stack. Calls that stack reports are left alone.
 */
class NotificationCallTracker(
    private val currentCall: MutableStateFlow<Call?>,
    private val answer: () -> Unit,
    private val end: () -> Unit,
) {
    companion object {
        private val logger = Logger.withTag("NotificationCallTracker")
        // Longer than the delay AndroidPhoneReceiver gives InCallService to claim a call.
        private val CLAIM_DELAY = 1.seconds
        // Apps remove and re-post the notification when a call is answered.
        private val REPOST_DELAY = 3.seconds
    }

    private var cookie: UInt? = null

    private fun ownsCurrentCall() = cookie != null && currentCall.value?.cookie == cookie

    suspend fun track(notifications: Flow<NotificationCall?>) {
        notifications.collectLatest { notification ->
            when {
                notification == null -> {
                    delay(REPOST_DELAY)
                    if (ownsCurrentCall()) {
                        logger.d { "Call notification gone, ending call" }
                        currentCall.value = null
                    }
                    cookie = null
                }
                notification.ringing -> {
                    if (ownsCurrentCall()) return@collectLatest
                    delay(CLAIM_DELAY)
                    if (currentCall.value != null) return@collectLatest
                    logger.d { "Ringing from call notification" }
                    val newCookie = Random.nextUInt()
                    cookie = newCookie
                    currentCall.value = Call.RingingCall(
                        contactName = notification.contactName,
                        contactNumber = notification.contactNumber ?: "",
                        cookie = newCookie,
                        onCallEnd = { end() },
                        onCallAnswer = { answer() },
                    )
                }
                else -> {
                    val call = currentCall.value
                    if (!ownsCurrentCall() || call !is Call.RingingCall) return@collectLatest
                    logger.d { "Call notification no longer ringing, call is active" }
                    currentCall.value = Call.ActiveCall(
                        contactName = call.contactName,
                        contactNumber = call.contactNumber,
                        cookie = call.cookie,
                        onCallEnd = { end() },
                    )
                }
            }
        }
    }
}
