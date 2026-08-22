package io.rebble.libpebblecommon.calls

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.Contacts
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.di.LibPebbleKoinComponent
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.inject
import kotlin.random.Random
import kotlin.random.nextUInt

class LibPebbleInCallService : InCallService(), LibPebbleKoinComponent {
    companion object {
        private val logger = Logger.withTag("LibPebbleInCallService")

        fun ContentResolver.resolveNameFromContacts(number: String?): String? {
            if (number == null) return null
            return try {
                val lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
                val cursor = query(
                    lookupUri,
                    arrayOf(Contacts.DISPLAY_NAME),
                    null,
                    null,
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        it.getString(it.getColumnIndexOrThrow(Contacts.DISPLAY_NAME))
                    } else {
                        null
                    }
                }
            } catch (e: SecurityException) {
                logger.w(e) { "Error getting contact name" }
                null
            }
        }
    }

    private val libPebble: LibPebble by inject()
    private val callDoNotDisturbFilter: CallDoNotDisturbFilter by inject()
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope by inject()
    private val inCallServiceCallCoordinator: InCallServiceCallCoordinator by inject()
    private val suppressedCalls = mutableSetOf<Call>()
    private val pendingDndChecks = mutableSetOf<Call>()

    override fun onCreate() {
        super.onCreate()
        logger.d { "onCreate()" }
        libPebble.currentCall.value = null
    }

    override fun onDestroy() {
        logger.d { "onDestroy()" }
        libPebble.currentCall.value = null
        inCallServiceCallCoordinator.clearAll()
        super.onDestroy()
    }

    private inner class Callback(private val cookie: UInt) : Call.Callback() {
        override fun onDetailsChanged(
            call: Call?,
            details: Call.Details?,
        ) {
            logger.d { "Call details changed: $details / ${details?.extras?.dump()}" }
        }

        override fun onStateChanged(call: Call?, state: Int) {
            call ?: return
            logger.d { "Call state changed: ${call.state} (arg state: $state)" }
            publishCall(call, cookie)
        }
    }

    override fun onCallAdded(call: Call?) {
        call ?: return
        if (calls.size > 1) {
            logger.w { "Multiple calls detected" }
        }
        val cookie = Random.nextUInt()
        val callback = Callback(cookie)
        calls.filter { it != call }.forEach {
            it.unregisterCallback(callback)
        }
        logger.d { "New call in state: ${call.state}" }
        call.registerCallback(callback)
        publishCall(call, cookie)
    }

    override fun onCallRemoved(call: Call?) {
        call ?: return
        libPebbleCoroutineScope.launch(Dispatchers.Main.immediate) {
            suppressedCalls.remove(call)
            pendingDndChecks.remove(call)
            inCallServiceCallCoordinator.clear(call)
            libPebble.currentCall.value = null
        }
    }

    private fun Call.resolveContactName(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            details.contactDisplayName
        } else {
            contentResolver.resolveNameFromContacts(details.handle.schemeSpecificPart)
        }
    }

    private fun Call.resolveContactNumber(): String {
        return this.details.handle?.schemeSpecificPart ?: "Unknown"
    }

    private fun publishCall(call: Call, cookie: UInt) {
        libPebbleCoroutineScope.launch(Dispatchers.Main.immediate) {
            if (call in suppressedCalls || call in pendingDndChecks) return@launch
            if (call.state == Call.STATE_RINGING) {
                inCallServiceCallCoordinator.markHandling(call)
                pendingDndChecks += call
                val pebbleCall = createLibPebbleCall(call, cookie)
                        as? io.rebble.libpebblecommon.calls.Call.RingingCall
                    ?: run {
                        pendingDndChecks.remove(call)
                        inCallServiceCallCoordinator.clear(call)
                        return@launch
                    }
                val shouldSuppress = callDoNotDisturbFilter.shouldSuppressIncomingCall(
                    contactName = pebbleCall.contactName,
                    contactNumber = pebbleCall.contactNumber,
                )
                if (call !in pendingDndChecks) return@launch
                if (shouldSuppress) {
                    suppressedCalls += call
                    pendingDndChecks.remove(call)
                    return@launch
                }
                pendingDndChecks.remove(call)
                val currentPebbleCall = createLibPebbleCall(call, cookie) ?: run {
                    inCallServiceCallCoordinator.clear(call)
                    return@launch
                }
                libPebble.currentCall.value = currentPebbleCall
                return@launch
            }
            inCallServiceCallCoordinator.markHandling(call)
            val pebbleCall = createLibPebbleCall(call, cookie) ?: run {
                inCallServiceCallCoordinator.clear(call)
                return@launch
            }
            libPebble.currentCall.value = pebbleCall
        }
    }

    private fun createLibPebbleCall(call: Call, cookie: UInt): io.rebble.libpebblecommon.calls.Call? =
        when (call.state) {
            Call.STATE_RINGING -> io.rebble.libpebblecommon.calls.Call.RingingCall(
                contactName = call.resolveContactName(),
                contactNumber = call.resolveContactNumber(),
                onCallEnd = { call.disconnect() },
                onCallAnswer = { call.answer(VideoProfile.STATE_AUDIO_ONLY) },
                cookie = cookie,
            )
            Call.STATE_DIALING, Call.STATE_CONNECTING -> io.rebble.libpebblecommon.calls.Call.DialingCall(
                contactName = call.resolveContactName(),
                contactNumber = call.resolveContactNumber(),
                onCallEnd = { call.disconnect() },
                cookie = cookie,
            )
            Call.STATE_ACTIVE -> io.rebble.libpebblecommon.calls.Call.ActiveCall(
                contactName = call.resolveContactName(),
                contactNumber = call.resolveContactNumber(),
                onCallEnd = { call.disconnect() },
                cookie = cookie,
            )
            Call.STATE_HOLDING -> io.rebble.libpebblecommon.calls.Call.HoldingCall(
                contactName = call.resolveContactName(),
                contactNumber = call.resolveContactNumber(),
                onCallEnd = { call.disconnect() },
                cookie = cookie,
            )
            else -> {
                logger.w { "Unknown call state: ${call.state}" }
                null
            }
        }

    private fun Bundle.dump(): String {
        return keySet().joinToString(prefix = "\n", separator = "\n") {
            "$it = ${get(it)}"
        }
    }
}
