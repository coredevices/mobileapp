package coredevices.pebble.ui

import io.rebble.libpebblecommon.database.dao.WatchPreference
import io.rebble.libpebblecommon.database.entity.BoolWatchPref
import io.rebble.libpebblecommon.database.entity.PluginActionLogEntity
import io.rebble.libpebblecommon.database.entity.QuicklaunchWatchPref
import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.BuiltInPlugins
import io.rebble.libpebblecommon.plugin.PluginMetadata
import io.rebble.libpebblecommon.plugin.WatchSettingsActions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

private val APP = Uuid.parse("11111111-1111-1111-1111-111111111111")
private val OTHER_PLUGIN = Uuid.parse("22222222-2222-2222-2222-222222222222")

class ActionDescriberTest {
    private val describer = ActionDescriber(
        plugins = listOf(PluginMetadata(OTHER_PLUGIN, "Lights", "", emptyList(), listOf(ActionDeclaration("toggle", "Toggle a light")))),
        watchPrefs = listOf(WatchPreference(BoolWatchPref.StandbyMode, null), WatchPreference(QuicklaunchWatchPref.QlUp, null)),
        appTitles = mapOf(APP to "Weather"),
    )

    private fun entry(pluginUuid: Uuid, action: String, args: String) =
        PluginActionLogEntity(atMs = 0, appUuid = APP, pluginUuid = pluginUuid, action = action, args = args, error = null)

    private fun setting(action: String, args: String) =
        describer.describe(entry(BuiltInPlugins.WATCH_SETTINGS, action, args))

    @Test
    fun aSwitchNamesTheSetting() {
        assertEquals(
            "Turned watch setting Standby Mode off",
            setting(WatchSettingsActions.SET_ON, """{"instanceId":"stationaryMode","on":false}"""),
        )
    }

    @Test
    fun aQuickLaunchButtonNamesTheApp() {
        assertEquals(
            "Set watch setting Quick Launch: Hold Up to Weather",
            setting(WatchSettingsActions.SET_VALUE, """{"instanceId":"qlUp","value":"$APP"}"""),
        )
        assertEquals(
            "Set watch setting Quick Launch: Hold Up to nothing",
            setting(WatchSettingsActions.SET_VALUE, """{"instanceId":"qlUp","value":""}"""),
        )
    }

    @Test
    fun namesAreBlankUntilTheTitlesLoad() {
        val loading = ActionDescriber(emptyList(), emptyList(), appTitles = null)
        assertEquals("", loading.appName(APP))
        assertEquals("", loading.pluginName(OTHER_PLUGIN))
        assertEquals("Weather", describer.appName(APP))
        assertEquals("Removed app", describer.appName(OTHER_PLUGIN))
        assertEquals("Removed plugin", describer.pluginName(Uuid.parse("33333333-3333-3333-3333-333333333333")))
        assertEquals("Weather", describer.pluginName(APP))
        assertEquals("toggle", loading.describe(entry(OTHER_PLUGIN, "toggle", "")))
    }

    @Test
    fun otherPluginsShowOnlyTheAction() {
        assertEquals("Lights: Toggle a light", describer.describe(entry(OTHER_PLUGIN, "toggle", """{"room":"kitchen"}""")))
    }
}
