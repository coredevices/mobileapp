package io.rebble.libpebblecommon.timeline

import io.rebble.libpebblecommon.connection.Timeline
import io.rebble.libpebblecommon.connection.endpointmanager.timeline.ActionOverrides
import io.rebble.libpebblecommon.connection.endpointmanager.timeline.InternalActionHandler
import io.rebble.libpebblecommon.database.dao.TimelinePinRealDao
import io.rebble.libpebblecommon.database.dao.TimelineReminderRealDao
import io.rebble.libpebblecommon.database.entity.TimelinePin
import io.rebble.libpebblecommon.database.entity.TimelineReminder
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class TimelineApi(
    private val timelinePinRealDao: TimelinePinRealDao,
    private val timelineReminderRealDao: TimelineReminderRealDao,
    private val actionOverrides: ActionOverrides,
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
) : Timeline {
    override fun insertOrReplace(pin: TimelinePin) {
        libPebbleCoroutineScope.launch {
            timelinePinRealDao.insertOrReplace(pin)
        }
    }

    override fun delete(pinUuid: Uuid) {
        libPebbleCoroutineScope.launch {
            timelinePinRealDao.markForDeletion(pinUuid)
        }
    }

    override suspend fun insertOrReplace(reminder: TimelineReminder) {
        timelineReminderRealDao.insertOrReplace(reminder)
    }

    override suspend fun deleteReminder(reminderUuid: Uuid) {
        timelineReminderRealDao.markForDeletion(reminderUuid)
    }

    override fun setActionHandler(internalType: String, handler: InternalActionHandler) {
        actionOverrides.internalActionHandlers[internalType] = handler
    }
}