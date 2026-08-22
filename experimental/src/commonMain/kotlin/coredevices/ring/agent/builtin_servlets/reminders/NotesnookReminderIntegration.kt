package coredevices.ring.agent.builtin_servlets.reminders

import coredevices.ring.agent.integrations.ReminderIntegration

/**
 * Notesnook is Android-only. Reminders become a Notesnook note with an attached reminder
 * alarm. iOS returns a disabled stub so the provider never surfaces there.
 */
expect fun createNotesnookReminderIntegration(): ReminderIntegration
