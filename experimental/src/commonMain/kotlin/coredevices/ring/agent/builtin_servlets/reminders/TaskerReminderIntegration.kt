package coredevices.ring.agent.builtin_servlets.reminders

import coredevices.ring.agent.integrations.ReminderIntegration

/**
 * Tasker is Android-only. The Android implementation routes reminders to Tasker via an
 * `ACTION_SEND` intent; iOS returns a disabled stub so the provider never surfaces there.
 */
expect fun createTaskerReminderIntegration(): ReminderIntegration
