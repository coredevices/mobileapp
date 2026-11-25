package coredevices.ring.agent.pebble_plugin

import coredevices.ring.agent.builtin_servlets.notes.NoteServlet
import coredevices.ring.agent.builtin_servlets.reminders.ReminderServlet
import io.rebble.libpebblecommon.plugin.ActionDeclaration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IndexServletPluginsTest {
    private val plugins = FakePlugins()
    private val indexEnabled = MutableStateFlow(false)
    private val notes = FakePlugin(
        actions = listOf(ActionDeclaration(name = "create_note")),
        name = "Notes",
        pluginUuid = NoteServlet.PLUGIN_UUID,
    )
    private val reminders = FakePlugin(
        actions = listOf(ActionDeclaration(name = "create_reminder")),
        name = "Reminders",
        pluginUuid = ReminderServlet.PLUGIN_UUID,
    )
    private var built = 0
    private val servletPlugins = IndexServletPlugins(plugins, indexEnabled) {
        built++
        listOf(notes, reminders)
    }

    private fun registered() = plugins.getAllPlugins().map { it.pluginUuid }.toSet()

    @Test
    fun `nothing is registered while Index is off`() = runTest {
        servletPlugins.start(backgroundScope)
        runCurrent()

        assertEquals(emptySet(), registered())
        assertEquals(0, built)
    }

    @Test
    fun `the servlet plugins register once Index is enabled`() = runTest {
        servletPlugins.start(backgroundScope)
        runCurrent()

        indexEnabled.value = true
        runCurrent()

        assertEquals(setOf(notes.pluginUuid, reminders.pluginUuid), registered())
        assertEquals(1, built)
    }

    @Test
    fun `they are removed again when Index is switched off`() = runTest {
        indexEnabled.value = true
        servletPlugins.start(backgroundScope)
        runCurrent()
        assertEquals(2, registered().size)

        indexEnabled.value = false
        runCurrent()

        assertEquals(emptySet(), registered())
    }
}
