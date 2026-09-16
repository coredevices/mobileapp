package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.connection.WatchPrefs
import io.rebble.libpebblecommon.database.dao.WatchPreference
import io.rebble.libpebblecommon.database.entity.BoolWatchPref
import io.rebble.libpebblecommon.database.entity.ColorWatchPref
import io.rebble.libpebblecommon.database.entity.EnumWatchPref
import io.rebble.libpebblecommon.database.entity.NumberWatchPref
import io.rebble.libpebblecommon.database.entity.QuickLaunchSetting
import io.rebble.libpebblecommon.database.entity.QuicklaunchWatchPref
import io.rebble.libpebblecommon.database.entity.RgbColorWatchPref
import io.rebble.libpebblecommon.database.entity.WatchPref
import io.rebble.libpebblecommon.plugin.ActionDeclaration.Companion.PARAM_INSTANCE_ID
import io.rebble.libpebblecommon.timeline.TimelineColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.Uuid

/**
 * What kind of thing a setting is, which is what decides the shape it reads as. One item each,
 * so every instance of a source reads the same way: a consumer subscribing to `watch/switch_setting`
 * knows it is drawing checkboxes, and one subscribing to `watch/number_setting` knows it is drawing
 * gauges.
 */
private enum class SettingKind(val item: String, val shape: String) {
    Switch(WatchSettingsPlugin.ITEM_SWITCH, SourceShapeNames.BOOLEAN),
    Number(WatchSettingsPlugin.ITEM_NUMBER, SourceShapeNames.NUMERIC_VALUE),
    Text(WatchSettingsPlugin.ITEM_TEXT, SourceShapeNames.SHORT_TEXT),
    // A uuid doesn't fit the few characters shortText promises.
    App(WatchSettingsPlugin.ITEM_APP, SourceShapeNames.LONG_TEXT),
}

private fun WatchPref<*>.kind(): SettingKind = when (this) {
    is BoolWatchPref -> SettingKind.Switch
    is NumberWatchPref -> SettingKind.Number
    is EnumWatchPref, is ColorWatchPref, is RgbColorWatchPref -> SettingKind.Text
    is QuicklaunchWatchPref -> SettingKind.App
}

/**
 * Built-in plugin for the watch's own settings — one instance per setting the app syncs, named
 * as the settings screen names it, valued as the watch has it.
 *
 * Settings are grouped by what they are: the switches, the numbers, the ones that read as a word
 * and the quick-launch buttons, which read as the uuid of the app they open, empty when nothing
 * is on them. Debug settings are left out: they are hidden in the app too.
 */
