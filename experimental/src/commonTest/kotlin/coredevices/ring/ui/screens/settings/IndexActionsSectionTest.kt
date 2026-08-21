package coredevices.ring.ui.screens.settings

import coredevices.ring.agent.DefaultCaptureType
import coredevices.ring.agent.IndexAction
import coredevices.ring.agent.LOCAL_MODEL_REASON
import coredevices.ring.agent.NOT_CONNECTED_REASON
import coredevices.ring.agent.builtin_servlets.calendar.CalendarServlet
import coredevices.ring.agent.builtin_servlets.notes.NoteServlet
import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.RingGesture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndexActionsSectionTest {
    @Test
    fun `info card shows when no gesture routes to the index agent`() {
        assertTrue(
            nothingRoutesToIndexAgent(
                mapOf(
                    RingGesture.Hold to GestureDestination.WebSearch,
                    RingGesture.ClickHold to GestureDestination.WebhookOnly,
                    RingGesture.Click to GestureDestination.PlayPause,
                )
            )
        )
    }

    @Test
    fun `info card hidden when any recording gesture routes to the index agent`() {
        assertFalse(
            nothingRoutesToIndexAgent(
                mapOf(
                    RingGesture.Hold to GestureDestination.WebSearch,
                    RingGesture.ClickHold to GestureDestination.IndexAgent,
                )
            )
        )
    }

    @Test
    fun `capture sentence labels follow the capture type`() {
        assertEquals("note", captureTypeLabel(DefaultCaptureType.Note))
        assertEquals("reminder", captureTypeLabel(DefaultCaptureType.Reminder))
        assertEquals(
            NoteProvider.Notion.title,
            captureDestinationLabel(
                DefaultCaptureType.Note,
                NoteProvider.Notion,
                ReminderProvider.GoogleTasks,
            )
        )
        assertEquals(
            ReminderProvider.GoogleTasks.title,
            captureDestinationLabel(
                DefaultCaptureType.Reminder,
                NoteProvider.Notion,
                ReminderProvider.GoogleTasks,
            )
        )
    }

    @Test
    fun `built-in providers are branded Index on the settings screen`() {
        assertEquals("Index Notes", NoteProvider.Builtin.settingsTitle)
        assertEquals("Index Reminders", ReminderProvider.BuiltIn.settingsTitle)
        assertEquals(
            "Index Reminders",
            captureDestinationLabel(
                DefaultCaptureType.Reminder,
                NoteProvider.Builtin,
                ReminderProvider.BuiltIn,
            )
        )
    }

    @Test
    fun `non built-in providers keep their enum title`() {
        assertEquals(NoteProvider.Notion.title, NoteProvider.Notion.settingsTitle)
        assertEquals(ReminderProvider.GoogleTasks.title, ReminderProvider.GoogleTasks.settingsTitle)
    }

    @Test
    fun `contacts badge pluralises`() {
        assertEquals("1 contact", contactsBadge(1))
        assertEquals("2 contacts", contactsBadge(2))
        assertEquals("0 contacts", contactsBadge(0))
    }

    @Test
    fun `note destinations mark unauthorized providers as unconnected`() {
        val options = noteDestOptions(listOf(NoteProvider.Builtin), isAndroid = true)
        assertEquals(NoteProvider.entries, options.map { it.provider })
        assertEquals(
            listOf(NoteProvider.Builtin),
            options.filter { it.connected }.map { it.provider }
        )
    }

    @Test
    fun `tasker is only offered on android`() {
        assertFalse(
            noteDestOptions(emptyList(), isAndroid = false).any { it.provider == NoteProvider.Tasker }
        )
        assertFalse(
            reminderDestOptions(emptyList(), isAndroid = false)
                .any { it.provider == ReminderProvider.Tasker }
        )
    }

    @Test
    fun `connecting a provider opens that provider's own flow`() {
        assertEquals(ActionsDialog.Notion, noteConnectDialog(NoteProvider.Notion))
        assertEquals(ActionsDialog.Obsidian, noteConnectDialog(NoteProvider.Obsidian))
        assertEquals(ActionsDialog.Tasker, noteConnectDialog(NoteProvider.Tasker))
        assertEquals(ActionsDialog.GoogleTasks, reminderConnectDialog(ReminderProvider.GoogleTasks))
        assertEquals(ActionsDialog.Tasker, reminderConnectDialog(ReminderProvider.Tasker))
    }

    @Test
    fun `an unconnected calendar offers the phone calendar connect flow`() {
        val disconnected = IndexAction(
            name = CalendarServlet.NAME,
            title = "Calendar",
            enabled = true,
            disabledReason = NOT_CONNECTED_REASON,
        )
        assertEquals(ActionsDialog.PhoneCalendar, actionConnectDialog(disconnected))

        assertNull(actionConnectDialog(disconnected.copy(disabledReason = null)))
        assertNull(actionConnectDialog(disconnected.copy(disabledReason = LOCAL_MODEL_REASON)))
        assertNull(
            actionConnectDialog(disconnected.copy(name = NoteServlet.NAME))
        )
    }

    @Test
    fun `providers with no connect flow fall back to the integration list`() {
        assertNull(noteConnectDialog(NoteProvider.Builtin))
        assertNull(reminderConnectDialog(ReminderProvider.BuiltIn))
        assertNull(reminderConnectDialog(ReminderProvider.IOSReminders))
    }

    @Test
    fun `ios reminders is only offered off android`() {
        assertFalse(
            reminderDestOptions(emptyList(), isAndroid = true)
                .any { it.provider == ReminderProvider.IOSReminders }
        )
        assertTrue(
            reminderDestOptions(listOf(ReminderProvider.IOSReminders), isAndroid = false)
                .any { it.provider == ReminderProvider.IOSReminders && it.connected }
        )
    }
}
