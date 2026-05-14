package io.rebble.libpebblecommon.connection.endpointmanager.timeline

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.SystemAppIDs.CALENDAR_APP_UUID
import io.rebble.libpebblecommon.calendar.PlatformCalendarActionHandler
import io.rebble.libpebblecommon.database.dao.TimelineNotificationRealDao
import io.rebble.libpebblecommon.database.dao.TimelinePinRealDao
import io.rebble.libpebblecommon.database.entity.TimelineNotification
import io.rebble.libpebblecommon.database.entity.TimelinePin
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.services.blobdb.TimelineActionResult
import io.rebble.libpebblecommon.services.blobdb.TimelineService
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
) {
    companion object {
        private val logger = Logger.withTag(TimelineActionManager::class.simpleName!!)
    }

    fun init() {
        timelineService.actionInvocations.onEach {
            handleAction(it)
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
            pin.content.parentId == CALENDAR_APP_UUID -> calendarActionHandler(pin, action)
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
        return notifActionHandler(invocation.itemId, action, invocation.attributes)
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
                    if (pin != null) {
                        handlePinAction(pin, invocation)
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

typealias CustomTimelineActionHandler = (List<TimelineItem.Attribute>) -> TimelineActionResult
