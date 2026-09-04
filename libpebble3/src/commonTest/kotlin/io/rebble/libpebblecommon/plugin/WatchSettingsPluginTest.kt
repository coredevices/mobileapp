package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.connection.WatchPrefs
import io.rebble.libpebblecommon.database.dao.WatchPreference
import io.rebble.libpebblecommon.database.entity.BoolWatchPref
import io.rebble.libpebblecommon.database.entity.EnumWatchPref
import io.rebble.libpebblecommon.database.entity.NumberWatchPref
import io.rebble.libpebblecommon.database.entity.QuickLaunchSetting
import io.rebble.libpebblecommon.database.entity.QuicklaunchWatchPref
import io.rebble.libpebblecommon.database.entity.WatchPref
import io.rebble.libpebblecommon.plugin.ActionDeclaration.Companion.PARAM_INSTANCE_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private class FakeWatchPrefs : WatchPrefs {
    val written = mutableListOf<WatchPreference<*>>()
    private val state = MutableStateFlow(
        WatchPref.enumeratePrefs().map { WatchPreference(it, null) }
    )
    override val watchPrefs: Flow<List<WatchPreference<*>>> = state

    override fun setWatchPref(watchPref: WatchPreference<*>) {
        written += watchPref
        state.value = state.value.map { if (it.pref == watchPref.pref) watchPref else it }
    }
}

private fun args(vararg pairs: Pair<String, String>): JsonObject = Json.decodeFromString(
    JsonObject.serializer(),
    pairs.joinToString(",", "{", "}") { (key, value) -> "\"$key\":\"$value\"" },
)

private suspend fun WatchSettingsPlugin.instance(item: String, id: String) =
    observe(WatchSettingsPlugin.CATEGORY, item).first()
        .instances.firstOrNull { it.instanceId == id }

private fun SourceInstance.shape(property: String, shape: String) =
    properties[property]?.get(shape)?.toString()

/** Every shape a property carries, so a test can say what is *not* there too. */
private fun SourceInstance.shapes(property: String) =
    properties[property]?.mapValues { (_, payload) -> payload.toString() }

class WatchSettingsPluginTest {

    @Test
    fun everySettingIsAnInstance_namedAsTheSettingsScreenNamesIt() = runTest {
        val plugin = WatchSettingsPlugin(FakeWatchPrefs())
        val clock = plugin.instance(WatchSettingsPlugin.ITEM_SWITCH, BoolWatchPref.Clock24h.id)
        assertEquals(
            "{\"text\":\"${BoolWatchPref.Clock24h.displayName}\"}",
            clock?.shape(SourceInstance.PROPERTY_NAME, SourceShapeNames.SHORT_TEXT),
        )
    }

    @Test
    fun eachKindOfSettingIsItsOwnThing() = runTest {
        val plugin = WatchSettingsPlugin(FakeWatchPrefs())
        // A switch is only ever in the switches, so a subscriber knows what it is drawing.
        assertNull(plugin.instance(WatchSettingsPlugin.ITEM_NUMBER, BoolWatchPref.Clock24h.id))
        assertNull(plugin.instance(WatchSettingsPlugin.ITEM_TEXT, BoolWatchPref.Clock24h.id))
        assertNull(
            plugin.instance(WatchSettingsPlugin.ITEM_SWITCH, NumberWatchPref.BacklightTimeoutMs.id)
        )
    }

    @Test
    fun debugSettingsAreLeftOut() = runTest {
        val plugin = WatchSettingsPlugin(FakeWatchPrefs())
        val debug = WatchPref.enumeratePrefs().first { it.isDebugSetting }
        assertNull(plugin.instance(WatchSettingsPlugin.ITEM_NUMBER, debug.id))
        assertNull(plugin.instance(WatchSettingsPlugin.ITEM_SWITCH, debug.id))
    }

    @Test
    fun aSwitchReadsAsACheckboxAndNothingElse() = runTest {
        val plugin = WatchSettingsPlugin(FakeWatchPrefs())
        // Defaults to on, and nothing has been set, so the default is what it reads as.
        val backlight =
            plugin.instance(WatchSettingsPlugin.ITEM_SWITCH, BoolWatchPref.Backlight.id)
        assertEquals(
            mapOf(SourceShapeNames.BOOLEAN to "{\"value\":true}"),
            backlight?.shapes(WatchSettingsPlugin.PROPERTY_VALUE),
        )
    }

