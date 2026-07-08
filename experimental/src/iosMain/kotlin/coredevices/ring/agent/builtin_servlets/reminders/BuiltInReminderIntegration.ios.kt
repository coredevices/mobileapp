package coredevices.ring.agent.builtin_servlets.reminders

import coredevices.ring.agent.integrations.ReminderIntegration

actual fun createBuiltInReminderIntegration(): BuiltInReminderIntegration =
    IOSBuiltInReminderIntegration()

actual fun createRemindersAppIntegration(): ReminderIntegration = IOSRemindersIntegration()
