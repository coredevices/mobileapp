package io.rebble.libpebblecommon.plugin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private class PermissionsFakePlugin(
    callerPermissions: List<PluginPermission> = emptyList(),
) : Plugin {
    override val pluginUuid: Uuid = Uuid.parse("00000000-0000-0000-0002-000000000001")
    override val name = "Fake"
    override val description = ""
    override val sources = listOf(
        SourceDeclaration(
            category = "calendar",
            items = listOf("event"),
            callerPermissions = callerPermissions,
        )
    )
    override val actions = listOf(
        ActionDeclaration(name = "do_it", callerPermissions = callerPermissions)
    )

    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
    ): Flow<SourceEnvelope> = emptyFlow()
}

private fun declared(vararg names: String) = names.map { PluginPermission(it) }

class CallerPermissionsTest {

    @Test
    fun aSourceGuardsItselfWithWhatItAsksOfCallers() {
        val plugin = PermissionsFakePlugin(
            callerPermissions = declared(PluginPermissions.CALENDAR)
        )
        assertEquals(setOf(PluginPermissions.CALENDAR), requiredOfCaller(plugin.sources[0]))
        assertEquals(setOf(PluginPermissions.CALENDAR), requiredOfCaller(plugin.actions[0]))
    }

    @Test
    fun aSourceCanAskForMoreThanOneThing() {
        val plugin = PermissionsFakePlugin(
            callerPermissions = declared(PluginPermissions.CALENDAR, "HomeControl")
        )
        assertEquals(
            setOf(PluginPermissions.CALENDAR, "HomeControl"),
            requiredOfCaller(plugin.sources[0]),
        )
    }

    @Test
    fun anUnguardedSourceIsReadableByAnyone() {
        val plugin = PermissionsFakePlugin()
        assertTrue(requiredOfCaller(plugin.sources[0]).isEmpty())
        assertTrue(requiredOfCaller(plugin.actions[0]).isEmpty())
    }
}
