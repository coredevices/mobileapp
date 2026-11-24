package coredevices.pebble.ui

import io.rebble.libpebblecommon.database.dao.WatchPreference
import io.rebble.libpebblecommon.database.entity.BoolWatchPref
import io.rebble.libpebblecommon.database.entity.DarkModeSetting
import io.rebble.libpebblecommon.database.entity.EnumWatchPref
import io.rebble.libpebblecommon.database.entity.QuietTimeSchedule
import io.rebble.libpebblecommon.database.entity.ScheduleWatchPref
import kotlin.test.Test
import kotlin.test.assertEquals

class QuietTimeSchedulePrefsTest {
    private fun prefs(weekday: Boolean?, weekend: Boolean?): List<WatchPreference<*>> = listOf(
        WatchPreference(BoolWatchPref.QuietTimeManuallyEnabled, null),
        WatchPreference(BoolWatchPref.QuietTimeWeekdayScheduleEnabled, weekday),
        WatchPreference(BoolWatchPref.QuietTimeWeekendScheduleEnabled, weekend),
        WatchPreference(EnumWatchPref.QuietTimeShowNotifications, null),
        WatchPreference(ScheduleWatchPref.QuietTimeWeekdaySchedule, QuietTimeSchedule(22, 0, 7, 0)),
        WatchPreference(ScheduleWatchPref.QuietTimeWeekendSchedule, null),
    )

    @Test
    fun hoursAreHiddenWhileTheScheduleIsOff() {
        assertEquals(
            listOf(
                BoolWatchPref.QuietTimeManuallyEnabled,
                BoolWatchPref.QuietTimeWeekdayScheduleEnabled,
                BoolWatchPref.QuietTimeWeekendScheduleEnabled,
                EnumWatchPref.QuietTimeShowNotifications,
            ),
            prefs(weekday = false, weekend = null).groupScheduleHours().map { it.pref },
        )
    }

    @Test
    fun hoursFollowTheToggleTheyBelongTo() {
        val grouped = prefs(weekday = true, weekend = true).groupScheduleHours()
        assertEquals(
            listOf(
                BoolWatchPref.QuietTimeManuallyEnabled,
                BoolWatchPref.QuietTimeWeekdayScheduleEnabled,
                ScheduleWatchPref.QuietTimeWeekdaySchedule,
                BoolWatchPref.QuietTimeWeekendScheduleEnabled,
                ScheduleWatchPref.QuietTimeWeekendSchedule,
                EnumWatchPref.QuietTimeShowNotifications,
            ),
            grouped.map { it.pref },
        )
        assertEquals(
            QuietTimeSchedule(22, 0, 7, 0),
            grouped.first { it.pref == ScheduleWatchPref.QuietTimeWeekdaySchedule }.value,
        )
    }

    @Test
    fun onlyTheEnabledSchedulesHoursAreShown() {
        assertEquals(
            listOf(
                BoolWatchPref.QuietTimeManuallyEnabled,
                BoolWatchPref.QuietTimeWeekdayScheduleEnabled,
                BoolWatchPref.QuietTimeWeekendScheduleEnabled,
                ScheduleWatchPref.QuietTimeWeekendSchedule,
                EnumWatchPref.QuietTimeShowNotifications,
            ),
            prefs(weekday = null, weekend = true).groupScheduleHours().map { it.pref },
        )
    }

    @Test
    fun darkModeHoursShowOnlyWhenScheduled() {
        fun grouped(mode: DarkModeSetting) = listOf(
            WatchPreference(EnumWatchPref.DarkMode, mode),
            WatchPreference(ScheduleWatchPref.DarkModeSchedule, null),
        ).groupScheduleHours().map { it.pref }
        assertEquals(listOf(EnumWatchPref.DarkMode), grouped(DarkModeSetting.On))
        assertEquals(listOf(EnumWatchPref.DarkMode, ScheduleWatchPref.DarkModeSchedule), grouped(DarkModeSetting.Scheduled))
    }
}
