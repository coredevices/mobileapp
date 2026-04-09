package coredevices.ring.service

import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RecordingEntryStatus
import coredevices.indexai.database.dao.ConversationMessageDao
import coredevices.mcp.data.SemanticResult
import coredevices.ring.data.IndexTimestamp
import coredevices.ring.data.InflightIndexNotification
import coredevices.ring.data.NoteShortcutType
import coredevices.ring.data.entity.room.RingTransfer
import coredevices.ring.data.entity.room.RingTransferStatus
import coredevices.ring.database.Preferences
import coredevices.ring.database.room.repository.RecordingRepository
import coredevices.ring.database.room.repository.RingTransferRepository
import coredevices.ring.ui.UITimeUtil
import coredevices.ring.ui.components.chat.actionText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.Instant

data class GenericNotification(
    val id: Int,
    val title: String,
    val contentText: String? = null,
    val inProgress: NotificationProgress? = null,
    val localOnly: Boolean = false,
    val deepLink: String? = null,
    val actions: List<NotificationAction> = emptyList()
)

data class NotificationAction(
    val title: String,
    val deepLink: String
)

sealed interface NotificationProgress {
    object Indeterminate: NotificationProgress
    data class Determinate(val progress: Float): NotificationProgress
}

expect class PlatformIndexNotificationManager{
    fun notify(notification: GenericNotification)
    fun cancel(notificationId: Int)
}

