package io.rebble.libpebblecommon.io.rebble.libpebblecommon.calls

import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.calls.Call
import io.rebble.libpebblecommon.calls.LegacyPhoneReceiver
import io.rebble.libpebblecommon.calls.LibPebbleInCallService.Companion.resolveNameFromContacts
import io.rebble.libpebblecommon.calls.NotificationCallDetector
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.util.PrivateLogger
import io.rebble.libpebblecommon.util.asFlow
import io.rebble.libpebblecommon.util.obfuscate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Handles phone call state via ACTION_PHONE_STATE_CHANGED broadcasts.
 *
 * When InCallService is available (companion device association exists), it defers to
 * InCallService for calls it has already claimed. For VoIP calls (which InCallService
 * doesn't handle), this receiver takes ownership.
 *
 * When InCallService is not available, this receiver handles all calls.
 *
 * Uses [NotificationCallDetector] for contact info and answer/decline actions from
 * CATEGORY_CALL notifications.
 */
class AndroidPhoneReceiver(
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val context: Context,
    private val callDetector: NotificationCallDetector,
    private val privateLogger: PrivateLogger,
) : LegacyPhoneReceiver {
    private val logger = Logger.withTag("AndroidPhoneReceiver")
    private var nullCallJob: Job? = null
    private var ringingDelayJob: Job? = null
    private var receiverCookie: UInt? = null

    private fun inCallServiceAvailable(): Boolean {
        val service = context.getSystemService(CompanionDeviceManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            service.myAssociations.isNotEmpty()
        } else {
            @Suppress("DEPRECATION")
            service.associations.isNotEmpty()
        }
    }

    private fun cancelNullCallJob() {
        nullCallJob?.cancel()
        nullCallJob = null
    }

    private fun cancelRingingDelayJob() {
        ringingDelayJob?.cancel()
        ringingDelayJob = null
    }

    override fun init(currentCall: MutableStateFlow<Call?>) {
        libPebbleCoroutineScope.launch {
            IntentFilter(ACTION_PHONE_STATE_CHANGED).asFlow(context, exported = true).collect { intent ->
                val callState = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                logger.v { "Phone state: $callState" }
                when (callState) {
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        cancelNullCallJob()
                        cancelRingingDelayJob()
                        // Only clear if this receiver owns the call
                        if (receiverCookie != null && currentCall.value?.cookie == receiverCookie) {
                            logger.d { "Call ended (idle)" }
                            currentCall.value = null
                        }
                        receiverCookie = null
                    }
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                        cancelNullCallJob()
                        if (number == null) {
                            // We often get two RINGING broadcasts in quick succession — the first
                            // one doesn't have a number, the second one does. Only use the null one
                            // if we don't quickly receive a non-null one.
                            nullCallJob = libPebbleCoroutineScope.launch {
                                delay(0.5.seconds)
                                logger.v { "No second RINGING with number received, handling with null number" }
                                if (currentCall.value != null) {
                                    logger.v { "InCallService already handling this call, skipping" }
                                    return@launch
                                }
                                handleRinging(currentCall, null)
                            }
                            return@collect
                        }

                        handleRingingWithDelay(currentCall, number)
                    }
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        cancelNullCallJob()
                        cancelRingingDelayJob()
                        val existingCall = currentCall.value
                        if (existingCall == null || existingCall.cookie != receiverCookie) {
                            // Not our call (InCallService is handling it) or no call
                            if (existingCall == null) {
                                logger.w { "Received STATE_OFFHOOK but no current call" }
                            }
                            return@collect
                        }
                        // Get the latest decline action for hangup
                        val declineAction = callDetector.declineAction
                        currentCall.value = Call.ActiveCall(
                            contactName = existingCall.contactName,
                            contactNumber = existingCall.contactNumber,
                            cookie = existingCall.cookie,
                            onCallEnd = {
                                if (declineAction != null) {
                                    logger.v { "Ending call via notification action" }
                                    try {
                                        declineAction.actionIntent.send()
                                    } catch (e: Exception) {
                                        logger.e(e) { "Failed to trigger hangup action" }
                                    }
                                } else {
                                    logger.d { "No hangup action available" }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun handleRingingWithDelay(currentCall: MutableStateFlow<Call?>, number: String?) {
        if (inCallServiceAvailable()) {
            // InCallService is available — give it a moment to claim the call.
            // If it doesn't (VoIP call), we handle it.
            cancelRingingDelayJob()
            ringingDelayJob = libPebbleCoroutineScope.launch {
                logger.v { "scheduling ringingDelayJob" }
                delay(500.milliseconds)
                if (currentCall.value != null) {
                    logger.v { "InCallService already handling this call, skipping" }
                    return@launch
                }
                handleRinging(currentCall, number)
            }
        } else {
            // No InCallService — handle immediately
            handleRinging(currentCall, number)
        }
    }

    private fun handleRinging(currentCall: MutableStateFlow<Call?>, number: String?) {
        logger.v { "handle ringing" }
        val cookie = Random.nextUInt()
        receiverCookie = cookie

        // Prefer contact info from the CATEGORY_CALL notification
        val contactName = callDetector.contactName
            ?: context.contentResolver.resolveNameFromContacts(number)
        val contactNumber = callDetector.contactNumber ?: number ?: "Unknown number"

        val answerAction = callDetector.answerAction
        val declineAction = callDetector.declineAction

        logger.d { "Handling ringing call: ${contactName.obfuscate(privateLogger)} / ${contactNumber.obfuscate(privateLogger)} (answer=${answerAction != null}, decline=${declineAction != null})" }

        currentCall.value = Call.RingingCall(
            contactName = contactName,
            contactNumber = contactNumber,
            cookie = cookie,
            onCallAnswer = {
                if (answerAction != null) {
                    logger.v { "Answering call via notification action" }
                    try {
                        answerAction.actionIntent.send()
                    } catch (e: Exception) {
                        logger.e(e) { "Failed to trigger answer action" }
                    }
                } else {
                    logger.d { "No answer action available" }
                }
            },
            onCallEnd = {
                if (declineAction != null) {
                    logger.v { "Declining call via notification action" }
                    try {
                        declineAction.actionIntent.send()
                    } catch (e: Exception) {
                        logger.e(e) { "Failed to trigger decline action" }
                    }
                } else {
                    logger.d { "No decline action available" }
                }
            },
        )
    }
}
