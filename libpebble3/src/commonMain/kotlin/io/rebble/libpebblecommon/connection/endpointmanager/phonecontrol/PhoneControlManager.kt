package io.rebble.libpebblecommon.connection.endpointmanager.phonecontrol

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.LibPebbleConfigFlow
import io.rebble.libpebblecommon.calls.Call
import io.rebble.libpebblecommon.calls.SystemCallFilter
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.WatchPrefs
import io.rebble.libpebblecommon.database.entity.BoolWatchPref
import io.rebble.libpebblecommon.database.entity.watchReceivesWatchPrefs
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.PhoneControl
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.services.PhoneControlService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.random.Random
import kotlin.random.nextUInt

internal class PhoneControlManager(
    private val watchScope: ConnectionCoroutineScope,
    private val libPebble: LibPebble,
    private val phoneControlService: PhoneControlService,
    private val watchPrefs: WatchPrefs,
    private val libPebbleConfigFlow: LibPebbleConfigFlow,
    private val systemCallFilter: SystemCallFilter,
) {
    companion object {
        private val logger = Logger.withTag(PhoneControlManager::class.simpleName!!)
    }
    private var lastCookie: UInt? = null
    fun init(capabilities: Set<ProtocolCapsFlag>) {
        phoneControlService.callActions.onEach {
            val call = libPebble.currentCall.value
            when (it) {
                CallAction.Answer -> if (call is Call.AnswerableCall) {
                    call.answerCall()
                } else {
                    logger.e { "Watch requested answer call on unanswerable call type ${call?.let { it::class.simpleName }}" }
                }
                CallAction.Hangup -> if (call is Call.EndableCall) {
                    call.endCall()
                    libPebble.currentCall.value = null
                } else {
                    logger.e { "Watch requested hangup call on unendable call type ${call?.let { it::class.simpleName }}" }
                }
            }
        }.launchIn(watchScope)
        libPebble.currentCall.onEach {
            when (it) {
                is Call.RingingCall -> {
                    if (blockedByPhoneDnd(it, capabilities)) {
                        logger.d { "Not sending call to watch: blocked by phone Do Not Disturb" }
                        return@onEach
                    }
                    lastCookie = it.cookie
                    phoneControlService.send(PhoneControl.IncomingCall(
                        cookie = it.cookie,
                        callerNumber = it.contactNumber.take(31),
                        callerName = it.contactName?.take(31) ?: it.contactNumber.take(31)
                    ))
                }
                is Call.ActiveCall -> {
                    lastCookie = it.cookie
                    phoneControlService.send(PhoneControl.Start(cookie = it.cookie))
                }
                is Call.HoldingCall, is Call.DialingCall -> {/* no-op */}
                null -> {
                    lastCookie?.let { cookie ->
                        phoneControlService.send(PhoneControl.End(cookie = cookie))
                        lastCookie = null
                    }
                }
            }
        }.launchIn(watchScope)
    }

    private suspend fun blockedByPhoneDnd(call: Call.RingingCall, capabilities: Set<ProtocolCapsFlag>): Boolean {
        val config = libPebbleConfigFlow.value
        val respectDnd = if (watchReceivesWatchPrefs(capabilities, config)) {
            watchPrefs.watchPrefs.first()
                .first { it.pref == BoolWatchPref.RespectPhoneSilence }
                .valueOrDefault() == true
        } else {
            config.notificationConfig.respectDoNotDisturb
        }
        return respectDnd && !systemCallFilter.allowsCall(call.contactNumber)
    }
}
