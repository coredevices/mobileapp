package io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification

import android.app.KeyguardManager
import android.app.Notification
import android.app.Notification.Action
import android.app.Notification.WearableExtender
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.service.notification.StatusBarNotification
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.NotificationConfigFlow
import io.rebble.libpebblecommon.connection.endpointmanager.blobdb.TimeProvider
import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.dao.NotificationAppRealDao
import io.rebble.libpebblecommon.database.dao.NotificationDao
import io.rebble.libpebblecommon.database.entity.ChannelItem
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.database.entity.NotificationAppItem
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.notification.NotificationDecision
import io.rebble.libpebblecommon.notification.NotificationDecision.NotSendChannelMuted
import io.rebble.libpebblecommon.notification.NotificationDecision.NotSendContactMuted
import io.rebble.libpebblecommon.notification.NotificationDecision.NotSentAppMuted
import io.rebble.libpebblecommon.notification.NotificationDecision.NotSentDuplicate
import io.rebble.libpebblecommon.notification.NotificationDecision.NotSentLocalOnly
import io.rebble.libpebblecommon.notification.NotificationDecision.SendToWatch
import io.rebble.libpebblecommon.util.PrivateLogger
import io.rebble.libpebblecommon.util.obfuscate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

class NotificationHandler(
    private val notificationProcessors: Set<NotificationProcessor>,
    private val notificationAppDao: NotificationAppRealDao,
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val timeProvider: TimeProvider,
    private val notificationConfig: NotificationConfigFlow,
    private val privateLogger: PrivateLogger,
    private val notificationDao: NotificationDao,
    private val context: Context,
) {
    companion object {
        private val logger = Logger.withTag("NotificationHandler")
    }

    private val verboseLogging: Boolean = true
    private val inflightNotifications = ConcurrentHashMap<String, LibPebbleNotification>()
    val notificationSendQueue = Channel<LibPebbleNotification>(Channel.BUFFERED)
    val notificationDeleteQueue = Channel<Uuid>(Channel.BUFFERED)
    private val notificationsToProcess = Channel<StatusBarNotification>(Channel.BUFFERED)
    private val _notificationServiceBound = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val notificationServiceBound = _notificationServiceBound.asSharedFlow()

    fun init() {
        notificationsToProcess.consumeAsFlow().onEach {
            val notification = processNotification(it) ?: return@onEach
            sendNotification(notification)
        }.launchIn(libPebbleCoroutineScope)
    }

    fun onServiceBound() {
        _notificationServiceBound.tryEmit(Unit)
    }

    fun getNotificationAction(itemId: Uuid, actionId: UByte): LibPebbleNotificationAction? {
        val notification = getNotification(itemId)
        return notification?.actions?.get(actionId.toInt())
    }

    fun getNotification(itemId: Uuid): LibPebbleNotification? {
        return inflightNotifications.values.firstOrNull { it.uuid == itemId }
    }

    private val _channelChanged = MutableSharedFlow<Unit>()
    val channelChanged = _channelChanged.asSharedFlow()

    fun onChannelChanged() {
        libPebbleCoroutineScope.launch {
            _channelChanged.emit(Unit)
        }
    }

    private fun verboseLog(message: () -> String) {
        if (verboseLogging) {
            logger.v { message() }
        }
    }

    private fun NotificationAppItem.getChannelFor(sbn: StatusBarNotification): ChannelItem? {
        val channelId = sbn.notification.channelId ?: return null
        return channelGroups.flatMap { it.channels }.find { it.id == channelId }
    }

    private suspend fun processNotification(sbn: StatusBarNotification): LibPebbleNotification? {
        // Don't even check (or persist) ongoing/group summary notifications
        if (sbn.isOngoing) {
            verboseLog {
                "Ignoring ongoing notification from ${sbn.packageName.obfuscate(privateLogger)}"
            }
            return null
        }
        if (sbn.notification.isGroupSummary()) {
            verboseLog {
                "Ignoring group summary notification from ${sbn.packageName.obfuscate(privateLogger)}"
            }
            return null
        }
        val appEntry = notificationAppDao.getEntry(sbn.packageName)
        // Don't do any further processing if we don't know the app
        if (appEntry == null) {
            verboseLog {
                "Ignoring unknown (maybe system) app notification from ${
                    sbn.packageName.obfuscate(
                        privateLogger
                    )
                }"
            }
            return null
        }
        notificationAppDao.insertOrReplace(
            appEntry.copy(
                lastNotified = timeProvider.now().asMillisecond()
            )
        )
        val channel = appEntry.getChannelFor(sbn)
        val result = extractNotification(sbn, appEntry, channel)
        if (notificationConfig.value.dumpNotificationContent) {
            sbn.dump(result)
        }
        val notification = when (result) {
            is NotificationResult.Extracted -> result.notification
            NotificationResult.NotProcessed -> {
                verboseLog {
                    "Ignoring notification from ${sbn.packageName.obfuscate(privateLogger)} (not extracted)"
                }
                return null
            }
        }
        val anyContactMuted = notification.people.any { it.muteState == MuteState.Always }
        val anyContactStarred = notification.people.any { it.muteState == MuteState.Exempt }
        val decision = when {
            sbn.notification.isLocalOnly() && !notificationConfig.value.sendLocalOnlyNotifications ->
                NotSentLocalOnly
            anyContactMuted -> NotSendContactMuted
            !anyContactStarred && appEntry.muteState == MuteState.Always -> NotSentAppMuted
            !anyContactStarred && (channel != null && channel.muteState == MuteState.Always) -> NotSendChannelMuted
            inflightNotifications.values.any { it.displayDataEquals(notification) } -> NotSentDuplicate
            !notificationConfig.value.alwaysSendNotifications && !notification.isPebbleTestNotification() && screenIsOnAndUnlocked() -> NotificationDecision.NotSentScreenOn
            else -> result.decision
        }
        val storeNotification = when {
            notificationConfig.value.storeNotifiationsForDays == 0 -> false
            notificationConfig.value.storeDisabledNotifications -> true
            decision == NotSentAppMuted -> false
            decision == NotSendChannelMuted -> false
            decision == NotSendContactMuted -> false
            else -> true
        }
        if (storeNotification) {
            notificationDao.insert(notification.toEntity(decision, channel?.id))
        }
        if (decision != SendToWatch) {
            verboseLog { "Not sending notification from ${sbn.packageName.obfuscate(privateLogger)} because $decision" }
            return null
        }
        return notification
    }

    private fun screenIsOnAndUnlocked(): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        val isScreenOn = powerManager.isInteractive
        val isDeviceLocked = keyguardManager.isKeyguardLocked
        return isScreenOn && !isDeviceLocked
    }

    private suspend fun extractNotification(
        sbn: StatusBarNotification,
        app: NotificationAppItem,
        channel: ChannelItem?,
    ): NotificationResult {
        for (processor in notificationProcessors) {
            try {
                when (val result = processor.extractNotification(sbn, app, channel)) {
                    is NotificationResult.Extracted -> {
                        verboseLog { "Notification from ${sbn.packageName.obfuscate(privateLogger)} extracted by ${processor::class.simpleName}" }
                        return result
                    }

                    is NotificationResult.NotProcessed -> Unit
                }
            } catch (e: Exception) {
                logger.e(e) {
                    "Error processing notification from ${sbn.packageName.obfuscate(privateLogger)}"
                }
            }
        }
        return NotificationResult.NotProcessed
    }

