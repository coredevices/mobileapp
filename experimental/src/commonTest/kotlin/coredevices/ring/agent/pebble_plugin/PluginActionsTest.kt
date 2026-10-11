package coredevices.ring.agent.pebble_plugin

import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class PluginActionsTest {
    private val things = FakePlugin(
        actions = listOf(ActionDeclaration(name = "create_todo"), ActionDeclaration(name = "create_note")),
        name = "Things",
        pluginUuid = Uuid.parse("00000000-0000-0000-0002-000000000001"),
    )
    private val notes = FakePlugin(
        actions = listOf(ActionDeclaration(name = "create_note"), ActionDeclaration(name = "create_reminder")),
        name = "Notes",
        pluginUuid = Uuid.parse("00000000-0000-0000-0002-000000000002"),
    )
    private val music = FakePlugin(
        actions = listOf(ActionDeclaration(name = "next")),
        name = "Music",
        pluginUuid = Uuid.parse("00000000-0000-0000-0002-000000000003"),
    )
    private val weather = FakePlugin(
        name = "Weather",
        pluginUuid = Uuid.parse("00000000-0000-0000-0002-000000000004"),
        sources = listOf(
            SourceDeclaration(category = "weather", items = listOf("current", "forecast")),
            SourceDeclaration(category = "air", items = listOf("quality")),
        ),
    )
    private val empty = FakePlugin(name = "Empty")

    @Test
    fun `destination actions are not agent actions`() {
        assertEquals(listOf("create_todo"), things.agentActions().map { it.name })
        assertEquals(emptyList(), notes.agentActions())
    }

    @Test
    fun `sources are listed as category slash item paths`() {
        assertEquals(listOf("weather/current", "weather/forecast", "air/quality"), weather.agentSources())
        assertEquals(emptyList(), things.agentSources())
    }

    @Test
    fun `a plugin offers a grant only when it declares something for it`() {
        assertTrue(things.offers(PluginGrant.Actions))
        assertFalse(things.offers(PluginGrant.Sources))
        assertTrue(weather.offers(PluginGrant.Sources))
        assertFalse(weather.offers(PluginGrant.Actions))
        assertFalse(notes.offers(PluginGrant.Actions))
    }

    @Test
    fun `only plugins with actions or sources reach the agent list`() {
        assertEquals(listOf(things, weather, music), listOf(things, weather, notes, music, empty).forAgent())
    }

    @Test
    fun `each grant only includes plugins that offer it`() {
        val all = listOf(things, weather, notes, music)
        assertEquals(listOf(things, music), all.withGrant(PluginGrant.Actions, emptySet()))
        assertEquals(listOf(weather), all.withGrant(PluginGrant.Sources, emptySet()))
    }

    @Test
    fun `plugins the user switched off are left out of that grant only`() {
        val all = listOf(things, weather, notes, music)
        val disabled = setOf(things.pluginUuid, weather.pluginUuid)
        assertEquals(listOf(music), all.withGrant(PluginGrant.Actions, disabled))
        assertEquals(emptyList(), all.withGrant(PluginGrant.Sources, disabled))
        assertEquals(listOf(weather), all.withGrant(PluginGrant.Sources, setOf(things.pluginUuid)))
    }
}
