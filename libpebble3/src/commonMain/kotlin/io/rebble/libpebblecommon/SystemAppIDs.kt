package io.rebble.libpebblecommon

import kotlin.uuid.Uuid

public object SystemAppIDs {
    public val SYSTEM_APP_UUID: Uuid = Uuid.fromLongs(0, 0)
    public val SETTINGS_APP_UUID: Uuid = Uuid.parse("07e0d9cb-8957-4bf7-9d42-35bf47caadfe")
    public val CALENDAR_APP_UUID: Uuid = Uuid.parse("6c6c6fc2-1912-4d25-8396-3547d1dfac5b")
    public val WEATHER_APP_UUID: Uuid = Uuid.parse("61b22bc8-1e29-460d-a236-3fe409a439ff")
    public val HEALTH_APP_UUID: Uuid = Uuid.parse("36d8c6ed-4c83-4fa1-a9e2-8f12dc941f8c")
    public val MUSIC_APP_UUID: Uuid = Uuid.parse("1f03293d-47af-4f28-b960-f2b02a6dd757")
    public val NOTIFICATIONS_APP_UUID: Uuid = Uuid.parse("b2cae818-10f8-46df-ad2b-98ad2254a3c1")
    public val ALARMS_APP_UUID: Uuid = Uuid.parse("67a32d95-ef69-46d4-a0b9-854cc62f97f9")
    public val SMS_APP_UUID: Uuid = Uuid.parse("0863fc6a-66c5-4f62-ab8a-82ed00a98b5d")
    public val REMINDERS_APP_UUID: Uuid = Uuid.parse("42a07217-5491-4267-904a-d02a156752b6")
    public val WORKOUT_APP_UUID: Uuid = Uuid.parse("fef82c82-7176-4e22-88de-35a3fc18d43f")
    public val WATCHFACES_APP_UUID: Uuid = Uuid.parse("18e443ce-38fd-47c8-84d5-6d0c775fbe55")
    public val TICTOC_APP_UUID: Uuid = Uuid.parse("8f3c8686-31a1-4f5f-91f5-01600c9bdc59")
    public val KICKSTART_APP_UUID: Uuid = Uuid.parse("3af858c3-16cb-4561-91e7-f1ad2df8725f")
    public val MISSED_CALLS_APP_UUID: Uuid = Uuid.parse("af760190-bfc0-11e4-bb52-0800200c9a66")
    public val ANDROID_NOTIFICATIONS_UUID: Uuid = Uuid.parse("ed429c16-f674-4220-95da-454f303f15e2")
    public val TIMELINE_FUTURE_UUID: Uuid = Uuid.parse("79C76B48-6111-4E80-8DEB-3119EEBEF33E")
    public val TIMELINE_PAST_UUID: Uuid = Uuid.parse("daae3686-bff6-4ba5-921b-262f847bb6e8")
    public val TIMELINE_MENU_ENTRY_UUID: Uuid = Uuid.parse("426ccd53-b380-4d83-8d06-9893de3477ce")
    public val QUIET_TIME_TOGGLE_UUID: Uuid = Uuid.parse("2220d805-cf9a-4e12-92b9-5ca778aff6bb")
    public val BACKLIGHT_UUID: Uuid = Uuid.parse("d0f12e6c-97eb-2287-a2f5-115dfaa1d168")
    public val MOTION_BACKLIGHT_UUID: Uuid = Uuid.parse("d4f7be63-97e6-4952-b265-dd4bce11c155")
    public val AIRPLANE_MODE_UUID: Uuid = Uuid.parse("88c28c12-7f81-42db-aaa6-14ccef6f27e5")
    public val TOUCH_TOGGLE_UUID: Uuid = Uuid.parse("5cc5a4e8-150d-4ec3-9afc-965e3fdec3c3")
    public val CLEAR_NOTIFICATIONS_UUID: Uuid = Uuid.parse("d9c0d758-54bd-45b1-99ac-2a3a889350c9")

    /** Launchable system apps/shortcuts that aren't locker entries. */
    public val SYSTEM_SHORTCUTS: Map<Uuid, String> = mapOf(
        QUIET_TIME_TOGGLE_UUID to "Quiet Time",
        BACKLIGHT_UUID to "Backlight",
        MOTION_BACKLIGHT_UUID to "Motion Backlight",
        AIRPLANE_MODE_UUID to "Airplane Mode",
        TOUCH_TOGGLE_UUID to "Touch",
        TIMELINE_PAST_UUID to "Timeline Past",
        TIMELINE_FUTURE_UUID to "Timeline Future",
        CLEAR_NOTIFICATIONS_UUID to "Clear Notifications",
    )
}