//    fun setActiveNotifications(notifications: List<StatusBarNotification>) =
//        libPebbleCoroutineScope.launch {
//            val inflightSnapshot = inflightNotifications.toMap()
//            val newNotifs = notifications.mapNotNull { sbn ->
//                if (inflightSnapshot.any { it.key == sbn.key }) {
//                    return@mapNotNull null
//                }
//                val notification = processNotification(sbn) ?: return@mapNotNull null
//                // Check if the notification is already in the list
//                if (inflightSnapshot.values.any { it.displayDataEquals(notification) }) {
//                    return@mapNotNull null
//                }
//                notification
//            }
//            newNotifs.forEach {
//                sendNotification(it)
//            }
//        }

    private fun sendNotification(notification: LibPebbleNotification) {
        inflightNotifications[notification.key] = notification
        notificationSendQueue.trySend(notification).also {
            if (it.isFailure) {
                logger.w { "Couldn't write notification to send queue" }
            }
        }
    }

    fun handleNotificationPosted(sbn: StatusBarNotification) {
        logger.d { "onNotificationPosted(${sbn.packageName.obfuscate(privateLogger)})  ($this)" }
        notificationsToProcess.trySend(sbn).also {
            if (it.isFailure) {
                logger.w { "Couldn't write notification to processing queue" }
            }
        }
    }

    fun handleNotificationRemoved(sbn: StatusBarNotification) {
        logger.d { "onNotificationRemoved(${sbn.packageName.obfuscate(privateLogger)})  ($this)" }
        val inflight = inflightNotifications[sbn.key]
        if (inflight != null) {
            inflightNotifications.remove(sbn.key)
            notificationDeleteQueue.trySend(inflight.uuid).also {
                if (it.isFailure) {
                    logger.w { "Couldn't write notification to deletion queue" }
                }
            }
        } else {
            logger.d { "Failed to remove notification: key=${sbn.key.obfuscate(privateLogger)} not found in inflight" }
        }
    }

    private fun StatusBarNotification.dump(result: NotificationResult) {
        val wearableExtender = WearableExtender(notification)
        val wearableBundle = notification.extras.getBundle(EXTRA_WEARABLE_BUNDLE)
        logger.v {
            """
New notification:
    id = $id
    key = ${key.obfuscate(privateLogger)}
    groupKey = ${groupKey.obfuscate(privateLogger)}
    postTime = $postTime
    tag = ${tag.obfuscate(privateLogger)}
    pkg = ${packageName.obfuscate(privateLogger)}
    user = $user
    ongoing = $isOngoing
    when = ${notification.`when`}
    number = ${notification.number}
    tickerText = ${notification.tickerText.obfuscate(privateLogger)}
    color = ${notification.color}
    visibility = ${notification.visibility}
    category = ${notification.category}
    groupKey(n) = ${notification.group.obfuscate(privateLogger)}
    flags = ${notification.flags}
    isGroupSummary = ${notification.isGroupSummary()}
    isLocalOnly = ${notification.isLocalOnly()}
    channelId = ${notification.dumpChannel().obfuscate(privateLogger)}
    groupAlertBehavior = ${notification.dumpGroupAlertBehaviour()}
    people: ${notification.people().joinToString { it.dump(privateLogger) }}
    contacts: ${result.notification()?.people}
    messagingUser: ${notification.messagingUser()?.dump(privateLogger)}
    extras: ${notification.extras.dump(8)}
    actions = ${notification.actions?.asList()?.dump()}
    WearableExtender actions: ${wearableExtender.actions?.dump()}
    WearableExtender extras: ${wearableBundle?.dump(8)}
Processed as:
    title = ${result.notification()?.title.obfuscate(privateLogger)}
    body = ${result.notification()?.body.obfuscate(privateLogger)}
        """.trimIndent()
        }
    }

    private fun Notification.dumpChannel(): String {
        return channelId ?: "<null>"
    }

    private fun Notification.dumpGroupAlertBehaviour(): String? {
        return when (groupAlertBehavior) {
            Notification.GROUP_ALERT_ALL -> "GROUP_ALERT_ALL"
            Notification.GROUP_ALERT_CHILDREN -> "GROUP_ALERT_CHILDREN"
            Notification.GROUP_ALERT_SUMMARY -> "GROUP_ALERT_SUMMARY"
            else -> "Unknown"
        }
    }

    private fun Bundle.dump(indent: Int): String {
        val newlineIndent = "\n${" ".repeat(indent)}"
        return keySet().joinToString(prefix = newlineIndent, separator = newlineIndent) {
            try {
                val value = get(it)
                when {
                    value is CharSequence || it in EXTRA_KEYS_NON_STRING_SENSITIVE -> "$it = ${
                        value.toString().obfuscate(privateLogger)
                    }"

                    else -> "$it = ${get(it)}"
                }
            } catch (_: Exception) {
                "$it = unknown (crashed)"
            }
        }
    }

    private fun Collection<Action>?.dump(): String {
        return this?.joinToString(prefix = "\n", separator = "\n") { action ->
            """        Action:
            title = ${action.title}
            showUserInterface = ${action.showsUserInterface()}
            extras: ${action.extras.dump(16)}
            remoteInputs: ${action.remoteInputs.dump(16)}"""
        } ?: "[]"
    }

    private fun Array<RemoteInput>?.dump(indent: Int): String {
        if (this == null) return "null"
        val newlineIndent = "\n${" ".repeat(indent)}"
        return this.joinToString(prefix = newlineIndent, separator = newlineIndent) { remoteInput ->
            """
        RemoteInput:
                        label = ${remoteInput.label}
                        allowFreeFormInput = ${remoteInput.allowFreeFormInput}
                        isDataOnly = ${remoteInput.dumpDataOnly()}
                """.trimIndent()
        }
    }
}

