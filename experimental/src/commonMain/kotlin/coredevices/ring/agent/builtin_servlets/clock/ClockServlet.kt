package coredevices.ring.agent.builtin_servlets.clock

import coredevices.ring.agent.pebble_plugin.BuiltInServletPlugin
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlin.uuid.Uuid

/** Index prefix 00000000-0000-0000-0002- */
val CLOCK_PLUGIN_UUID: Uuid = Uuid.parse("00000000-0000-0000-0002-000000000001")

const val CLOCK_SERVLET_NAME = "builtin_clock"

object ClockServlet : BuiltInServletPlugin(
    name = CLOCK_SERVLET_NAME,
    tools = listOf(
        SetAlarmTool(),
        SetTimerTool(),
    ),
    pluginUuid = CLOCK_PLUGIN_UUID,
    pluginName = "Timers & Alarms (Index)",
    description = "Set alarms and timers on the phone.",
    callerPermission = PluginPermissions.SET_INDEX_ALARMS,
)