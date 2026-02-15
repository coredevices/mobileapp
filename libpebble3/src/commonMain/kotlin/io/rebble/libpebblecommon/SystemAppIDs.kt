package io.rebble.libpebblecommon

import kotlin.uuid.Uuid

object SystemAppIDs {
    val SYSTEM_APP_UUID = Uuid.fromLongs(0, 0)
    val SETTINGS_APP_UUID = Uuid.parse("07e0d9cb-8957-4bf7-9d42-35bf47caadfe")
    val CALENDAR_APP_UUID = Uuid.parse("6c6c6fc2-1912-4d25-8396-3547d1dfac5b")
    val WEATHER_APP_UUID = Uuid.parse("61b22bc8-1e29-460d-a236-3fe409a439ff")
    val HEALTH_APP_UUID = Uuid.parse("36d8c6ed-4c83-4fa1-a9e2-8f12dc941f8c")
    val MUSIC_APP_UUID = Uuid.parse("1f03293d-47af-4f28-b960-f2b02a6dd757")
    val NOTIFICATIONS_APP_UUID = Uuid.parse("b2cae818-10f8-46df-ad2b-98ad2254a3c1")
    val ALARMS_APP_UUID = Uuid.parse("67a32d95-ef69-46d4-a0b9-854cc62f97f9")
    val SMS_APP_UUID = Uuid.parse("0863fc6a-66c5-4f62-ab8a-82ed00a98b5d")
    val REMINDERS_APP_UUID = Uuid.parse("42a07217-5491-4267-904a-d02a156752b6")
    val WORKOUT_APP_UUID = Uuid.parse("fef82c82-7176-4e22-88de-35a3fc18d43f")
    val WATCHFACES_APP_UUID = Uuid.parse("18e443ce-38fd-47c8-84d5-6d0c775fbe55")
    val TICTOC_APP_UUID = Uuid.parse("8f3c8686-31a1-4f5f-91f5-01600c9bdc59")
    val KICKSTART_APP_UUID = Uuid.parse("3af858c3-16cb-4561-91e7-f1ad2df8725f")
    val MISSED_CALLS_APP_UUID = Uuid.parse("af760190-bfc0-11e4-bb52-0800200c9a66")
    val ANDROID_NOTIFICATIONS_UUID = Uuid.parse("ed429c16-f674-4220-95da-454f303f15e2")
    val TIMELINE_FUTURE_UUID = Uuid.parse("79C76B48-6111-4E80-8DEB-3119EEBEF33E")
    val TIMELINE_PAST_UUID = Uuid.parse("daae3686-bff6-4ba5-921b-262f847bb6e8")
    val QUIET_TIME_TOGGLE_UUID = Uuid.parse("2220d805-cf9a-4e12-92b9-5ca778aff6bb")
    val BACKLIGHT_UUID = Uuid.parse("d0f12e6c-97eb-2287-a2f5-115dfaa1d168")
    val MOTION_BACKLIGHT_UUID = Uuid.parse("d4f7be63-97e6-4952-b265-dd4bce11c155")
    val AIRPLANE_MODE_UUID = Uuid.parse("88c28c12-7f81-42db-aaa6-14ccef6f27e5")
}