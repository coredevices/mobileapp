package io.rebble.libpebblecommon.io.rebble.libpebblecommon.calls

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.calls.BlockedReason
import io.rebble.libpebblecommon.calls.MissedCall
import io.rebble.libpebblecommon.calls.SystemCallLog
import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds

internal class AndroidSystemCallLog(private val context: AppContext): SystemCallLog {
    private val scope = CoroutineScope(Dispatchers.Default)
    companion object {
        private val logger = Logger.withTag(AndroidSystemCallLog::class.simpleName!!)
    }
    private val handler = Handler(Looper.getMainLooper())

    override suspend fun getMissedCalls(start: Instant): List<MissedCall> {
        val missedCalls = mutableListOf<MissedCall>()
        val query = try {
            context.context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                buildList {
                    add(CallLog.Calls.NUMBER)
                    add(CallLog.Calls.CACHED_NAME)
                    add(CallLog.Calls.DATE)
                    add(CallLog.Calls.DURATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        add(CallLog.Calls.BLOCK_REASON)
                    }
                }.toTypedArray(),
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} > ?",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString(), start.toEpochMilliseconds().toString()),
                "${CallLog.Calls.DATE} DESC"
            )
        } catch (e: SecurityException) {
            // checkSelfPermission() can report granted while the CallLog query is still denied
            // (permission revoked mid-lifecycle, AppOps revocation). This runs in the flow body, so
            // an uncaught exception would crash the app - mirror registerForMissedCallChanges.
            logger.e(e) { "Failed to query missed calls" }
            return emptyList()
        }
        query?.use { cursor ->
            val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            if (numberIdx == -1) {
                logger.e { "Call log cursor does not contain number column." }
                return emptyList()
            }
            val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
            if (nameIdx == -1) {
                logger.e { "Call log cursor does not contain cached name column." }
            }
            val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
            if (dateIdx == -1) {
                logger.e { "Call log cursor does not contain date column." }
                return emptyList()
            }
            val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
            if (durationIdx == -1) {
                logger.e { "Call log cursor does not contain duration column." }
                return emptyList()
            }
            val blockReasonIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(CallLog.Calls.BLOCK_REASON)
            } else {
                null
            }
            if (blockReasonIdx == -1) {
                logger.e { "Call log cursor does not contain block reason column." }
            }

            while (cursor.moveToNext()) {
                val callerNumber = cursor.getString(numberIdx) ?: "Unknown"
                val callerName = cursor.getString(nameIdx) ?: null
                val timestamp = Instant.fromEpochMilliseconds(cursor.getLong(dateIdx))
                val duration = cursor.getLong(durationIdx).seconds
                val blockReason = if (blockReasonIdx != null) {
                    when (cursor.getInt(blockReasonIdx)) {
                        CallLog.Calls.BLOCK_REASON_NOT_BLOCKED -> BlockedReason.NotBlocked
                        CallLog.Calls.BLOCK_REASON_BLOCKED_NUMBER -> BlockedReason.BlockedNumber
                        CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE -> BlockedReason.CallScreening
                        CallLog.Calls.BLOCK_REASON_DIRECT_TO_VOICEMAIL -> BlockedReason.DirectToVoicemail
                        else -> BlockedReason.Other
                    }
                } else {
                    BlockedReason.NotBlocked
                }

                if (blockReason != BlockedReason.CallScreening) {
                    missedCalls.add(MissedCall(callerNumber, callerName, blockReason, timestamp, duration))
                } else {
                    logger.d { "Ignoring a missed call due to call screening." }
                }
            }
        }
        return missedCalls
    }

    override fun registerForMissedCallChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                if (selfChange) return
                if (uri != CallLog.Calls.CONTENT_URI) return
                trySend(Unit)
            }
        }
        val registeredObserver = try {
            context.context.contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                observer
            )
            observer
        } catch (e: SecurityException) {
            logger.e(e) { "Failed to register for missed calls" }
            null
        }

        awaitClose {
            registeredObserver?.let {
                try {
                    context.context.contentResolver.unregisterContentObserver(it)
                } catch (e: IllegalArgumentException) {
                    logger.w { "Failed to unregister content observer, it may not have been registered." }
                }
            }
        }
    }

    override fun hasPermission(): Boolean {
        return context.context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    }
}