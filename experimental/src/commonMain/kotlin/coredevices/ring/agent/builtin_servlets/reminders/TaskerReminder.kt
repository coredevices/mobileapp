package coredevices.ring.agent.builtin_servlets.reminders

import kotlin.time.Instant

/**
 * Tasker is Android-only. The Android implementation routes the reminder to Tasker via an
 * `ACTION_SEND` intent; iOS provides a stub that is never reached (the provider is not offered).
 */
expect fun createTaskerReminder(time: Instant?, message: String): ListAssignableReminder