class IndexNotificationManager(
    private val recordingRepo: RecordingRepository,
    private val ringTransferRepo: RingTransferRepository,
    private val conversationMessageDao: ConversationMessageDao,
    private val platformIndexNotificationManager: PlatformIndexNotificationManager,
    private val prefs: Preferences,
) {
    companion object {
        private val logger = Logger.withTag("IndexNotificationManager")
        private const val DEEP_LINK_URI = "pebble://navbar/index"
        private val BUG_REPORT_DEBOUNCE = 1.minutes
    }
    private val inflightNotificationJobs = mutableMapOf<Long, Job?>()
    private val inflightNotifications = mutableMapOf<Long, InflightIndexNotification>()
    private var lastBugReportPrompt: Instant? = null

    private suspend fun makeInflightNotification(notifId: Int, transfer: RingTransfer, entry: RecordingEntryEntity?): InflightIndexNotification {
        val remoteTimestamp = transfer.transferInfo?.buttonPressed?.let { Instant.fromEpochMilliseconds(it) }
        val timestamp = IndexTimestamp(
            remoteTimestamp ?: transfer.createdAt,
            if (remoteTimestamp != null) IndexTimestamp.Source.RemoteDevice else IndexTimestamp.Source.LocalDevice
        )

        when (transfer.status) {
            RingTransferStatus.Started -> {
                return InflightIndexNotification.Transferring(notifId, timestamp)
            }
            RingTransferStatus.Discarded -> {
                return InflightIndexNotification.Discarded(notifId, timestamp)
            }
            RingTransferStatus.Failed -> {
                return InflightIndexNotification.Error(notifId, timestamp, "Transfer failed")
            }
            else -> {} // Continue to process
        }

        when(entry?.status) {
            null -> {
                return InflightIndexNotification.Transferring(notifId, timestamp)
            }
            RecordingEntryStatus.pending -> {
                return InflightIndexNotification.Transcribing(notifId, timestamp)
            }
            RecordingEntryStatus.agent_processing -> {
                return InflightIndexNotification.AgentRunning(
                    notifId,
                    timestamp,
                    entry.transcription ?: "<No text>"
                )
            }
            RecordingEntryStatus.agent_error -> {
                return InflightIndexNotification.Error(notifId, timestamp, entry.error ?: "Agent processing error")
            }
            RecordingEntryStatus.transcription_error -> {
                return InflightIndexNotification.Error(notifId, timestamp, "Transcription error")
            }
            RecordingEntryStatus.completed -> {} // Continue to process
        }

        val localRecording = transfer.recordingId?.let { recordingRepo.getRecording(it) } ?:
            return InflightIndexNotification.Transferring(notifId, timestamp)

        val messages = conversationMessageDao.getMessagesForRecording(localRecording.id).first()
        val lastMessageRole = conversationMessageDao.getLastMessageForRecording(localRecording.id).first()?.document?.role
        val actions = messages
            .filter { it.document.role == MessageRole.tool }
            .mapNotNull { it.document.semantic_result }

        return when {
            (lastMessageRole ?: MessageRole.user) == MessageRole.assistant || actions.any { it !is SemanticResult.SupportingData || !it.assistiveOnly } -> {
                val rxComplete = transfer.transferInfo?.transferCompleted
                val pressToRxLatency = if (remoteTimestamp != null && rxComplete != null) {
                    Instant.fromEpochMilliseconds(rxComplete) - remoteTimestamp
                } else {
                    null
                }

                if (actions.isEmpty()) {
                    InflightIndexNotification.AgentRunning(
                        notifId,
                        timestamp,
                        entry.transcription ?: "<No text>"
                    )
                } else {
                    InflightIndexNotification.AgentComplete(
                        notifId,
                        timestamp,
                        localRecording.id,
                        entry.transcription ?: "<No text>",
                        pressToRxLatency,
                        actions,
                        prefs.noteShortcut.value
                    )
                }

            }
            else -> {
                InflightIndexNotification.AgentRunning(
                    notifId,
                    timestamp,
                    entry.transcription ?: "<No text>"
                )
            }
        }
    }
    private fun buildNotifTimestamp(notification: InflightIndexNotification): String {
        return buildString {
            append("Pressed: Today at ")
            append(
                notification.pressedTimestamp.timestamp.toLocalDateTime(TimeZone.currentSystemDefault()).format(
                    LocalDateTime.Format {
                        amPmHour()
                        char(':')
                        minute()
                        char(':')
                        second()
                        char(' ')
                        amPmMarker("AM", "PM")
                    }
                )
            )
            if (prefs.debugDetailsEnabled.value) {
                append(" (${notification.pressedTimestamp.source})")
            }
        }
    }
    private fun nextNotificationId() = (inflightNotifications.values.maxByOrNull { it.id }?.id?.plus(1) ?: (10 + 1))
    @OptIn(FlowPreview::class)
    private suspend fun startNotificationJobFor(transfer: RingTransfer, scope: CoroutineScope) {
        if (inflightNotificationJobs.containsKey(transfer.id)) {
            logger.d { "Notification job already exists for recording ${transfer.id}" }
            return
        }

        val id = transfer.id
        inflightNotificationJobs[id] = ringTransferRepo.getTransferWithFeedItemFlow(transfer.id).filterNotNull().flatMapLatest {
            val conv = it.ringTransfer?.recordingId?.let {conversationMessageDao.getMessagesForRecording(it) } ?: emptyFlow()
            combine(
                flowOf(it),
                conv
            ) { transfer, _ ->
                transfer
            }
        }.mapNotNull { (transfer, rec) ->
            transfer ?: return@mapNotNull null

            val lastEntry = rec?.entry
            val notif = makeInflightNotification(
                inflightNotifications[id]?.id ?: nextNotificationId(),
                transfer,
                lastEntry
            )
            if (!prefs.debugDetailsEnabled.value) {
                // Mute non-final notifications when not in debug mode
                when (notif) {
                    is InflightIndexNotification.Transferring -> return@mapNotNull null
                    is InflightIndexNotification.Transcribing -> return@mapNotNull null
                    is InflightIndexNotification.AgentRunning -> return@mapNotNull null
                    else -> {}
                }
            }
            logger.d { "(Job ID ${currentCoroutineContext()[Job]?.hashCode()?.toHexString()}) Created notification for transfer ${transfer.id}: $notif" }
            inflightNotifications[transfer.id] = notif

            notif
        }
            .distinctUntilChanged()
            .debounce(100)
            .onEach { notification ->
                logger.d { "(Job ID ${currentCoroutineContext()[Job]?.hashCode()?.toHexString()}) Handling notification for transfer ${transfer.id}: $notification" }
                val notif = when (notification) {
                    is InflightIndexNotification.Discarded -> null
                    is InflightIndexNotification.Transferring -> {
                        GenericNotification(
                            id = notification.id,
                            title = "Transferring recording",
                            inProgress = NotificationProgress.Indeterminate,
                            localOnly = false,
                            deepLink = DEEP_LINK_URI
                        )
                    }
                    is InflightIndexNotification.Transcribing -> {
                        GenericNotification(
                            id = notification.id,
                            title = "Transcribing",
                            inProgress = NotificationProgress.Indeterminate,
                            localOnly = true,
                            deepLink = DEEP_LINK_URI
                        )
                    }
                    is InflightIndexNotification.AgentRunning -> {
                        val contextText = buildString {
                            appendLine(notification.userText)
                            appendLine()
                            append(buildNotifTimestamp(notification))
                        }
                        GenericNotification(
                            id = notification.id,
                            title = "Assistant running",
                            contentText = contextText,
                            inProgress = NotificationProgress.Indeterminate,
                            localOnly = true,
                            deepLink = DEEP_LINK_URI
                        )
                    }
                    is InflightIndexNotification.AgentComplete -> {
                        val contentText = buildString {
                            if (notification.pressToRXLatency != null && prefs.debugDetailsEnabled.value) {
                                val latencyInSeconds = notification.pressToRXLatency
                                    .toString(DurationUnit.SECONDS, 1)
                                appendLine("Press->RX: $latencyInSeconds")
                            }
                            if (notification.actionsTaken.isNotEmpty()) {
                                if (prefs.debugDetailsEnabled.value) {
                                    val actionNames = notification.actionsTaken
                                        .map { it.actionText() }
                                        .joinToString(", ")
                                    appendLine("Actions taken: $actionNames")
                                    appendLine()
                                    appendLine(buildNotifTimestamp(notification))
                                } else {
                                    when (val lastAction = notification.actionsTaken.lastOrNull()) {
                                        is SemanticResult.GenericFailure -> {
                                            if (lastAction.userErrorMessage != null) {
                                                appendLine(lastAction.userErrorMessage)
                                            } else {
                                                appendLine("Error performing action")
                                            }
                                        }
                                        is SemanticResult.TaskCreation if (lastAction.deadline != null) -> {
                                            val dateTime = lastAction.deadline!!.toLocalDateTime(
                                                TimeZone.currentSystemDefault()
                                            )
                                            val humanDate = UITimeUtil.humanDate(dateTime.date)
                                            val humanTime = dateTime.time.format(UITimeUtil.timeFormat())

                                            appendLine("Reminder set for ${humanDate}, ${humanTime}")
                                            appendLine()
                                            appendLine(lastAction.title)
                                        }
                                        is SemanticResult.AlarmCreation -> {
                                            val time = lastAction.fireTime
                                            appendLine("Alarm set for ${time.format(UITimeUtil.timeFormat())}")
                                        }
                                        is SemanticResult.TimerCreation -> {
                                            val requested = lastAction.requestedDuration
                                            val time = lastAction.fireTime.toLocalDateTime(TimeZone.currentSystemDefault())
                                            if (requested != null) {
                                                appendLine("$requested timer set, ending at ${time.time.format(UITimeUtil.timeFormat())}")
                                            } else {
                                                appendLine("Timer set to end at ${time.time.format(UITimeUtil.timeFormat())}")
                                            }
                                        }
                                        is SemanticResult.SupportingData -> {
                                            if (!lastAction.summary.isNullOrBlank()) {
                                                appendLine(lastAction.summary)
                                            } else {
                                                appendLine(notification.userText)
                                            }
                                        }
                                        else -> {
                                            appendLine(notification.userText)
                                        }
                                    }
                                }
                            }
                        }.trim()
                        GenericNotification(
                            id = notification.id,
                            title = if (prefs.debugDetailsEnabled.value) {
                                notification.userText
                            } else {
                                notification.actionsTaken.lastOrNull()?.actionText()
                                    ?: "Assistant complete"
                            },
                            contentText = contentText,
                            inProgress = null,
                            localOnly = false,
                            deepLink = DEEP_LINK_URI,
                            actions = listOf(
                                notification.shortcutAction.let { action ->
                                    when (action) {
                                        NoteShortcutType.SendToMe -> NotificationAction(
                                            "Email to me",
                                            "pebblecore://index-link/send-to-me?recordingId=${notification.recordingId}"
                                        )
                                        is NoteShortcutType.SendToNoteProvider -> NotificationAction(
                                            "Send to ${action.provider.title}",
                                            "pebblecore://index-link/send-to-note-provider?recordingId=${notification.recordingId}&provider=${action.provider.id}"
                                        )
                                        is NoteShortcutType.SendToReminderProvider -> NotificationAction(
                                            "Send to ${action.provider.title}",
                                            "pebblecore://index-link/send-to-reminder-provider?recordingId=${notification.recordingId}&provider=${action.provider.id}"
                                        )
                                    }
                                }
                            )
                        )
                    }
                    is InflightIndexNotification.Error -> {
                        GenericNotification(
                            id = notification.id,
                            title = "Error",
                            contentText = notification.message,
                            inProgress = null,
                            localOnly = false,
                            deepLink = DEEP_LINK_URI
                        )
                    }
                }
                if (notif != null) {
                    platformIndexNotificationManager.notify(notif)
                } else {
                    inflightNotifications[transfer.id]?.id?.let { platformIndexNotificationManager.cancel(it) }
                }

                if (
                    notification is InflightIndexNotification.AgentComplete ||
                    notification is InflightIndexNotification.Error ||
                    notification is InflightIndexNotification.Discarded
                ) {
                    inflightNotificationJobs.remove(id)?.cancel("Notification complete")
                }
            }.onCompletion {
                inflightNotificationJobs.remove(id)
                logger.d { "Notification job for recording $id completed" }
            }.launchIn(scope)
    }

    fun sendBugReportPrompt(
        title: String,
        content: String
    ) {
        val now = Clock.System.now()
        lastBugReportPrompt?.let { last ->
            if (now - last < BUG_REPORT_DEBOUNCE) {
                logger.d { "Skipping bug report prompt notification; last sent at $lastBugReportPrompt" }
                return
            }
        }
        lastBugReportPrompt = now
        val notif = GenericNotification(
            id = nextNotificationId(),
            title = title,
            contentText = content,
            inProgress = null,
            localOnly = false,
            deepLink = "pebblecore://deep-link/bug-report?pebble=false"
        )
        platformIndexNotificationManager.notify(notif)
    }


    suspend fun startNotificationProcessingJob(scope: CoroutineScope) {
        val lastTimestamp = MutableStateFlow(ringTransferRepo.getMostRecentTransfer()?.createdAt ?: Instant.DISTANT_PAST)

        lastTimestamp.flatMapLatest {
            ringTransferRepo.getTransfersAfterFlow(it)
        }.collect { transfers ->
            transfers.forEach { startNotificationJobFor(it, scope) }
            lastTimestamp.value = transfers
                .maxByOrNull { it.createdAt }
                ?.createdAt
                ?: lastTimestamp.value
        }
    }

    suspend fun processRingSyncTransferNotifications(events: Flow<RingEvent>) {
        var inProgressUpdate: GenericNotification? = null
        events.filterIsInstance<RingEvent.FirmwareUpdate>()
            .collect {
                when (it) {
                    is RingEvent.FirmwareUpdate.Started -> {
                        val notifId = nextNotificationId()
                        val notif = GenericNotification(
                            id = notifId,
                            title = "Updating Index 01 Firmware",
                            contentText = "Updating to version ${it.newVersion}",
                            inProgress = NotificationProgress.Indeterminate,
                            localOnly = false
                        )
                        platformIndexNotificationManager.notify(notif)
                    }
                    is RingEvent.FirmwareUpdate.Success -> {
                        val notif = GenericNotification(
                            id = inProgressUpdate?.id ?: nextNotificationId(),
                            title = "Index 01 Firmware Updated",
                            contentText = "Successfully updated to version ${it.newVersion}",
                            inProgress = null,
                            localOnly = false
                        )
                        platformIndexNotificationManager.notify(notif)
                        inProgressUpdate = null
                    }
                    is RingEvent.FirmwareUpdate.Failed -> {
                        val notif = GenericNotification(
                            id = inProgressUpdate?.id ?: nextNotificationId(),
                            title = "Firmware Update Failed",
                            contentText = "Failed to update Index 01 to version ${it.newVersion}",
                            inProgress = null,
                            localOnly = false,
                            deepLink = "pebblecore://deep-link/bug-report?pebble=false"
                        )
                        platformIndexNotificationManager.notify(notif)
                        inProgressUpdate = null
                    }
                }
            }
    }
}