private fun LibPebbleNotification.isPebbleTestNotification(): Boolean = packageName == "coredevices.coreapp" &&
        title == "Test Notification"

private const val ACTION_KEY_SHOWS_USER_INTERFACE = "android.support.action.showsUserInterface"
private const val EXTRA_WEARABLE_BUNDLE = "android.wearable.EXTENSIONS"
private val EXTRA_KEYS_NON_STRING_SENSITIVE =
    setOf("argAndroidAccount", "android.appInfo", "gif_uri_list", "android.largeIcon")

fun Notification.isGroupSummary(): Boolean = (flags and Notification.FLAG_GROUP_SUMMARY) != 0
fun Notification.isLocalOnly(): Boolean = (flags and Notification.FLAG_LOCAL_ONLY) != 0
fun RemoteInput.dumpDataOnly(): Boolean? {
    return isDataOnly
}

fun Person.dump(privateLogger: PrivateLogger): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    "[Person: ${privateLogger.obfuscate(name)} / ${privateLogger.obfuscate(uri)} / $key]"
} else ""

fun Notification.people(): List<Person> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    extras.getParcelableArrayList<Person>("android.people.list")?.toList() ?: emptyList()
} else emptyList()

fun Notification.vibrationPattern(): List<UInt>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    extras.getIntArray("android.vibrationPattern.list")?.toList()?.map(Int::toUInt)
} else null

fun Notification.messagingUser(): Person? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    extras.getParcelable("android.messagingUser")
} else null

fun Action.showsUserInterface(): Boolean = extras.getBoolean(ACTION_KEY_SHOWS_USER_INTERFACE, false)
