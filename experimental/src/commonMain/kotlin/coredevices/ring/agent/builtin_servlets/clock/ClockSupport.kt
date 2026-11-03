package coredevices.ring.agent.builtin_servlets.clock

const val IOS_26_REQUIRED_REASON = "Requires iOS 26 or later"

/** Null when this device can set timers and alarms, otherwise why it can't. */
expect fun clockUnavailableReason(): String?
