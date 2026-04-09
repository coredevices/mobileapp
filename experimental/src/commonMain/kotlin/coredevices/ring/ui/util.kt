package coredevices.ring.ui

import androidx.compose.runtime.Composable
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.am
import coreapp.ring.generated.resources.april
import coreapp.ring.generated.resources.april_short
import coreapp.ring.generated.resources.august
import coreapp.ring.generated.resources.august_short
import coreapp.ring.generated.resources.december
import coreapp.ring.generated.resources.december_short
import coreapp.ring.generated.resources.february
import coreapp.ring.generated.resources.february_short
import coreapp.ring.generated.resources.friday
import coreapp.ring.generated.resources.january
import coreapp.ring.generated.resources.january_short
import coreapp.ring.generated.resources.july
import coreapp.ring.generated.resources.july_short
import coreapp.ring.generated.resources.june
import coreapp.ring.generated.resources.june_short
import coreapp.ring.generated.resources.march
import coreapp.ring.generated.resources.march_short
import coreapp.ring.generated.resources.may
import coreapp.ring.generated.resources.may_short
import coreapp.ring.generated.resources.monday
import coreapp.ring.generated.resources.november
import coreapp.ring.generated.resources.november_short
import coreapp.ring.generated.resources.october
import coreapp.ring.generated.resources.october_short
import coreapp.ring.generated.resources.pm
import coreapp.ring.generated.resources.saturday
import coreapp.ring.generated.resources.september
import coreapp.ring.generated.resources.september_short
import coreapp.ring.generated.resources.sunday
import coreapp.ring.generated.resources.thursday
import coreapp.ring.generated.resources.today
import coreapp.ring.generated.resources.tomorrow
import coreapp.ring.generated.resources.tuesday
import coreapp.ring.generated.resources.wednesday
import coreapp.ring.generated.resources.yesterday

@Composable
fun dayNames() = DayOfWeekNames(
    monday = stringResource(Res.string.monday),
    tuesday = stringResource(Res.string.tuesday),
    wednesday = stringResource(Res.string.wednesday),
    thursday = stringResource(Res.string.thursday),
    friday = stringResource(Res.string.friday),
    saturday = stringResource(Res.string.saturday),
    sunday = stringResource(Res.string.sunday)
)

suspend fun dayNamesStatic() = DayOfWeekNames(
    monday = getString(Res.string.monday),
    tuesday = getString(Res.string.tuesday),
    wednesday = getString(Res.string.wednesday),
    thursday = getString(Res.string.thursday),
    friday = getString(Res.string.friday),
    saturday = getString(Res.string.saturday),
    sunday = getString(Res.string.sunday)
)

@Composable
fun monthNames() = MonthNames(
    january = stringResource(Res.string.january),
    february = stringResource(Res.string.february),
    march = stringResource(Res.string.march),
    april = stringResource(Res.string.april),
    may = stringResource(Res.string.may),
    june = stringResource(Res.string.june),
    july = stringResource(Res.string.july),
    august = stringResource(Res.string.august),
    september = stringResource(Res.string.september),
    october = stringResource(Res.string.october),
    november = stringResource(Res.string.november),
    december = stringResource(Res.string.december)
)

suspend fun monthNamesShortStatic() = MonthNames(
    january = getString(Res.string.january_short),
    february = getString(Res.string.february_short),
    march = getString(Res.string.march_short),
    april = getString(Res.string.april_short),
    may = getString(Res.string.may_short),
    june = getString(Res.string.june_short),
    july = getString(Res.string.july_short),
    august = getString(Res.string.august_short),
    september = getString(Res.string.september_short),
    october = getString(Res.string.october_short),
    november = getString(Res.string.november_short),
    december = getString(Res.string.december_short)
)

@Composable
fun monthNamesShort() = MonthNames(
    january = stringResource(Res.string.january_short),
    february = stringResource(Res.string.february_short),
    march = stringResource(Res.string.march_short),
    april = stringResource(Res.string.april_short),
    may = stringResource(Res.string.may_short),
    june = stringResource(Res.string.june_short),
    july = stringResource(Res.string.july_short),
    august = stringResource(Res.string.august_short),
    september = stringResource(Res.string.september_short),
    october = stringResource(Res.string.october_short),
    november = stringResource(Res.string.november_short),
    december = stringResource(Res.string.december_short)
)

expect fun isLocale24HourFormat(): Boolean

object UITimeUtil {
    private suspend fun amPm() = Pair(getString(Res.string.am), getString(Res.string.pm))
    suspend fun shortDateFormat(): DateTimeFormat<LocalDate> {
        val monthNamesShort = monthNamesShortStatic()
        return LocalDate.Format {
            dayOfMonth()
            char(' ')
            monthName(monthNamesShort)
        }
    }
    suspend fun timeFormat(): DateTimeFormat<LocalTime> {
        val amPm = amPm()
        val is24Hour = isLocale24HourFormat()
        return LocalTime.Format {
            if (is24Hour) {
                hour()
            } else {
                amPmHour(Padding.NONE)
            }
            char(':')
            minute()
            if (!is24Hour) {
                char(' ')
                amPmMarker(amPm.first, amPm.second)
            }
        }
    }
    suspend fun humanDate(date: LocalDate): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val dayNames = dayNamesStatic()
        when {
            now == date -> return getString(Res.string.today)
            LocalDate(now.year, now.month, now.dayOfMonth + 1) == date -> return getString(Res.string.tomorrow)
            LocalDate(now.year, now.month, now.dayOfMonth - 1) == date -> return getString(Res.string.yesterday)
            now.daysUntil(date) < 7 -> return date.format(LocalDate.Format { dayOfWeek(dayNames) })
            else -> return date.format(shortDateFormat())
        }
    }
}