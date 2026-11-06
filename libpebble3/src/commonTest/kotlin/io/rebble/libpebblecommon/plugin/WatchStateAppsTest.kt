package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.locker.AppBasicProperties
import io.rebble.libpebblecommon.locker.AppType
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class WatchStateAppsTest {
    private val face = AppBasicProperties(Uuid.random(), AppType.Watchface, "Face", "Dev")
    private val app = AppBasicProperties(Uuid.random(), AppType.Watchapp, "App", "Dev")
    private val plugin = AppBasicProperties(Uuid.random(), AppType.Plugin, "Plugin", "Dev")

    @Test
    fun listsLaunchableAppsByUuidWithNameAndType() {
        val instances = appsEnvelope(listOf(face, plugin, app)).instances

        assertEquals(listOf(face.id.toString(), app.id.toString()), instances.map { it.instanceId })
        assertEquals(
            listOf("Face" to "watchface", "App" to "watchapp"),
            instances.map { it.shortText(SourceInstance.PROPERTY_NAME) to it.shortText(WatchStatePlugin.PROPERTY_TYPE) },
        )
    }

    @Test
    fun readingNeedsReadApps() {
        assertEquals(setOf(PluginPermissions.READ_APPS), requiredOfCaller(WatchStatePlugin.APPS_SOURCE))
    }

    @Test
    fun launchAppCanBeFiredFromAnAppTile() {
        val action = WatchStatePlugin.LAUNCH_APP_ACTION
        assertEquals(listOf("${WatchStatePlugin.CATEGORY}/${WatchStatePlugin.ITEM_APP}"), action.targets)
        assertTrue(action.bindable)
    }

    private fun SourceInstance.shortText(property: String) =
        properties.getValue(property).getValue(SourceShapeNames.SHORT_TEXT).jsonObject.getValue("text").jsonPrimitive.content
}
