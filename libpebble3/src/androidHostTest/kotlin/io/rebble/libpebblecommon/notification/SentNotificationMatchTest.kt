package io.rebble.libpebblecommon.notification

import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.LibPebbleNotification
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.toEntity
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.wasSentAs
import io.rebble.libpebblecommon.notification.NotificationDecision.NotSentDuplicate
import io.rebble.libpebblecommon.notification.NotificationDecision.SendToWatch
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SentNotificationMatchTest {

    private val postedAt = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun notification(
        body: String = "See you soon",
        messageKey: String? = null,
        imageAspect: UByte? = null,
        timestamp: Instant = postedAt,
    ) = LibPebbleNotification(
        packageName = "org.telegram.messenger",
        uuid = Uuid.random(),
        groupKey = null,
        key = "0|org.telegram.messenger|1|null|10001",
        timestamp = timestamp,
        title = "Alice",
        body = body,
        icon = TimelineIcon.NotificationTelegram,
        actions = emptyList(),
        people = emptyList(),
        vibrationPattern = null,
        previousUuids = emptyList(),
        messageKey = messageKey,
        imageAspect = imageAspect,
    )

    private fun LibPebbleNotification.sent() = toEntity(SendToWatch, channelId = null)

    @Test
    fun `replay of the same post matches`() {
        assertTrue(notification().wasSentAs(notification().sent()))
    }

    @Test
    fun `repost of the same message with a new post time matches`() {
        val sent = notification(messageKey = "1000|See you soon").sent()
        val repost = notification(messageKey = "1000|See you soon", timestamp = postedAt + 1.hours)
        assertTrue(repost.wasSentAs(sent))
    }

    @Test
    fun `new post without a message key does not match`() {
        val sent = notification().sent()
        assertFalse(notification(timestamp = postedAt + 5.minutes).wasSentAs(sent))
    }

    @Test
    fun `identical text in a new message does not match`() {
        val sent = notification(messageKey = "1000|See you soon").sent()
        assertFalse(notification(messageKey = "2000|See you soon").wasSentAs(sent))
    }

    @Test
    fun `repost that gains a photo does not match`() {
        val sent = notification(messageKey = "1000|Image").sent()
        assertFalse(notification(messageKey = "1000|Image", imageAspect = 9u).wasSentAs(sent))
    }

    @Test
    fun `different body does not match`() {
        assertFalse(notification(body = "Running late").wasSentAs(notification().sent()))
    }

    @Test
    fun `watch item id is only stored for sent notifications`() {
        val n = notification()
        assertEquals(n.uuid, n.sent().itemId)
        assertNull(n.toEntity(NotSentDuplicate, channelId = null).itemId)
    }
}
