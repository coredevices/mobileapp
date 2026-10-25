package coredevices.pebble.ui

import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class PluginCapabilitiesTest {
    private fun metadata(sources: List<SourceDeclaration> = emptyList(), actions: List<ActionDeclaration> = emptyList()) =
        PluginMetadata(Uuid.random(), "Hue", "", sources, actions)

    @Test
    fun sourceBlocksSharingACategoryAreOneGroupWithoutRepeatedItems() {
        val shapes = mapOf("name" to listOf("shortText"), "on" to listOf("boolean"), "brightness" to listOf("numericValue"))
        val capabilities = PluginCapabilities.of(
            metadata(
                sources = listOf(
                    SourceDeclaration("home", listOf("room_lights", "light"), shapes),
                    SourceDeclaration("home", listOf("home_lights", "light"), shapes),
                    SourceDeclaration("weather", listOf("location")),
                ),
            ),
        )

        assertEquals(listOf("Home", "Weather"), capabilities.sources.map { it.label })
        val home = capabilities.sources.first()
        assertEquals(listOf("Room lights", "Light", "Home lights"), home.items.map { it.label })
        assertEquals(listOf("Name", "On", "Brightness"), home.items.first().properties)
        assertTrue(capabilities.sources.last().items.single().properties.isEmpty())
    }

    @Test
    fun actionsKeepTheirDeclaredOrderAndFlags() {
        val capabilities = PluginCapabilities.of(
            metadata(
                actions = listOf(
                    ActionDeclaration("set_playing", description = " Resume or pause. "),
                    ActionDeclaration("clear_message", destructive = true, requiresConfirmation = true),
                ),
            ),
        )

        assertEquals(listOf("Set playing", "Clear message"), capabilities.actions.map { it.label })
        assertEquals("Resume or pause.", capabilities.actions[0].description)
        assertTrue(capabilities.actions[1].destructive && capabilities.actions[1].requiresConfirmation)
    }

    @Test
    fun aManifestWithNothingDeclaredIsEmpty() {
        assertTrue(PluginCapabilities.of(metadata()).isEmpty)
    }

    @Test
    fun namesReadAsWords() {
        assertEquals("Room lights", PluginCapabilities.humanize("room_lights"))
        assertEquals("Set playing", PluginCapabilities.humanize("setPlaying"))
        assertEquals("Day change percent", PluginCapabilities.humanize("day-change_percent"))
        assertEquals("Uv index", PluginCapabilities.humanize("uv_index"))
    }
}
