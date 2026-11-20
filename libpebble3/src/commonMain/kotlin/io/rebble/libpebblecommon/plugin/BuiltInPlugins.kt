package io.rebble.libpebblecommon.plugin

import kotlin.uuid.Uuid

/** Reserved namespace 00000000-0000-0000-0001-*; 0002 and 0009 are taken by app-side plugins. */
public object BuiltInPlugins {
    public val PHONE_STATE: Uuid = Uuid.parse("00000000-0000-0000-0001-000000000001")
    public val MUSIC: Uuid = Uuid.parse("00000000-0000-0000-0001-000000000003")
    public val CALENDAR: Uuid = Uuid.parse("00000000-0000-0000-0001-000000000004")
    public val NOTIFICATIONS: Uuid = Uuid.parse("00000000-0000-0000-0001-000000000005")
    public val WATCH_SETTINGS: Uuid = Uuid.parse("00000000-0000-0000-0001-000000000006")
    public val WATCH_STATE: Uuid = Uuid.parse("00000000-0000-0000-0001-000000000007")
    internal val FIND_MY_PHONE: Uuid = Uuid.parse("00000000-0000-0000-0001-000000000008")
}

public object WatchSettingsActions {
    public const val SET_ON: String = "set_on"
    public const val SET_VALUE: String = "set_value"
}
