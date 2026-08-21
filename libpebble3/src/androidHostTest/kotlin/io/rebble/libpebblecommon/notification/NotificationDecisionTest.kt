package io.rebble.libpebblecommon.notification

import io.rebble.libpebblecommon.NotificationConfig
import io.rebble.libpebblecommon.database.MillisecondInstant
import io.rebble.libpebblecommon.database.entity.ChannelItem
import io.rebble.libpebblecommon.database.entity.ContactEntity
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.database.entity.NotificationAppItem
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.LibPebbleNotification
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.decideNotification
import io.rebble.libpebblecommon.notification.NotificationDecision.NotSendChannelMuted
import io.rebble.libpebblecommon.notification.NotificationDecision.NotSendContactMuted
import io.rebble.libpebblecommon.notification.NotificationDecision.NotSentAppMuted
import io.rebble.libpebblecommon.notification.NotificationDecision.NotSentDuplicate
import io.rebble.libpebblecommon.notification.NotificationDecision.NotSentEmpty
import io.rebble.libpebblecommon.notification.NotificationDecision.NotSentLocalOnly
import io.rebble.libpebblecommon.notification.NotificationDecision.NotSentRuleFiltered
import io.rebble.libpebblecommon.notification.NotificationDecision.NotSentScreenOn
import io.rebble.libpebblecommon.notification.NotificationDecision.SendToWatch
import io.rebble.libpebblecommon.notification.processor.NotificationProperties
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val DEFAULT_PKG = "com.example.app"

class NotificationDecisionTest {

    private fun appEntry(
        packageName: String = DEFAULT_PKG,
        muteState: MuteState = MuteState.Never,
        allowDuplicates: Boolean = false,
    ) = NotificationAppItem(
        packageName = packageName,
        name = packageName,
        muteState = muteState,
        channelGroups = emptyList(),
        stateUpdated = MillisecondInstant(Instant.fromEpochMilliseconds(0)),
        lastNotified = MillisecondInstant(Instant.fromEpochMilliseconds(0)),
        vibePatternName = null,
        colorName = null,
        iconCode = null,
        allowDuplicates = allowDuplicates,
    )

    private fun channel(muteState: MuteState) = ChannelItem(
        id = "ch",
        name = "Channel",
        muteState = muteState,
    )

    private fun contact(muteState: MuteState) = ContactEntity(
        lookupKey = "$muteState",
        name = "Contact",
        muteState = muteState,
        vibePatternName = null,
    )

    private fun notification(
        packageName: String = DEFAULT_PKG,
        title: String? = "Title",
        body: String? = "Body",
        key: String = "k:$packageName:${title ?: ""}:${body ?: ""}",
        messageKey: String? = null,
        imageAspect: UByte? = null,
    ) = LibPebbleNotification(
        messageKey = messageKey,
        imageAspect = imageAspect,
        packageName = packageName,
        uuid = Uuid.random(),
        groupKey = null,
        key = key,
        timestamp = Instant.fromEpochMilliseconds(0),
        title = title,
        body = body,
        icon = TimelineIcon.NotificationReminder,
        actions = emptyList(),
        people = emptyList(),
        vibrationPattern = null,
        previousUuids = emptyList(),
    )

    private suspend fun decide(
        notification: LibPebbleNotification,
        inflight: Collection<LibPebbleNotification> = emptyList(),
        appProperties: NotificationProperties? = null,
        config: NotificationConfig = NotificationConfig(),
        appEntry: NotificationAppItem = appEntry(notification.packageName),
        channel: ChannelItem? = null,
        isLocalOnly: Boolean = false,
        isRuleFiltered: Boolean = false,
        screenIsOnAndUnlocked: Boolean = false,
    ) = decideNotification(
        notification = notification,
        appEntry = appEntry,
        channel = channel,
        appProperties = appProperties,
        inflightNotifications = inflight,
        notificationConfig = config,
        isLocalOnly = isLocalOnly,
        isRuleFiltered = { isRuleFiltered },
        screenIsOnAndUnlocked = { screenIsOnAndUnlocked },
    )

    @Test
    fun `duplicate from non-Ring app is blocked`() = runTest {
        val first = notification()
        val second = notification()
        assertEquals(NotSentDuplicate, decide(second, inflight = listOf(first)))
    }

    @Test
    fun `consecutive photos with the same placeholder text are not duplicates`() = runTest {
        // Messaging apps title every attachment with the sender and body them "Image", and two
        // photos can easily share an aspect, so only the message identity tells them apart.
        val first = notification(body = "Image", messageKey = "1000|Image", imageAspect = 12u)
        val second = notification(body = "Image", messageKey = "2000|Image", imageAspect = 12u)
        assertEquals(SendToWatch, decide(second, inflight = listOf(first)))
    }