class WatchSettingsPlugin(
    private val watchPrefs: WatchPrefs,
) : Plugin {
    override val pluginUuid: Uuid = BUILT_IN_WATCH_SETTINGS_UUID
    override val name: String = "Watch Settings"

    override val sources: List<SourceDeclaration> = SettingKind.entries.map { kind ->
        SourceDeclaration(
            category = CATEGORY,
            items = listOf(kind.item),
            properties = mapOf(
                SourceInstance.PROPERTY_NAME to listOf(SourceShapeNames.SHORT_TEXT),
                PROPERTY_VALUE to listOf(kind.shape),
            ),
            supportsMultiple = true,
        )
    }

    override val actions: List<ActionDeclaration> = listOf(
        ActionDeclaration(
            name = ACTION_SET_ON,
            description = "Turn a setting that is a switch on or off.",
            parameters = schema(
                """
                "$PARAM_INSTANCE_ID":{"type":"string","description":"Id from $CATEGORY/$ITEM_SWITCH."},
                "on":{"type":"boolean"}
                """,
                required = listOf(PARAM_INSTANCE_ID, "on"),
            ),
            targets = listOf("$CATEGORY/$ITEM_SWITCH/$PROPERTY_VALUE"),
        ),
        ActionDeclaration(
            name = ACTION_SET_VALUE,
            description = "Set a setting to a value: `on`/`off` for a switch, one of the " +
                "choices by the name it is shown under, a number, or the uuid of the app a " +
                "quick-launch button should open — empty to leave the button unassigned.",
            parameters = schema(
                """
                "$PARAM_INSTANCE_ID":{"type":"string","description":"Id from any $CATEGORY source."},
                "value":{"type":"string"}
                """,
                required = listOf(PARAM_INSTANCE_ID, "value"),
            ),
            targets = SettingKind.entries.map { "$CATEGORY/${it.item}/$PROPERTY_VALUE" },
        ),
    )

    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
    ): Flow<SourceEnvelope> {
        val kind = SettingKind.entries.firstOrNull { it.item == item }
        if (category != CATEGORY || kind == null) return emptyFlow()
        return watchPrefs.watchPrefs.map { prefs -> toEnvelope(prefs, kind) }
    }

    override suspend fun invoke(action: String, args: JsonObject): ActionResult {
        val id = args[PARAM_INSTANCE_ID]?.jsonPrimitive?.content
        val preference = settings().firstOrNull { it.pref.id == id }
            ?: return ActionResult.error(PluginErrors.INVALID_ARGS, "no setting called '$id'")
        return when (action) {
            ACTION_SET_ON -> {
                val on = args["on"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                    ?: return ActionResult.error(PluginErrors.INVALID_ARGS, "'on' must be a boolean")
                setOn(preference, on)
            }
            ACTION_SET_VALUE -> {
                val value = args["value"]?.jsonPrimitive?.content
                    ?: return ActionResult.error(PluginErrors.INVALID_ARGS, "'value' is required")
                setValue(preference, value)
            }
            else -> ActionResult.error(PluginErrors.PLUGIN_UNAVAILABLE, "no action $action")
        }
    }

    private suspend fun settings() =
        watchPrefs.watchPrefs.first().filterNot { it.pref.isDebugSetting }

    private fun toEnvelope(prefs: List<WatchPreference<*>>, kind: SettingKind) = SourceEnvelope(
        pluginUuid = pluginUuid.toString(),
        instances = prefs
            .filterNot { it.pref.isDebugSetting }
            .filter { it.pref.kind() == kind }
            .map { preference ->
                SourceInstance(
                    instanceId = preference.pref.id,
                    properties = mapOf(
                        SourceInstance.PROPERTY_NAME to text(preference.pref.displayName),
                        PROPERTY_VALUE to value(preference),
                    ),
                )
            },
    )

    /** One shape: the one the setting actually is. */
    private fun value(preference: WatchPreference<*>): Map<String, JsonElement> =
        when (val pref = preference.pref) {
            is BoolWatchPref -> mapOf(
                SourceShapeNames.BOOLEAN to
                    encode(BooleanShape(pref.castParent(preference).valueOrDefault()))
            )
            is NumberWatchPref -> number(pref, pref.castParent(preference).valueOrDefault())
            is EnumWatchPref -> text(pref.castParent(preference).valueOrDefault().displayName)
            is RgbColorWatchPref -> text(hex(pref.castParent(preference).valueOrDefault()))
            is ColorWatchPref -> text(pref.castParent(preference).valueOrDefault().displayName)
            is QuicklaunchWatchPref -> mapOf(
                SourceShapeNames.LONG_TEXT to
                    encode(LongTextShape(pref.castParent(preference).valueOrDefault().app()))
            )
        }

    private fun setOn(preference: WatchPreference<*>, on: Boolean): ActionResult =
        when (val pref = preference.pref) {
            is BoolWatchPref -> write(pref, on, if (on) ON else OFF)
            else -> ActionResult.error(
                PluginErrors.INVALID_ARGS,
                "${pref.displayName} is not a switch",
            )
        }

    private fun setValue(preference: WatchPreference<*>, value: String): ActionResult =
        when (val pref = preference.pref) {
            is BoolWatchPref -> {
                val on = value.toSwitch() ?: return invalid(pref, value, "on or off")
                write(pref, on, if (on) ON else OFF)
            }
            is EnumWatchPref -> {
                val option = pref.options.firstOrNull { it.displayName.equals(value, true) }
                    ?: pref.options.firstOrNull { it.code.toString() == value }
                    ?: return invalid(pref, value, pref.options.joinToString { it.displayName })
                write(pref, option, option.displayName)
            }
            is NumberWatchPref -> {
                val number = value.trim().removeSuffix(pref.unit).trim().toLongOrNull()
                    ?: return invalid(pref, value, "a number")
                if (number < pref.min || number > pref.max) {
                    return invalid(pref, value, "${pref.min} to ${pref.max}")
                }
                write(pref, number, reading(pref, number))
            }
            is RgbColorWatchPref -> {
                val rgb = value.removePrefix("#").toUIntOrNull(radix = 16)
                    ?: return invalid(pref, value, "a hex colour like #FFBFA2")
                write(pref, rgb, hex(rgb))
            }
            is ColorWatchPref -> {
                val color = TimelineColor.entries
                    .firstOrNull { it.displayName.equals(value, true) }
                    ?: return invalid(pref, value, "a colour name")
                write(pref, color, color.displayName)
            }
            is QuicklaunchWatchPref -> {
                if (value.isBlank()) {
                    write(pref, QuickLaunchSetting(enabled = false, uuid = null), NOTHING)
                } else {
                    val app = runCatching { Uuid.parse(value.trim()) }.getOrNull()
                        ?: return invalid(pref, value, "an app uuid, or nothing at all")
                    write(pref, QuickLaunchSetting(enabled = true, uuid = app), app.toString())
                }
            }
        }

    private fun <T> write(pref: WatchPref<T>, value: T, reading: String): ActionResult {
        watchPrefs.setWatchPref(WatchPreference(pref, value))
        return ActionResult(
            ok = true,
            text = "${pref.displayName}: $reading",
            refreshed = listOf("$CATEGORY/${pref.kind().item}"),
        )
    }

    private fun invalid(pref: WatchPref<*>, value: String, expected: String) =
        ActionResult.error(
            PluginErrors.INVALID_ARGS,
            "'$value' is not a value for ${pref.displayName} — expected $expected",
        )

    /** The app on a quick-launch button, and nothing at all when there isn't one. */
    private fun QuickLaunchSetting.app(): String =
        if (enabled) uuid?.toString().orEmpty() else ""

    private fun number(pref: NumberWatchPref, value: Long) = mapOf(
        SourceShapeNames.NUMERIC_VALUE to encode(
            NumericValueShape(
                value = value.toDouble(),
                unit = pref.unit.ifEmpty { null },
                min = pref.min.toDouble(),
                max = pref.max.toDouble(),
            )
        ),
    )

    private fun reading(pref: NumberWatchPref, value: Long) =
        if (pref.unit.isEmpty()) "$value" else "$value ${pref.unit}"

    private fun text(value: String) =
        mapOf(SourceShapeNames.SHORT_TEXT to encode(ShortTextShape(value)))

    private fun hex(rgb: UInt) = "#" + rgb.toString(16).padStart(6, '0').uppercase()

    private fun String.toSwitch(): Boolean? = when (trim().lowercase()) {
        "on", "true", "1", "yes" -> true
        "off", "false", "0", "no" -> false
        else -> null
    }

    private inline fun <reified T> encode(value: T): JsonElement =
        Json.encodeToJsonElement(value)

    private fun schema(properties: String, required: List<String> = emptyList()): JsonObject {
        val requiredJson = required.joinToString(",") { "\"$it\"" }
        return Json.decodeFromString(
            JsonObject.serializer(),
            """{"type":"object","properties":{$properties},"required":[$requiredJson]}""",
        )
    }

    companion object {
        const val CATEGORY = "watch"
        const val ITEM_SWITCH = "switch_setting"
        const val ITEM_NUMBER = "number_setting"
        const val ITEM_TEXT = "text_setting"
        const val ITEM_APP = "app_setting"
        const val PROPERTY_VALUE = "value"
        const val ACTION_SET_ON = "set_on"
        const val ACTION_SET_VALUE = "set_value"

        private const val ON = "On"
        private const val OFF = "Off"
        private const val NOTHING = "Nothing"

        // Reserved built-in UUID namespace: prefix 00000000-0000-0000-0001-* for built-ins.
        val BUILT_IN_WATCH_SETTINGS_UUID: Uuid =
            Uuid.parse("00000000-0000-0000-0001-000000000006")
    }
}
