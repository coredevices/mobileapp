package io.rebble.libpebblecommon.calendar

import io.rebble.libpebblecommon.database.entity.BaseAction
import io.rebble.libpebblecommon.database.entity.TimelinePin
import io.rebble.libpebblecommon.services.blobdb.TimelineActionResult

interface PlatformCalendarActionHandler {
    suspend operator fun invoke(pin: TimelinePin, action: BaseAction): TimelineActionResult
}
