package io.rebble.libpebblecommon.pebblekit.two

import android.content.Context
import io.rebble.libpebblecommon.datalogging.Datalogging
import io.rebble.libpebblecommon.datalogging.ThirdPartyDatalogEvent
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.disk.pbw.PbwApp
import io.rebble.libpebblecommon.locker.Locker
import io.rebble.libpebblecommon.locker.LockerPBWCache
import io.rebble.pebblekit2.common.model.DataLogSession
import io.rebble.pebblekit2.common.model.ReceiveResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import io.rebble.pebblekit2.server.DefaultPebbleListenerConnector
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * Delivers third-party data logging sessions to companion apps over PebbleKit 2. Not tied to a
 * running watchapp: spooled data can arrive on reconnect while no watchapp is open.
 */
class PebbleKit2Datalogging(
    private val context: Context,
    private val datalogging: Datalogging,
    private val locker: Locker,
    private val lockerPBWCache: LockerPBWCache,
    private val coroutineScope: LibPebbleCoroutineScope,
) {
    private val delivery = DatalogDelivery(coroutineScope, ::senderFor)

    fun init() {
        coroutineScope.launch {
            datalogging.thirdPartyEvents.collect { delivery.enqueue(it) }
        }
    }

    private suspend fun senderFor(uuid: Uuid): DatalogSender? {
        val packages = companionPackagesFor(uuid)
        if (packages.isEmpty()) return null
        val connector = DefaultPebbleListenerConnector(context, packages)
        return object : DatalogSender {
            override suspend fun sendBatch(event: ThirdPartyDatalogEvent.Batch): Boolean {
                val result = connector.sendOnDataLogReceived(
                    event.uuid.toJavaUuid(),
                    event.toDataLogSession(),
                    event.data,
                    event.itemsLeft.toLong(),
                    WatchIdentifier(event.watchSerial),
                )
                return result == ReceiveResult.Ack
            }

            override suspend fun sendFinished(event: ThirdPartyDatalogEvent.Finished): Boolean {
                val result = connector.sendOnDataLogSessionFinished(
                    event.uuid.toJavaUuid(),
                    event.toDataLogSession(),
                    WatchIdentifier(event.watchSerial),
                )
                return result == ReceiveResult.Ack
            }

            override fun close() = connector.close()
        }
    }

    private suspend fun companionPackagesFor(uuid: Uuid): List<String> {
        val entry = locker.getApp(uuid) ?: return emptyList()
        val pbwPath = lockerPBWCache.getPBWFileForApp(entry.id, entry.version, locker)
        return PbwApp(pbwPath).info.companionApp?.android?.apps.orEmpty().mapNotNull { it.pkg }
    }
}

private fun ThirdPartyDatalogEvent.toDataLogSession() = DataLogSession(
    tag = tag.toLong(),
    timestamp = Instant.fromEpochSeconds(timestamp.toLong()),
    itemSize = itemSize.toInt(),
)
