package coredevices.ring.agent

import com.russhwolf.settings.MapSettings
import coredevices.indexai.util.JsonSnake
import coredevices.ring.agent.builtin_servlets.notes.CreateNoteTool
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import coredevices.ring.agent.builtin_servlets.reminders.ReminderTool
import coredevices.ring.database.PreferencesImpl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultCaptureTypeTest {

    @Test
    fun defaultsToNoteSoTodaysBehaviourIsUnchanged() {
        assertEquals(
            DefaultCaptureType.Note,
            PreferencesImpl(MapSettings()).defaultCaptureType.value
        )
    }

    @Test
    fun persistsAndPublishesTheSelectedCaptureType() {
        val settings = MapSettings()
        val preferences = PreferencesImpl(settings)

        preferences.setDefaultCaptureType(DefaultCaptureType.Reminder)

        assertEquals(DefaultCaptureType.Reminder, preferences.defaultCaptureType.value)
        assertEquals(
            DefaultCaptureType.Reminder,
            PreferencesImpl(settings).defaultCaptureType.value
        )
    }

    @Test
    fun reminderCapturesGoToIndexRemindersUntilTheUserPicksSomethingElse() {
        val settings = MapSettings()

        assertEquals(ReminderProvider.BuiltIn, PreferencesImpl(settings).reminderProvider.value)

        PreferencesImpl(settings).setReminderProvider(ReminderProvider.GoogleTasks)

        assertEquals(ReminderProvider.GoogleTasks, PreferencesImpl(settings).reminderProvider.value)
    }

    @Test
    fun unknownStoredIdFallsBackToNote() {
        assertEquals(DefaultCaptureType.Note, DefaultCaptureType.fromId(99))
    }

    @Test
    fun notePromptKeepsTheNoteFallbackGuidance() {
        val prompt = IndexAgentNenya.agentContext(DefaultCaptureType.Note)

        assertTrue(prompt.contains("Create a note with the user's input"))
        assertTrue(prompt.contains("always lean towards creating a note"))
        assertTrue(prompt.contains("fall back to creating a note with what the user said"))
        assertFalse(prompt.contains("creating a reminder"))
    }

    @Test
    fun reminderPromptSwapsTheFallbackGuidanceToReminders() {
        val prompt = IndexAgentNenya.agentContext(DefaultCaptureType.Reminder)

        assertTrue(prompt.contains("Create a reminder with the user's input"))
        assertTrue(prompt.contains("always lean towards creating a reminder"))
        assertTrue(prompt.contains("fall back to creating a reminder with what the user said"))
        assertFalse(prompt.contains("creating a note"))
    }

    @Test
    fun noteFallbackRunsTheCreateNoteToolWithTheUsersWords() {
        val call = DefaultCaptureType.Note.fallbackToolCall("buy milk")

        assertEquals(CreateNoteTool.TOOL_NAME, call.toolName)
        val args = JsonSnake.decodeFromString<CreateNoteTool.CreateNoteArgs>(call.arguments)
        assertEquals("buy milk", args.text)
        assertTrue(args.automatic)
    }

    @Test
    fun reminderFallbackRunsTheCreateReminderToolWithNoTime() {
        val call = DefaultCaptureType.Reminder.fallbackToolCall("buy milk")

        assertEquals(ReminderTool.TOOL_NAME, call.toolName)
        val args = Json.parseToJsonElement(call.arguments).jsonObject
        assertEquals("buy milk", args["message"]?.jsonPrimitive?.content)
        assertEquals(setOf("message"), args.keys)
    }
}
