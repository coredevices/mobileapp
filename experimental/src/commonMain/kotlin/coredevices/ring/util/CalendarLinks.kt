package coredevices.ring.util

import kotlin.time.Instant

/**
 * Opens the system calendar app focused on [startTime]. Used by the "Added … to calendar"
 * chip so the user can see the event the agent created. Failures are logged, not thrown —
 * a missing calendar app shouldn't crash the recording view.
 */
expect fun openSystemCalendarAt(startTime: Instant)
