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
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.util.asFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.time.Duration.Companion.seconds

/**
 * This is a fallback for when InCallService isn't available (i.e. when there is no
 * CompanionDeviceManager association).
 */
class AndroidPhoneReceiver(
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val context: Context,
) : LegacyPhoneReceiver {
    private val logger = Logger.withTag("AndroidPhoneReceiver")
    private var nullCallJob: Job? = null

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

    override fun init(currentCall: MutableStateFlow<Call?>) {
        libPebbleCoroutineScope.launch {
            IntentFilter(ACTION_PHONE_STATE_CHANGED).asFlow(context, exported = true).collect { intent ->
                if (inCallServiceAvailable()) {
                    logger.v { "Ignoring ACTION_PHONE_STATE_CHANGED because InCallService available" }
                    return@collect
                }
                val callState = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                logger.v { "Got phone state change: ${intent.action}: $callState" }
                when (callState) {
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        currentCall.value = null
                        cancelNullCallJob()
                    }
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                        if (number == null) {
                            // We often get two RINGING broadcasts in quick succession - the first
                            // one doesn't have a numer, the second one does. Only use the null one
                            // if we don't quickly receive a non-null one.
                            nullCallJob = libPebbleCoroutineScope.launch {
                                delay(0.5.seconds)
                            }
                            return@collect
                        }
                        cancelNullCallJob()
                        val name = context.contentResolver.resolveNameFromContacts(number)
                        val cookie = Random.nextUInt()
                        currentCall.value = Call.RingingCall(
                            contactName = name,
                            contactNumber = number ?: "Unknown",
                            cookie = cookie,
                            onCallEnd = { logger.d { "Can't end call from receiver" } },
                            onCallAnswer = { logger.d { "Can't answer call from receiver" } },
                        )
                    }
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        cancelNullCallJob()
                        val existingCall = currentCall.value
                        if (existingCall == null) {
                            logger.w { "Received STATE_OFFHOOK but no current call" }
                            return@collect
                        }
                        currentCall.value = Call.ActiveCall(
                            contactName = existingCall.contactName,
                            contactNumber = existingCall.contactNumber,
                            cookie = existingCall.cookie,
                            onCallEnd = { logger.d { "Can't end call from receiver" } },
                        )
                    }
                }
            }
        }
    }
}