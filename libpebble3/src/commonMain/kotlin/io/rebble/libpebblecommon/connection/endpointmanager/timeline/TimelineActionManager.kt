package io.rebble.libpebblecommon.connection.endpointmanager.timeline

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.dao.TimelineNotificationRealDao
import io.rebble.libpebblecommon.database.dao.TimelinePinRealDao
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

    private suspend fun handleTimelineAction(
        invocation: TimelineService.TimelineActionInvocation,
    ): TimelineActionResult {
        val pin = pinDao.getEntry(invocation.itemId)
        if (pin == null) {
            return TimelineActionResult(
                success = false,
                icon = TimelineIcon.ResultFailed,
                title = "Failed",
            )
        }
        val action = pin.content.actions.firstOrNull { it.actionID == invocation.actionId }
        if (action == null) {
            return TimelineActionResult(
                success = false,
                icon = TimelineIcon.ResultFailed,
                title = "Failed",
            )
        }
        when (action.type) {
            TimelineItem.Action.Type.Remove -> {
                pinDao.markForDeletion(invocation.itemId)
                return TimelineActionResult(
                    success = true,
                    icon = TimelineIcon.ResultDeleted,
                    title = "Removed",
                )
            }
            else -> {
                return TimelineActionResult(
                    success = false,
                    icon = TimelineIcon.ResultFailed,
                    title = "Failed",
                )
            }
        }
    }

    private suspend fun handleAction(
        invocation: TimelineService.TimelineActionInvocation
    ) {
        val itemId = invocation.itemId
        val actionId = invocation.actionId
        val attributes = invocation.attributes
        val notificationItem = notificationDao.getEntry(itemId) ?: run {
            logger.w {
                "Received action for item $itemId, but it doesn't exist in the database"
            }
            invocation.respond(handleTimelineAction(invocation))
            return
        }
        val action = notificationItem.content.actions.firstOrNull { it.actionID == actionId } ?: run {
            logger.w {
                "Received action for item $itemId, but it doesn't exist in the pin (action ID $actionId not in ${notificationItem.content.actions.map { it.actionID }})"
            }
            return
        }
        val result = try {
            actionOverrides.actionHandlerOverrides[itemId]?.get(actionId)?.let {
                it(attributes)
            } ?:
            //when (item.) {
//                BlobCommand.BlobDatabase.Pin -> {
//                    handleTimelineAction(itemId, action, attributes)
//                }
//                BlobCommand.BlobDatabase.Notification -> {
                notifActionHandler(itemId, action, attributes)
//                }
//                else -> error(
//                    "Received action for item $itemId, but it is not a notification or pin (${item.watchDatabase})"
//                )
//            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) {
                "Error handling action for item $itemId: ${e.message}"
            }
            TimelineActionResult(
                success = false,
                icon = TimelineIcon.ResultFailed,
                title = "Failed"
            )
        }
        invocation.respond(result)
    }
}

typealias CustomTimelineActionHandler = (List<TimelineItem.Attribute>) -> TimelineActionResult
