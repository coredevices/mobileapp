package io.rebble.libpebblecommon.permissions

import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.PermissionDeclaration
import io.rebble.libpebblecommon.plugin.PluginManifest
import io.rebble.libpebblecommon.plugin.PluginPermission
import io.rebble.libpebblecommon.plugin.PluginPermissions
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.uuid.Uuid

private val HUE = Uuid.parse("00000000-0000-0000-0002-000000000001")

private fun manifest(
    defines: List<PermissionDeclaration> = listOf(PermissionDeclaration("HomeControl", "Control your lights", PermissionKind.Write)),
    callerPermissions: List<String> = listOf("HomeControl"),
) = PluginManifest(
    definesPermissions = defines,
    sources = listOf(
        SourceDeclaration("home", listOf("light"), callerPermissions = callerPermissions.map { PluginPermission(it) })
    ),
    actions = listOf(ActionDeclaration("set_on", callerPermissions = callerPermissions.map { PluginPermission(it) })),
)

class PluginDefinedPermissionsTest {
    @Test
    fun aPluginsOwnPermissionResolvesToAKeyScopedToIt() {
        val resolved = manifest(callerPermissions = listOf("HomeControl", PluginPermissions.LOCATION))
            .resolvePermissions(HUE)
        val expected = listOf("$HUE:HomeControl", PluginPermissions.LOCATION)
        assertEquals(expected, resolved.sources.single().callerPermissions.map { it.name })
        assertEquals(expected, resolved.actions.single().callerPermissions.map { it.name })
    }

    @Test
    fun aNameThatIsNeitherBuiltInNorDefinedIsRejected() {
        assertFailsWith<IllegalArgumentException> { manifest(defines = emptyList()).resolvePermissions(HUE) }
        assertFailsWith<IllegalArgumentException> {
            manifest(callerPermissions = listOf("$HUE:HomeControl")).resolvePermissions(HUE)
        }
    }

    @Test
    fun aPluginsOwnNameWinsOverABuiltInOne() {
        val resolved = manifest(
            defines = listOf(PermissionDeclaration(PluginPermissions.CALENDAR, "Read Hue's schedule", PermissionKind.Read)),
            callerPermissions = listOf(PluginPermissions.CALENDAR),
        ).resolvePermissions(HUE)
        assertEquals("$HUE:${PluginPermissions.CALENDAR}", resolved.sources.single().callerPermissions.single().name)
    }

    @Test
    fun aDefinitionCantBreakTheKeyOrTakeTheImplicitOne() {
        listOf("InvokeAction", "Home:Control", "Home/Control", "").forEach { name ->
            assertFailsWith<IllegalArgumentException>(name) {
                manifest(defines = listOf(PermissionDeclaration(name, "x", PermissionKind.Write)), callerPermissions = emptyList())
                    .resolvePermissions(HUE)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            manifest(defines = listOf(PermissionDeclaration("HomeControl", " ", PermissionKind.Write))).resolvePermissions(HUE)
        }
    }

    @Test
    fun aDefinitionWithoutAKindIsRejected() {
        val json = """{"definesPermissions":[{"name":"HomeControl","label":"Control your lights"}]}"""
        assertFailsWith<SerializationException> { Json.decodeFromString(PluginManifest.serializer(), json) }
        val withKind = """{"definesPermissions":[{"name":"HomeControl","label":"Control your lights","kind":"write"}]}"""
        assertEquals(
            PermissionKind.Write,
            Json.decodeFromString(PluginManifest.serializer(), withKind).definesPermissions.single().kind,
        )
    }

    private fun stored(manifest: PluginManifest, manifestVersion: Int = 2) = LockerEntry(
        id = HUE,
        version = "1.0",
        title = "Hue",
        type = "plugin",
        developerName = "Pebble",
        configurable = false,
        pbwVersionCode = "0",
        platforms = emptyList(),
        pluginManifest = Json.encodeToString(PluginManifest.serializer(), manifest),
        manifestVersion = manifestVersion,
    )

    @Test
    fun everyPluginHasAnExfiltratingPermissionToSendItData() {
        val defined = pluginDefinedPermissions(listOf(stored(manifest(defines = emptyList(), callerPermissions = emptyList()))))
        assertEquals(PermissionKind.Exfiltrating, defined[invokeActionKey(HUE)]?.kind)
    }

    @Test
    fun definitionsComeFromTheStoredManifest() {
        val defined = pluginDefinedPermissions(listOf(stored(manifest())))
        assertEquals("Control your lights", defined["$HUE:HomeControl"]?.label)
        assertEquals("Hue", defined["$HUE:HomeControl"]?.pluginName)
    }

    @Test
    fun aPluginThatWouldntLoadDefinesNothing() {
        assertEquals(emptyMap(), pluginDefinedPermissions(listOf(stored(manifest(), manifestVersion = 1))))
        assertEquals(emptyMap(), pluginDefinedPermissions(listOf(stored(manifest(callerPermissions = listOf("Nonsense"))))))
    }

    @Test
    fun aCallerDeclaresAnotherPluginsPermissionByItsUuid() {
        val declared = Json.decodeFromString(
            ListSerializer(PluginPermission.serializer()),
            """[{ "name": "HomeControl", "plugin": "$HUE" }, { "name": "InvokeAction", "plugin": "$HUE" }, { "name": "X", "plugin": "nope" }]""",
        )
        assertEquals(listOf("$HUE:HomeControl", invokeActionKey(HUE)), declared.flatMap { it.keys() })
    }
}
