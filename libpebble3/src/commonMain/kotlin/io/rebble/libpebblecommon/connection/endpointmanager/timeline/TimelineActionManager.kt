package io.rebble.libpebblecommon.connection.endpointmanager.timeline

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.SystemAppIDs.CALENDAR_APP_UUID
import io.rebble.libpebblecommon.calendar.PlatformCalendarActionHandler
import io.rebble.libpebblecommon.database.dao.TimelineNotificationRealDao
import io.rebble.libpebblecommon.database.dao.TimelinePinRealDao
import io.rebble.libpebblecommon.database.dao.TimelineReminderRealDao
import io.rebble.libpebblecommon.database.entity.PluginActionRef
import io.rebble.libpebblecommon.database.entity.TimelineNotification
import io.rebble.libpebblecommon.database.entity.TimelinePin
import io.rebble.libpebblecommon.database.entity.TimelineReminder
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.blobdb.TimelineAttribute
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.plugin.PluginCaller
import io.rebble.libpebblecommon.plugin.PluginClient
import io.rebble.libpebblecommon.plugin.PluginRegistry
import io.rebble.libpebblecommon.services.blobdb.TimelineActionResult
import io.rebble.libpebblecommon.services.blobdb.TimelineService
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

class ActionOverrides {
    val actionHandlerOverrides =
        mutableMapOf<Uuid, Map<UByte, CustomTimelineActionHandler>>()

    fun setActionHandlers(itemId: Uuid, actionHandlers: Map<UByte, CustomTimelineActionHandler>) {
        actionHandlerOverrides[itemId] = actionHandlers
    }
}

class TimelineActionManager(
    private val timelineService: TimelineService,
    private val notifActionHandler: PlatformNotificationActionHandler,
    private val calendarActionHandler: PlatformCalendarActionHandler,
    private val scope: ConnectionCoroutineScope,
    private val notificationDao: TimelineNotificationRealDao,
    private val actionOverrides: ActionOverrides,
    private val pinDao: TimelinePinRealDao,
    private val reminderDao: TimelineReminderRealDao,
    private val pluginRegistry: PluginRegistry,
    private val pluginClient: PluginClient,
) {
    companion object {
        private val logger = Logger.withTag(TimelineActionManager::class.simpleName!!)
    }

    fun init() {
        timelineService.actionInvocations.onEach {
            handleAction(it)
        }.launchIn(scope)
        notifActionHandler.onDisplayedItem(null)
        timelineService.displayedItems.onEach {
            logger.d { "Watch displaying notification $it" }
            notifActionHandler.onDisplayedItem(it)
        }.onCompletion {
            notifActionHandler.onDisplayedItem(null)
        }.launchIn(scope)
    }

    private suspend fun handlePinAction(
        pin: TimelinePin,
        invocation: TimelineService.TimelineActionInvocation,
    ): TimelineActionResult {
        val action = pin.content.actions.firstOrNull { it.actionID == invocation.actionId }
            ?: run {
                logger.w {
                    "Action ${invocation.actionId} missing on pin ${invocation.itemId}"
                }
                return failedResult()
            }
        return when {
            action.type == TimelineItem.Action.Type.Remove -> {
                pinDao.markForDeletion(invocation.itemId)
                TimelineActionResult(
                    success = true,
                    icon = TimelineIcon.ResultDeleted,
                    title = "Removed",
                )
            }
            action.pluginAction != null -> invokePluginAction(pin.content.parentId, action.pluginAction, invocation.attributes)
            pin.content.parentId == CALENDAR_APP_UUID -> calendarActionHandler(pin, action)
            else -> failedResult()
        }
    }

    private suspend fun handleReminderAction(
        reminder: TimelineReminder,
        invocation: TimelineService.TimelineActionInvocation,
    ): TimelineActionResult {
        val action = reminder.content.actions.firstOrNull { it.actionID == invocation.actionId }
            ?: run {
                logger.w {
                    "Action ${invocation.actionId} missing on reminder ${invocation.itemId}"
                }
                return failedResult()
            }
        return when (action.type) {
            // The reminder popup's Dismiss (and the dismiss the firmware fires by
            // itself after a snooze) is remote: the watch dismisses the popup on a
            // successful response and there is nothing else for the phone to do.
            TimelineItem.Action.Type.Dismiss -> TimelineActionResult(
                success = true,
                icon = TimelineIcon.ResultDismissed,
                title = "Dismissed",
            )
            else -> failedResult()
        }
    }

    private suspend fun handleNotificationAction(
        notification: TimelineNotification,
        invocation: TimelineService.TimelineActionInvocation,
    ): TimelineActionResult {
        val action = notification.content.actions.firstOrNull { it.actionID == invocation.actionId }
            ?: run {
                logger.w {
                    "Action ${invocation.actionId} missing on notification ${invocation.itemId} " +
                            "(have ${notification.content.actions.map { it.actionID }})"
                }
                return failedResult()
            }
        action.pluginAction?.let { return invokePluginAction(notification.content.parentId, it, invocation.attributes) }
        return notifActionHandler(invocation.itemId, action, invocation.attributes)
    }

    /** Only ever the item's own plugin: whoever made the item chose the action and its args. */
    private suspend fun invokePluginAction(
        parentId: Uuid,
        ref: PluginActionRef,
        attributes: List<TimelineItem.Attribute>,
    ): TimelineActionResult {
        val plugin = pluginRegistry.findPlugin(parentId.toString()) ?: return failedResult()
        val args = ref.argsWithReply(attributes) ?: return failedResult()
        val result = pluginClient.invoke(PluginCaller.Host(), plugin.metadata, ref.action, args, timeout = null)
        if (!result.ok) return failedResult()
        return TimelineActionResult(
            success = true,
            icon = TimelineIcon.ResultSent,
            title = result.text ?: "Done",
        )
    }

    private suspend fun handleAction(
        invocation: TimelineService.TimelineActionInvocation,
    ) {
        val itemId = invocation.itemId
        val actionId = invocation.actionId
        val result = try {
            // Per-item overrides take precedence over the default pin/notification dispatch
            // so callers can swap in custom behavior without touching the DB-backed model.
            actionOverrides.actionHandlerOverrides[itemId]?.get(actionId)?.invoke(invocation.attributes)
                ?: run {
                    val pin = pinDao.getEntry(itemId)
                    val reminder = if (pin == null) reminderDao.getEntry(itemId) else null
                    if (pin != null) {
                        handlePinAction(pin, invocation)
                    } else if (reminder != null) {
                        handleReminderAction(reminder, invocation)
                    } else {
                        val notification = notificationDao.getEntry(itemId)
                        if (notification != null) {
                            handleNotificationAction(notification, invocation)
                        } else {
                            logger.w { "Received action for unknown item $itemId" }
                            failedResult()
                        }
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Error handling action for item $itemId: ${e.message}" }
            failedResult()
        }
        invocation.respond(result)
    }

    private fun failedResult() = TimelineActionResult(
        success = false,
        icon = TimelineIcon.ResultFailed,
        title = "Failed",
    )
}

/** Null when the action asks for a reply and the watch sent none. */
internal fun PluginActionRef.argsWithReply(attributes: List<TimelineItem.Attribute>): JsonObject? {
    val arg = inputArg ?: return args
    val reply = attributes.firstOrNull { it.attributeId.get() == TimelineAttribute.Title.id }
        ?.content?.get()?.asByteArray()?.decodeToString()?.trimEnd('\u0000') ?: return null
    return JsonObject(args + (arg to JsonPrimitive(reply)))
}

typealias CustomTimelineActionHandler = (List<TimelineItem.Attribute>) -> TimelineActionResult
