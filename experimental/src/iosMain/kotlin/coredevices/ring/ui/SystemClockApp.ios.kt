package coredevices.ring.ui

import coredevices.indexai.data.entity.ItemDocument.ItemMetadata

actual fun openSystemClockApp(fireKind: ItemMetadata.Scheduled.FireKind): Boolean {
    // iOS has no way to open the Clock app (and timers/alarms can't be set there
    // anyway); callers fall back to in-app navigation.
    return false
}