    @Test
    fun `repost of the same photo is a duplicate`() = runTest {
        // Some apps hand out a fresh content URI for the same photo on every re-post, so nothing
        // about the image itself can be part of the identity.
        val first = notification(body = "Image", messageKey = "1000|Image", imageAspect = 9u)
        val second = notification(body = "Image", messageKey = "1000|Image", imageAspect = 9u)
        assertEquals(NotSentDuplicate, decide(second, inflight = listOf(first)))
    }

    @Test
    fun `repost that gains a photo is sent`() = runTest {
        val first = notification(body = "Image", messageKey = "1000|Image", imageAspect = null)
        val second = notification(body = "Image", messageKey = "1000|Image", imageAspect = 9u)
        assertEquals(SendToWatch, decide(second, inflight = listOf(first)))
    }

    @Test
    fun `duplicate from Ring app is allowed through`() = runTest {
        val first = notification("com.ringapp", "There is a Person", "at your Front door")
        val second = notification("com.ringapp", "There is a Person", "at your Front door")
        assertEquals(
            SendToWatch,
            decide(
                second,
                inflight = listOf(first),
                appProperties = NotificationProperties.Ring,
                appEntry = appEntry("com.ringapp", allowDuplicates = true),
            ),
        )
    }

    @Test
    fun `non-duplicate from Ring app is sent`() = runTest {
        val first = notification("com.ringapp", "Person at Front Door", "")
        val second = notification("com.ringapp", "Motion at Driveway", "")
        assertEquals(
            SendToWatch,
            decide(
                second,
                inflight = listOf(first),
                appProperties = NotificationProperties.Ring,
                appEntry = appEntry("com.ringapp", allowDuplicates = true),
            ),
        )
    }

    @Test
    fun `unique notification with no inflight is sent`() = runTest {
        assertEquals(SendToWatch, decide(notification()))
    }

    @Test
    fun `notification with no title or body is blocked`() = runTest {
        assertEquals(NotSentEmpty, decide(notification(title = null, body = null)))
        assertEquals(NotSentEmpty, decide(notification(title = " ", body = "")))
    }

    @Test
    fun `notification with only a title is sent`() = runTest {
        assertEquals(SendToWatch, decide(notification(body = null)))
    }

    @Test
    fun `contact muted notification is blocked`() = runTest {
        val n = notification().copy(people = listOf(contact(MuteState.Always)))
        assertEquals(NotSendContactMuted, decide(n))
    }

    @Test
    fun `local-only notification is blocked by default`() = runTest {
        assertEquals(NotSentLocalOnly, decide(notification(), isLocalOnly = true))
    }

    @Test
    fun `local-only notification is sent when global config opts in`() = runTest {
        val config = NotificationConfig(sendLocalOnlyNotifications = true)
        assertEquals(SendToWatch, decide(notification(), isLocalOnly = true, config = config))
    }

    @Test
    fun `local-only notification is sent when app properties opt in`() = runTest {
        val n = notification(packageName = "com.google.android.apps.messaging")
        assertEquals(
            SendToWatch,
            decide(n, isLocalOnly = true, appProperties = NotificationProperties.GoogleMessaging),
        )
    }

    @Test
    fun `app-muted notification is blocked`() = runTest {
        assertEquals(
            NotSentAppMuted,
            decide(notification(), appEntry = appEntry(muteState = MuteState.Always)),
        )
    }

    @Test
    fun `starred contact bypasses app mute`() = runTest {
        val n = notification().copy(people = listOf(contact(MuteState.Exempt)))
        assertEquals(SendToWatch, decide(n, appEntry = appEntry(muteState = MuteState.Always)))
    }

    @Test
    fun `channel-muted notification is blocked`() = runTest {
        assertEquals(NotSendChannelMuted, decide(notification(), channel = channel(MuteState.Always)))
    }

    @Test
    fun `starred contact bypasses channel mute`() = runTest {
        val n = notification().copy(people = listOf(contact(MuteState.Exempt)))
        assertEquals(SendToWatch, decide(n, channel = channel(MuteState.Always)))
    }

    @Test
    fun `rule-filtered notification is blocked`() = runTest {
        assertEquals(NotSentRuleFiltered, decide(notification(), isRuleFiltered = true))
    }

    @Test
    fun `screen-on blocks when alwaysSendNotifications is false`() = runTest {
        val config = NotificationConfig(alwaysSendNotifications = false)
        assertEquals(NotSentScreenOn, decide(notification(), screenIsOnAndUnlocked = true, config = config))
    }

    @Test
    fun `screen-on does not block when alwaysSendNotifications is true`() = runTest {
        assertEquals(SendToWatch, decide(notification(), screenIsOnAndUnlocked = true))
    }

    @Test
    fun `pebble test notification bypasses screen-on check`() = runTest {
        val n = notification("coredevices.coreapp", "Test Notification", body = null)
        val config = NotificationConfig(alwaysSendNotifications = false)
        assertEquals(SendToWatch, decide(n, screenIsOnAndUnlocked = true, config = config))
    }
}
