package io.rebble.libpebblecommon.services.blobdb

import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.packets.blobdb.TimelineAction
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.services.ProtocolService
import io.rebble.libpebblecommon.util.TimelineAttributeFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

/**
 * Singleton that handles receiving of timeline actions.
 */
internal class TimelineService(private val protocolHandler: PebbleProtocolHandler) : ProtocolService {
    val actionInvocations: Flow<TimelineService.TimelineActionInvocation> = protocolHandler.inboundMessages
        .filterIsInstance<TimelineAction.InvokeAction>()
        .map {
            val actionId = it.actionID.get()
            val itemId = it.itemID.get()
            val attributes = it.attributes.list
            TimelineActionInvocation(itemId, actionId, attributes)
        }

    val displayedItems: Flow<Uuid?> = protocolHandler.inboundMessages
        .filterIsInstance<TimelineAction.DisplayedItem>()
        .map { it.itemID.get().takeIf { id -> id != TimelineAction.NOTHING_DISPLAYED } }

    inner class TimelineActionInvocation(val itemId: Uuid, val actionId: UByte, val attributes: List<TimelineItem.Attribute>) {
        suspend fun respond(response: TimelineActionResult) {
            val responsePacket = response.toPebblePacket()
            responsePacket.itemID.set(itemId)
            protocolHandler.send(responsePacket)
        }
    }
}

public class TimelineActionResult(public val success: Boolean, public val icon: TimelineIcon, public val title: String) {
    internal fun toPebblePacket(): TimelineAction.ActionResponse = TimelineAction.ActionResponse(
        Uuid.NIL,
        if (success) 0u else 1u,
        listOf(
            TimelineAttributeFactory.largeIcon(icon),
            TimelineAttributeFactory.subtitle(title)
        )
    )
}