    @Test
    fun aNumberCarriesTheRangeTheSettingsScreenEnforces() = runTest {
        val plugin = WatchSettingsPlugin(FakeWatchPrefs())
        val timeout = plugin.instance(
            WatchSettingsPlugin.ITEM_NUMBER,
            NumberWatchPref.BacklightTimeoutMs.id,
        )
        assertEquals(
            mapOf(
                SourceShapeNames.NUMERIC_VALUE to
                    "{\"value\":3000.0,\"unit\":\"ms\",\"min\":1.0,\"max\":10000.0}"
            ),
            timeout?.shapes(WatchSettingsPlugin.PROPERTY_VALUE),
        )
    }

    @Test
    fun aChoiceReadsAsTheNameOfTheChosenOption() = runTest {
        val plugin = WatchSettingsPlugin(FakeWatchPrefs())
        val textSize = plugin.instance(WatchSettingsPlugin.ITEM_TEXT, EnumWatchPref.TextSize.id)
        assertEquals(
            mapOf(
                SourceShapeNames.SHORT_TEXT to
                    "{\"text\":\"${EnumWatchPref.TextSize.defaultValue.displayName}\"}"
            ),
            textSize?.shapes(WatchSettingsPlugin.PROPERTY_VALUE),
        )
    }

    @Test
    fun aQuickLaunchButtonReadsAsTheAppItOpens() = runTest {
        val plugin = WatchSettingsPlugin(FakeWatchPrefs())
        val assigned = QuicklaunchWatchPref.QlSingleClickUp
        assertEquals(
            mapOf(SourceShapeNames.LONG_TEXT to "{\"text\":\"${assigned.defaultValue.uuid}\"}"),
            plugin.instance(WatchSettingsPlugin.ITEM_APP, assigned.id)
                ?.shapes(WatchSettingsPlugin.PROPERTY_VALUE),
        )
        // A button with nothing on it says so by having nothing to say.
        assertEquals(
            mapOf(SourceShapeNames.LONG_TEXT to "{\"text\":\"\"}"),
            plugin.instance(WatchSettingsPlugin.ITEM_APP, QuicklaunchWatchPref.QlUp.id)
                ?.shapes(WatchSettingsPlugin.PROPERTY_VALUE),
        )
    }

    @Test
    fun setOnWritesTheSwitch() = runTest {
        val prefs = FakeWatchPrefs()
        val plugin = WatchSettingsPlugin(prefs)
        val result = plugin.invoke(
            WatchSettingsPlugin.ACTION_SET_ON,
            args(PARAM_INSTANCE_ID to BoolWatchPref.Clock24h.id, "on" to "true"),
        )
        assertTrue(result.ok)
        assertEquals(BoolWatchPref.Clock24h, prefs.written.single().pref)
        assertEquals(true, prefs.written.single().value)
        assertEquals(
            "{\"value\":true}",
            plugin.instance(WatchSettingsPlugin.ITEM_SWITCH, BoolWatchPref.Clock24h.id)
                ?.shape(WatchSettingsPlugin.PROPERTY_VALUE, SourceShapeNames.BOOLEAN),
        )
    }

    @Test
    fun setValuePutsAnAppOnAQuickLaunchButton() = runTest {
        val prefs = FakeWatchPrefs()
        val plugin = WatchSettingsPlugin(prefs)
        val pref = QuicklaunchWatchPref.QlSingleClickDown
        val app = "8b1c6b0e-7d6a-4cf2-a9b2-2c3f8b1c6b0e"
        assertTrue(
            plugin.invoke(
                WatchSettingsPlugin.ACTION_SET_VALUE,
                args(PARAM_INSTANCE_ID to pref.id, "value" to app),
            ).ok
        )
        assertEquals(QuickLaunchSetting(enabled = true, uuid = Uuid.parse(app)), prefs.written.single().value)
        assertEquals(
            "{\"text\":\"$app\"}",
            plugin.instance(WatchSettingsPlugin.ITEM_APP, pref.id)
                ?.shape(WatchSettingsPlugin.PROPERTY_VALUE, SourceShapeNames.LONG_TEXT),
        )
    }

