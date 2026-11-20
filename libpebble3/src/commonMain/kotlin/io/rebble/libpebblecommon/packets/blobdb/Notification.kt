package io.rebble.libpebblecommon.packets.blobdb

import coredev.BlobDatabase
import io.rebble.libpebblecommon.util.TimelineAttributeFactory
import kotlin.time.Clock
import kotlin.random.Random
import kotlin.uuid.Uuid

private val notifsUUID = Uuid.parse("B2CAE818-10F8-46DF-AD2B-98AD2254A3C1")

internal enum class NotificationSource(val id: UInt) { //TODO: There's likely more... (probably fw >3)
    Generic(1u),
    Twitter(6u),
    Facebook(11u),
    Email(19u),
    SMS(45u),
}

/**
 * Helper class to generate a BlobDB command that inserts a notification
 */
//open class PushNotification(subject: String, sender: String? = null, message: String? = null, source: NotificationSource = NotificationSource.Generic, backgroundColor: UByte? = null): BlobCommand.InsertCommand(Random.nextInt(0, UShort.MAX_VALUE.toInt()).toUShort(),
//    BlobDatabase.Notification, ubyteArrayOf(), ubyteArrayOf()) {
//    init {
//        val itemID = Uuid.random()
//
//        //TODO: Replies, open on phone, detect dismiss
//        val attributes = mutableListOf(
//            TimelineAttributeFactory.sender(sender ?: ""),
//            TimelineAttributeFactory.icon(TimelineIcon.fromId(source.id))
//        )
//        if (message != null) attributes += TimelineAttributeFactory.body(message)
//        attributes += TimelineAttributeFactory.subtitle(subject)
//
//        if (backgroundColor != null) {
//            // XXX: https://youtrack.jetbrains.com/issue/KT-49366
//            val bgColTemp = backgroundColor.toUByte()
//            attributes += TimelineAttributeFactory.primaryColor(bgColTemp)
//        }
//
//        val actions = mutableListOf(
//            TimelineItem.Action(
//                0u, TimelineActionType.Dismiss, mutableListOf(
//                    TimelineItem.Attribute(
//                        0x01u,
//                        "Dismiss".encodeToByteArray().toUByteArray()
//                    )
//                )
//            )
//        )
//
//        val timestampSecs = Clock.System.now().epochSeconds
//
//        val notification = TimelineItem(
//            itemID,
//            Uuid.NIL,
//            timestampSecs.toUInt(),
//            0u,
//            TimelineItemType.Notification,
//            0u,
//            TimelineItemLayout.GenericPin,
//            attributes,
//            actions
//        )
//        super.targetKey.set(notification.itemId.toBytes(), notification.itemId.size)
//        super.keySize.set(super.targetKey.size.toUByte())
//        val nbytes = notification.toBytes()
//        super.targetValue.set(nbytes, nbytes.size)
//        super.valSize.set(super.targetValue.size.toUShort())
//    }
//}