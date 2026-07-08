package coredevices.ring.ui

import coredevices.indexai.data.entity.ItemDocument.ItemMetadata

/**
 * Opens the system clock app on its timers or alarms screen — the surface that
 * owns timers/alarms set by the agent. Returns false when the platform can't
 * (iOS, or no clock app installed) so callers can fall back to in-app navigation.
 */
expect fun openSystemClockApp(fireKind: ItemMetadata.Scheduled.FireKind): Boolean
