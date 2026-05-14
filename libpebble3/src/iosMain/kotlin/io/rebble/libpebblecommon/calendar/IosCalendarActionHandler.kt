package io.rebble.libpebblecommon.calendar

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.entity.BaseAction
import io.rebble.libpebblecommon.database.entity.TimelinePin
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.services.blobdb.TimelineActionResult

/**
 * iOS does not attach RSVP/Cancel actions to calendar pins (see
 * [IosSystemCalendar.supportsPinActions]), so this handler shouldn't be called unless something
 * weird happened.
 */
class IosCalendarActionHandler : PlatformCalendarActionHandler {
    private val logger = Logger.withTag("IosCalendarActionHandler")

    override suspend fun invoke(pin: TimelinePin, action: BaseAction): TimelineActionResult {
        logger.w { "Unsupported action: $action" }
        return TimelineActionResult(
            success = false,
            icon = TimelineIcon.ResultFailed,
            title = "Unsupported"
        )
    }
}