    @Test
    fun setValueWithNothingClearsAQuickLaunchButton() = runTest {
        val prefs = FakeWatchPrefs()
        val plugin = WatchSettingsPlugin(prefs)
        val pref = QuicklaunchWatchPref.QlSingleClickUp
        assertTrue(
            plugin.invoke(
                WatchSettingsPlugin.ACTION_SET_VALUE,
                args(PARAM_INSTANCE_ID to pref.id, "value" to ""),
            ).ok
        )
        assertEquals(QuickLaunchSetting(enabled = false, uuid = null), prefs.written.single().value)
        assertEquals(
            "{\"text\":\"\"}",
            plugin.instance(WatchSettingsPlugin.ITEM_APP, pref.id)
                ?.shape(WatchSettingsPlugin.PROPERTY_VALUE, SourceShapeNames.LONG_TEXT),
        )
    }

    @Test
    fun setValueRefusesSomethingThatIsNotAnApp() = runTest {
        val prefs = FakeWatchPrefs()
        val result = WatchSettingsPlugin(prefs).invoke(
            WatchSettingsPlugin.ACTION_SET_VALUE,
            args(PARAM_INSTANCE_ID to QuicklaunchWatchPref.QlSingleClickUp.id, "value" to "health"),
        )
        assertFalse(result.ok)
        assertEquals(PluginErrors.INVALID_ARGS, result.code)
        assertTrue(prefs.written.isEmpty())
    }

    @Test
    fun setOnRefusesASettingThatIsNotASwitch() = runTest {
        val prefs = FakeWatchPrefs()
        val result = WatchSettingsPlugin(prefs).invoke(
            WatchSettingsPlugin.ACTION_SET_ON,
            args(PARAM_INSTANCE_ID to EnumWatchPref.TextSize.id, "on" to "true"),
        )
        assertFalse(result.ok)
        assertEquals(PluginErrors.INVALID_ARGS, result.code)
        assertTrue(prefs.written.isEmpty())
    }

    @Test
    fun setValueTakesAChoiceByTheNameItIsShownUnder() = runTest {
        val prefs = FakeWatchPrefs()
        val plugin = WatchSettingsPlugin(prefs)
        val option = EnumWatchPref.TextSize.options.last()
        val result = plugin.invoke(
            WatchSettingsPlugin.ACTION_SET_VALUE,
            args(PARAM_INSTANCE_ID to EnumWatchPref.TextSize.id, "value" to option.displayName.lowercase()),
        )
        assertTrue(result.ok)
        assertEquals(option, prefs.written.single().value)
    }

    @Test
    fun setValueTakesANumberWithOrWithoutItsUnit() = runTest {
        val prefs = FakeWatchPrefs()
        val plugin = WatchSettingsPlugin(prefs)
        val result = plugin.invoke(
            WatchSettingsPlugin.ACTION_SET_VALUE,
            args(PARAM_INSTANCE_ID to NumberWatchPref.BacklightTimeoutMs.id, "value" to "5000 ms"),
        )
        assertTrue(result.ok)
        assertEquals(5000L, prefs.written.single().value)
    }

    @Test
    fun setValueRefusesANumberTheWatchWouldReject() = runTest {
        val prefs = FakeWatchPrefs()
        val result = WatchSettingsPlugin(prefs).invoke(
            WatchSettingsPlugin.ACTION_SET_VALUE,
            args(PARAM_INSTANCE_ID to NumberWatchPref.BacklightTimeoutMs.id, "value" to "99999"),
        )
        assertFalse(result.ok)
        assertEquals(PluginErrors.INVALID_ARGS, result.code)
        assertTrue(prefs.written.isEmpty())
    }

    @Test
    fun anUnknownSettingIsRejectedRatherThanWrittenToTheFirstOne() = runTest {
        val prefs = FakeWatchPrefs()
        val result = WatchSettingsPlugin(prefs).invoke(
            WatchSettingsPlugin.ACTION_SET_ON,
            args(PARAM_INSTANCE_ID to "no_such_setting", "on" to "true"),
        )
        assertFalse(result.ok)
        assertEquals(PluginErrors.INVALID_ARGS, result.code)
        assertTrue(prefs.written.isEmpty())
    }

    @Test
    fun theSwitchActionAsksForTheInstanceAndTheStateToPutItIn() {
        val setOn = WatchSettingsPlugin(FakeWatchPrefs()).action(WatchSettingsPlugin.ACTION_SET_ON)
        assertEquals(listOf(PARAM_INSTANCE_ID, "on"), setOn?.requiredParams)
        // `on` is a value, so nothing can fire this blind; a tile fills it from the checkbox
        // it is already drawing.
        assertFalse(setOn!!.bindable)
    }
}
