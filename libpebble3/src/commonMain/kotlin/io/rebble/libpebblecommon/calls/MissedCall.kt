package io.rebble.libpebblecommon.calls

import io.rebble.libpebblecommon.SystemAppIDs.MISSED_CALLS_APP_UUID
import io.rebble.libpebblecommon.database.entity.TimelinePin
import io.rebble.libpebblecommon.database.entity.buildTimelinePin
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItemLayout
import kotlin.time.Instant
import kotlin.time.Duration

internal data class MissedCall(
    val callerNumber: String,
    val callerName: String?,
    val blockedReason: BlockedReason,
    val timestamp: Instant,
    val duration: Duration
)

internal fun MissedCall.toTimelinePin(): TimelinePin = buildTimelinePin(
    parentId = MISSED_CALLS_APP_UUID,
    timestamp = timestamp
) {
    duration = Duration.ZERO
    layout = TimelineItemLayout.GenericPin
    attributes {
        tinyIcon { TimelineIcon.TimelineMissedCall }
        title { "Missed Call" }
        subtitle { callerName ?: callerNumber }
        body { "Missed call from $callerNumber" }
    }
}
