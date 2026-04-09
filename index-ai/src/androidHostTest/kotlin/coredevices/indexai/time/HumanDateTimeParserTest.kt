package coredevices.indexai.time

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class HumanDateTimeParserTest {

    // Fixed reference time: Wednesday, January 15, 2025 at 10:30 AM
    private val referenceDateTime = LocalDateTime(2025, 1, 15, 10, 30)
    private val parser = HumanDateTimeParser(object : Clock {
        override fun now(): kotlin.time.Instant {
            return referenceDateTime.toInstant(TimeZone.UTC)
        }
    }, TimeZone.UTC)

    // ===== RELATIVE DURATION TESTS =====

    @Test
    fun testRelativeInMinutes() {
        val result = parser.parse("in 30 minutes")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(30.minutes, result.duration)
    }

    @Test
    fun testRelativeInHours() {
        val result = parser.parse("in 3 hours")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(3.hours, result.duration)
    }

    @Test
    fun testRelativeInDays() {
        val result = parser.parse("in 5 days")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(5.days, result.duration)
    }

    @Test
    fun testRelativeInWeeks() {
        val result = parser.parse("in 2 weeks")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(14.days, result.duration)
    }

    @Test
    fun testRelativeInSeconds() {
        val result = parser.parse("in 45 seconds")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(45.seconds, result.duration)
    }

    @Test
    fun testRelativeSingularUnit() {
        val result = parser.parse("in 1 hour")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(1.hours, result.duration)
    }

    @Test
    fun testRelativeFromNow() {
        val result = parser.parse("3 hours from now")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(3.hours, result.duration)
    }

    @Test
    fun testRelativeMinutesFromNow() {
        val result = parser.parse("45 minutes from now")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(45.minutes, result.duration)
    }

    // ===== HUMAN QUANTIFIER TESTS =====

    @Test
    fun testRelativeAnHour() {
        val result = parser.parse("in an hour")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(1.hours, result.duration)
    }

    @Test
    fun testRelativeADay() {
        val result = parser.parse("in a day")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(1.days, result.duration)
    }

    @Test
    fun testRelativeAMinute() {
        val result = parser.parse("in a minute")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(1.minutes, result.duration)
    }

    @Test
    fun testRelativeOneHour() {
        val result = parser.parse("in one hour")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(1.hours, result.duration)
    }

    @Test
    fun testRelativeCoupleHours() {
        val result = parser.parse("in a couple hours")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(2.hours, result.duration)
    }

    @Test
    fun testRelativeCoupleOfHours() {
        val result = parser.parse("in a couple of hours")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(2.hours, result.duration)
    }

    @Test
    fun testRelativeFewMinutes() {
        val result = parser.parse("in a few minutes")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(3.minutes, result.duration)
    }

    @Test
    fun testRelativeSeveralDays() {
        val result = parser.parse("in several days")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(5.days, result.duration)
    }

    @Test
    fun testRelativeHalfAnHour() {
        val result = parser.parse("in half an hour")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(30.minutes, result.duration)
    }

    @Test
    fun testRelativeHalfADay() {
        val result = parser.parse("in half a day")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(12.hours, result.duration)
    }

    @Test
    fun testRelativeHalfAnHourFromNow() {
        val result = parser.parse("half an hour from now")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(30.minutes, result.duration)
    }

    @Test
    fun testRelativeCoupleMinutesFromNow() {
        val result = parser.parse("a couple minutes from now")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(2.minutes, result.duration)
    }

    @Test
    fun testRelativeFewHoursFromNow() {
        val result = parser.parse("a few hours from now")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(3.hours, result.duration)
    }

    // ===== COMPOUND DURATION TESTS =====

    @Test
    fun testCompoundInHoursAndMinutes() {
        val result = parser.parse("in 1 hour and 10 minutes")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(70.minutes, result.duration)
    }

    @Test
    fun testCompoundInHoursMinutesNoAnd() {
        val result = parser.parse("in 1 hour 10 minutes")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(70.minutes, result.duration)
    }

    @Test
    fun testCompoundWordHoursAndMinutes() {
        val result = parser.parse("in one hour and ten minutes")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(70.minutes, result.duration)
    }

    @Test
    fun testCompoundWordHoursMinutesNoAnd() {
        val result = parser.parse("one hour ten minutes")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(70.minutes, result.duration)
    }

    @Test
    fun testCompoundWordHoursMinutesCommaNoAnd() {
        val result = parser.parse("one hour, ten minutes")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(70.minutes, result.duration)
    }

    @Test
    fun testCompoundTwoHoursThirtyMinutes() {
        val result = parser.parse("in 2 hours and 30 minutes")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(150.minutes, result.duration)
    }

    @Test
    fun testCompoundTwoHoursThirtyMinutesNoAnd() {
        val result = parser.parse("in 2 hours 30 minutes")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(150.minutes, result.duration)
    }

    @Test
    fun testCompoundFromNow() {
        val result = parser.parse("1 hour and 30 minutes from now")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(90.minutes, result.duration)
    }

    @Test
    fun testCompoundFromNowNoAnd() {
        val result = parser.parse("1 hour 30 minutes from now")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(90.minutes, result.duration)
    }

    @Test
    fun testCompoundDayAndHours() {
        val result = parser.parse("in 1 day and 3 hours")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(27.hours, result.duration)
    }

    @Test
    fun testCompoundMinutesAndSeconds() {
        val result = parser.parse("in 45 minutes and 30 seconds")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(45.minutes + 30.seconds, result.duration)
    }

    @Test
    fun testCompoundStandaloneNoIn() {
        val result = parser.parse("1 hour 10 minutes")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(70.minutes, result.duration)
    }

    // ===== WORD NUMBER TESTS =====

    @Test
    fun testRelativeInTwoHours() {
        val result = parser.parse("in two hours")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(2.hours, result.duration)
    }

    @Test
    fun testRelativeInThreeMinutes() {
        val result = parser.parse("in three minutes")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(3.minutes, result.duration)
    }

    @Test
    fun testRelativeInFiveDays() {
        val result = parser.parse("in five days")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(5.days, result.duration)
    }

    @Test
    fun testRelativeInTenMinutes() {
        val result = parser.parse("in ten minutes")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(10.minutes, result.duration)
    }

    @Test
    fun testRelativeInTwelveHours() {
        val result = parser.parse("in twelve hours")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(12.hours, result.duration)
    }

    @Test
    fun testRelativeInTwentyMinutes() {
        val result = parser.parse("in twenty minutes")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(20.minutes, result.duration)
    }

    @Test
    fun testRelativeThreeHoursFromNow() {
        val result = parser.parse("three hours from now")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(3.hours, result.duration)
    }

    @Test
    fun testRelativeStandaloneTwoMinutes() {
        val result = parser.parse("two minutes")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(2.minutes, result.duration)
    }

    @Test
    fun testRelativeInSixMonths() {
        val result = parser.parse("in six months")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(DatePeriod(months = 6), result.period)
    }

    @Test
    fun testRelativeInTwoWeeks() {
        val result = parser.parse("in two weeks")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(14.days, result.duration)
    }

    @Test
    fun testParseFromMessageExtractsWordNumber() {
        val result = parser.parseFromMessage("remind me in two hours to call mom")
        assertIs<InterpretedDateTime.Relative>(result?.dateTime)
        assertEquals(2.hours, (result?.dateTime as InterpretedDateTime.Relative).duration)
        assertEquals("in two hours", result.matchedText.lowercase())
    }

    // ===== STANDALONE DURATION TESTS =====

    @Test
    fun testRelativeStandaloneMinutes() {
        val result = parser.parse("5 minutes")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(5.minutes, result.duration)
    }

    @Test
    fun testRelativeStandaloneHours() {
        val result = parser.parse("2 hours")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(2.hours, result.duration)
    }

    @Test
    fun testRelativeStandaloneSeconds() {
        val result = parser.parse("30 seconds")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(30.seconds, result.duration)
    }

    @Test
    fun testRelativeStandaloneDays() {
        val result = parser.parse("3 days")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(3.days, result.duration)
    }

    @Test
    fun testRelativeStandaloneSingular() {
        val result = parser.parse("1 hour")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(1.hours, result.duration)
    }

    @Test
    fun testRelativeStandaloneAnHour() {
        val result = parser.parse("an hour")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(1.hours, result.duration)
    }

    @Test
    fun testRelativeStandaloneCoupleMinutes() {
        val result = parser.parse("a couple minutes")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(2.minutes, result.duration)
    }

    // ===== RELATIVE MONTH/YEAR TESTS =====

    @Test
    fun testRelativeInMonths() {
        val result = parser.parse("in 6 months")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(DatePeriod(months = 6), result.period)
        assertEquals(Duration.ZERO, result.duration)
    }

    @Test
    fun testRelativeInAMonth() {
        val result = parser.parse("in a month")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(DatePeriod(months = 1), result.period)
    }

    @Test
    fun testRelativeInAYear() {
        val result = parser.parse("in 1 year")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(DatePeriod(years = 1), result.period)
    }

    @Test
    fun testRelativeInYears() {
        val result = parser.parse("in 2 years")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(DatePeriod(years = 2), result.period)
    }

    @Test
    fun testRelativeMonthsFromNow() {
        val result = parser.parse("6 months from now")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(DatePeriod(months = 6), result.period)
    }

    @Test
    fun testRelativeAYearFromNow() {
        val result = parser.parse("a year from now")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(DatePeriod(years = 1), result.period)
    }

    @Test
    fun testRelativeStandaloneMonths() {
        val result = parser.parse("3 months")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(DatePeriod(months = 3), result.period)
    }

    // ===== ABSOLUTE TIME TESTS =====

    @Test
    fun testAbsoluteTimeAt3pm() {
        val result = parser.parse("at 3pm")
        assertIs<InterpretedDateTime.AbsoluteTime>(result)
        assertEquals(LocalTime(15, 0), result.time)
    }

    @Test
    fun testAbsoluteTimeAt3am() {
        val result = parser.parse("at 3am")
        assertIs<InterpretedDateTime.AbsoluteTime>(result)
        assertEquals(LocalTime(3, 0), result.time)
    }

    @Test
    fun testAbsoluteTimeAt12pm() {
        val result = parser.parse("at 12pm")
        assertIs<InterpretedDateTime.AbsoluteTime>(result)
        assertEquals(LocalTime(12, 0), result.time)
    }

    @Test
    fun testAbsoluteTimeAt12am() {
        val result = parser.parse("at 12am")
        assertIs<InterpretedDateTime.AbsoluteTime>(result)
        assertEquals(LocalTime(0, 0), result.time)
    }

    @Test
    fun testAbsoluteTimeWithMinutes() {
        val result = parser.parse("at 3:30pm")
        assertIs<InterpretedDateTime.AbsoluteTime>(result)
        assertEquals(LocalTime(15, 30), result.time)
    }

    @Test
    fun testAbsoluteTime24HourFormat() {
        val result = parser.parse("at 15:00")
        assertIs<InterpretedDateTime.AbsoluteTime>(result)
        assertEquals(LocalTime(15, 0), result.time)
    }

    @Test
    fun testAbsoluteTime24HourFormatWithMinutes() {
        val result = parser.parse("at 08:45")
        assertIs<InterpretedDateTime.AbsoluteTime>(result)
        assertEquals(LocalTime(8, 45), result.time)
    }

    @Test
    fun testAbsoluteTimeWithoutAt() {
        val result = parser.parse("3pm")
        assertIs<InterpretedDateTime.AbsoluteTime>(result)
        assertEquals(LocalTime(15, 0), result.time)
    }

    @Test
    fun testAbsoluteTime24HourWithoutAt() {
        val result = parser.parse("15:00")
        assertIs<InterpretedDateTime.AbsoluteTime>(result)
        assertEquals(LocalTime(15, 0), result.time)
    }

    @Test
    fun testAbsoluteTimeWithSpaceBeforeAmPm() {
        val result = parser.parse("at 3:30 pm")
        assertIs<InterpretedDateTime.AbsoluteTime>(result)
        assertEquals(LocalTime(15, 30), result.time)
    }

    @Test
    fun testAbsoluteTimeWithDottedAmPm() {
        val result = parser.parse("at 3:30 p.m.")
        assertIs<InterpretedDateTime.AbsoluteTime>(result)
        assertEquals(LocalTime(15, 30), result.time)
    }

    @Test
    fun testAbsoluteTimeWithDottedAm() {
        val result = parser.parse("at 9 a.m.")
        assertIs<InterpretedDateTime.AbsoluteTime>(result)
        assertEquals(LocalTime(9, 0), result.time)
    }

    @Test
    fun testAbsoluteDateTimeWithDottedPm() {
        val result = parser.parse("tomorrow at 3 p.m.")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 16, 15, 0), result.dateTime)
    }

    @Test
    fun testAbsoluteFuzzyDateTimeMorning() {
        val result = parser.parse("tomorrow morning")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 16, 9, 0), result.dateTime)
    }

    @Test
    fun testAbsoluteFuzzyDateTimeThisMorning() {
        val result = parser.parse("this morning")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 15, 9, 0), result.dateTime)
    }

    @Test
    fun testAbsoluteFuzzyDateTimeEvening() {
        val result = parser.parse("tomorrow evening")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 16, 19, 0), result.dateTime)
    }

    @Test
    fun testAbsoluteFuzzyDateTimeThisEvening() {
        val result = parser.parse("this evening")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 15, 19, 0), result.dateTime)
    }

    @Test
    fun testAbsoluteFuzzyDateTimeAfternoon() {
        val result = parser.parse("tomorrow afternoon")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 16, 14, 0), result.dateTime)
    }

    @Test
    fun testAbsoluteFuzzyDateTimeThisAfternoon() {
        val result = parser.parse("this afternoon")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 15, 14, 0), result.dateTime)
    }

    @Test
    fun testAbsoluteFuzzyDateTimeNight() {
        val result = parser.parse("tomorrow night")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 16, 21, 0), result.dateTime)
    }

    // ===== ABSOLUTE DATE TESTS =====

    @Test
    fun testAbsoluteDateToday() {
        val result = parser.parse("today")
        assertIs<InterpretedDateTime.AbsoluteDate>(result)
        assertEquals(LocalDate(2025, 1, 15), result.date)
    }

    @Test
    fun testAbsoluteDateTomorrow() {
        val result = parser.parse("tomorrow")
        assertIs<InterpretedDateTime.AbsoluteDate>(result)
        assertEquals(LocalDate(2025, 1, 16), result.date)
    }

    @Test
    fun testAbsoluteDateNextMonday() {
        // Reference is Wednesday Jan 15, so next Monday is Jan 20
        val result = parser.parse("next monday")
        assertIs<InterpretedDateTime.AbsoluteDate>(result)
        assertEquals(LocalDate(2025, 1, 20), result.date)
    }

    @Test
    fun testAbsoluteDateOnFriday() {
        // Reference is Wednesday Jan 15, so Friday is Jan 17
        val result = parser.parse("on friday")
        assertIs<InterpretedDateTime.AbsoluteDate>(result)
        assertEquals(LocalDate(2025, 1, 17), result.date)
    }

    @Test
    fun testAbsoluteDateSameDay() {
        // Reference is Wednesday, asking for Wednesday should go to next week
        val result = parser.parse("wednesday")
        assertIs<InterpretedDateTime.AbsoluteDate>(result)
        assertEquals(LocalDate(2025, 1, 22), result.date)
    }

    @Test
    fun testAbsoluteDateMonthDay() {
        val result = parser.parse("february 14")
        assertIs<InterpretedDateTime.AbsoluteDate>(result)
        assertEquals(LocalDate(2025, 2, 14), result.date)
    }

    @Test
    fun testAbsoluteDateMonthDayWithOrdinal() {
        val result = parser.parse("march 1st")
        assertIs<InterpretedDateTime.AbsoluteDate>(result)
        assertEquals(LocalDate(2025, 3, 1), result.date)
    }

    @Test
    fun testAbsoluteDatePastDateRollsToNextYear() {
        // January 10 is in the past (reference is Jan 15), should roll to 2026
        val result = parser.parse("january 10")
        assertIs<InterpretedDateTime.AbsoluteDate>(result)
        assertEquals(LocalDate(2026, 1, 10), result.date)
    }

    @Test
    fun testAbsoluteDateMonthDayCommaYear() {
        val result = parser.parse("August 24, 2026")
        assertIs<InterpretedDateTime.AbsoluteDate>(result)
        assertEquals(LocalDate(2026, 8, 24), result.date)
    }

    @Test
    fun testAbsoluteDateMonthDayYear() {
        val result = parser.parse("august 24 2026")
        assertIs<InterpretedDateTime.AbsoluteDate>(result)
        assertEquals(LocalDate(2026, 8, 24), result.date)
    }

    @Test
    fun testAbsoluteDateMonthDayOrdinalCommaYear() {
        val result = parser.parse("march 1st, 2026")
        assertIs<InterpretedDateTime.AbsoluteDate>(result)
        assertEquals(LocalDate(2026, 3, 1), result.date)
    }

    @Test
    fun testAbsoluteDateWithExplicitYearDoesNotRollForward() {
        // January 10, 2025 is in the past but year is explicit — respect it
        val result = parser.parse("january 10, 2025")
        assertIs<InterpretedDateTime.AbsoluteDate>(result)
        assertEquals(LocalDate(2025, 1, 10), result.date)
    }

    @Test
    fun testAbsoluteDateNumericFormat() {
        val result = parser.parse("2/14")
        assertIs<InterpretedDateTime.AbsoluteDate>(result)
        assertEquals(LocalDate(2025, 2, 14), result.date)
    }

    // ===== ABSOLUTE DATETIME TESTS =====

    @Test
    fun testAbsoluteDateTimeTomorrowAt3pm() {
        val result = parser.parse("tomorrow at 3pm")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 16, 15, 0), result.dateTime)
    }

    @Test
    fun testAbsoluteDateTimeTodayAt3pm() {
        val result = parser.parse("today at 3pm")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 15, 15, 0), result.dateTime)
    }

    @Test
    fun testAbsoluteDateTime3pmToday() {
        val result = parser.parse("at 3pm today")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 15, 15, 0), result.dateTime)
    }

    @Test
    fun testAbsoluteDateTime3pmTodayFullTime() {
        val result = parser.parse("3:00PM today")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 15, 15, 0), result.dateTime)
    }

    @Test
    fun testAbsoluteDateTimeAt3pmTomorrow() {
        val result = parser.parse("at 3pm tomorrow")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 16, 15, 0), result.dateTime)
    }

    @Test
    fun testAbsoluteDateTimeNextMondayAt9am() {
        val result = parser.parse("next monday at 9am")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 20, 9, 0), result.dateTime)
    }

    @Test
    fun testAbsoluteDateTimeOnFridayAt3pm() {
        val result = parser.parse("on friday at 3pm")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 17, 15, 0), result.dateTime)
    }

    @Test
    fun testAbsoluteDateTimeAt3pmNextMonday() {
        val result = parser.parse("at 3pm next monday")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 20, 15, 0), result.dateTime)
    }

    @Test
    fun testAbsoluteDateTimeMonthDayAtTime() {
        val result = parser.parse("february 14 at 7pm")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 2, 14, 19, 0), result.dateTime)
    }

    @Test
    fun testAbsoluteDateTimeAtTimeOnMonthDay() {
        val result = parser.parse("at 7pm on february 14")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 2, 14, 19, 0), result.dateTime)
    }

    @Test
    fun testAbsoluteDateTimeNumericDateAtTime() {
        val result = parser.parse("2/14 at 7pm")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 2, 14, 19, 0), result.dateTime)
    }

    @Test
    fun testAbsoluteDateTimeWithOrdinalSuffix() {
        val result = parser.parse("january 20th at 3:30pm")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 20, 15, 30), result.dateTime)
    }

    // ===== CASE INSENSITIVITY TESTS =====

    @Test
    fun testCaseInsensitiveUppercase() {
        val result = parser.parse("TOMORROW AT 3PM")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 16, 15, 0), result.dateTime)
    }

    @Test
    fun testCaseInsensitiveMixed() {
        val result = parser.parse("Next Monday at 9AM")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 20, 9, 0), result.dateTime)
    }

    // ===== EDGE CASES AND INVALID INPUT =====

    @Test
    fun testInvalidInputReturnsNull() {
        val result = parser.parse("gibberish text")
        assertNull(result)
    }

    @Test
    fun testEmptyStringReturnsNull() {
        val result = parser.parse("")
        assertNull(result)
    }

    @Test
    fun testWhitespaceHandling() {
        val result = parser.parse("  in 3 hours  ")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(3.hours, result.duration)
    }

    // ===== ALL DAYS OF WEEK =====

    @Test
    fun testAllDaysOfWeek() {
        val days = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
        for (day in days) {
            val result = parser.parse(day)
            assertIs<InterpretedDateTime.AbsoluteDate>(result)
        }
    }

    // ===== ALL MONTHS =====

    @Test
    fun testAllMonths() {
        val months = listOf(
            "january" to 1, "february" to 2, "march" to 3, "april" to 4,
            "may" to 5, "june" to 6, "july" to 7, "august" to 8,
            "september" to 9, "october" to 10, "november" to 11, "december" to 12
        )
        for ((month, num) in months) {
            val result = parser.parse("$month 20")
            assertIs<InterpretedDateTime.AbsoluteDate>(result)
            assertEquals(num, result.date.month.number)
        }
    }

    // ===== parseFromMessage TESTS =====

    @Test
    fun testParseFromMessageExtractsDateTimeFromSentence() {
        val result = parser.parseFromMessage("remind me to buy groceries tomorrow at 3pm")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result?.dateTime)
        assertEquals(LocalDateTime(2025, 1, 16, 15, 0), (result?.dateTime as InterpretedDateTime.AbsoluteDateTime).dateTime)
        assertEquals("tomorrow at 3pm", result.matchedText.lowercase())
    }

    @Test
    fun testParseFromMessageExtractsRelativeDuration() {
        val result = parser.parseFromMessage("ping me again in 30 minutes please")
        assertIs<InterpretedDateTime.Relative>(result?.dateTime)
        assertEquals(30.minutes, (result?.dateTime as InterpretedDateTime.Relative).duration)
        assertEquals("in 30 minutes", result.matchedText.lowercase())
    }

    @Test
    fun testParseFromMessageExtractsFromNow() {
        val result = parser.parseFromMessage("set a timer for 3 hours from now ok?")
        assertIs<InterpretedDateTime.Relative>(result?.dateTime)
        assertEquals(3.hours, (result?.dateTime as InterpretedDateTime.Relative).duration)
        assertEquals("3 hours from now", result.matchedText.lowercase())
    }

    @Test
    fun testParseFromMessageExtractsAbsoluteTime() {
        val result = parser.parseFromMessage("let's meet at 3pm for coffee")
        assertIs<InterpretedDateTime.AbsoluteTime>(result?.dateTime)
        assertEquals(LocalTime(15, 0), (result?.dateTime as InterpretedDateTime.AbsoluteTime).time)
        assertEquals("at 3pm", result.matchedText.lowercase())
    }

    @Test
    fun testParseFromMessageExtractsDayOfWeekWithTime() {
        val result = parser.parseFromMessage("schedule the meeting next monday at 9am")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result?.dateTime)
        assertEquals(LocalDateTime(2025, 1, 20, 9, 0), (result?.dateTime as InterpretedDateTime.AbsoluteDateTime).dateTime)
    }

    @Test
    fun testParseFromMessageExtractsMonthDay() {
        val result = parser.parseFromMessage("the party is on february 14")
        assertIs<InterpretedDateTime.AbsoluteDate>(result?.dateTime)
        assertEquals(LocalDate(2025, 2, 14), (result?.dateTime as InterpretedDateTime.AbsoluteDate).date)
    }

    @Test
    fun testParseFromMessageExtractsMonthDayWithTime() {
        val result = parser.parseFromMessage("dinner reservation february 14 at 7pm")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result?.dateTime)
        assertEquals(LocalDateTime(2025, 2, 14, 19, 0), (result?.dateTime as InterpretedDateTime.AbsoluteDateTime).dateTime)
    }

    @Test
    fun testParseFromMessageExtractsTomorrow() {
        val result = parser.parseFromMessage("I'll do it tomorrow")
        assertIs<InterpretedDateTime.AbsoluteDate>(result?.dateTime)
        assertEquals(LocalDate(2025, 1, 16), (result?.dateTime as InterpretedDateTime.AbsoluteDate).date)
        assertEquals("tomorrow", result.matchedText.lowercase())
    }

    @Test
    fun testParseFromMessageExtractsHalfAnHour() {
        val result = parser.parseFromMessage("remind me in half an hour to check the oven")
        assertIs<InterpretedDateTime.Relative>(result?.dateTime)
        assertEquals(30.minutes, (result?.dateTime as InterpretedDateTime.Relative).duration)
    }

    @Test
    fun testParseFromMessageNoDateTimeReturnsNull() {
        val result = parser.parseFromMessage("just a regular message with no time info")
        assertNull(result)
    }

    @Test
    fun testParseFromMessageCleanInputStillWorks() {
        val result = parser.parseFromMessage("tomorrow at 3pm")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result?.dateTime)
        assertEquals(LocalDateTime(2025, 1, 16, 15, 0), (result?.dateTime as InterpretedDateTime.AbsoluteDateTime).dateTime)
    }

    @Test
    fun testParseFromMessagePrefersMoreSpecificMatch() {
        // Should match "tomorrow at 3pm" (AbsoluteDateTime) rather than just "tomorrow" (AbsoluteDate) or "3pm" (AbsoluteTime)
        val result = parser.parseFromMessage("don't forget tomorrow at 3pm we have a meeting")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result?.dateTime)
        assertEquals(LocalDateTime(2025, 1, 16, 15, 0), (result?.dateTime as InterpretedDateTime.AbsoluteDateTime).dateTime)
    }

    @Test
    fun testParseFromMessageRangeIsCorrect() {
        val message = "remind me tomorrow at 3pm to call mom"
        val result = parser.parseFromMessage(message)
        assertIs<ParsedDateTimeResult>(result)
        // The matched text should be extractable from the original message using the range
        assertEquals(result.matchedText, message.substring(result.range))
    }

    @Test
    fun testParseFromMessageCaseInsensitive() {
        val result = parser.parseFromMessage("Let's meet TOMORROW AT 3PM for lunch")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result?.dateTime)
        assertEquals(LocalDateTime(2025, 1, 16, 15, 0), (result?.dateTime as InterpretedDateTime.AbsoluteDateTime).dateTime)
    }

    @Test
    fun testParseFromMessageExtractsCoupleHours() {
        val result = parser.parseFromMessage("I'll be there in a couple hours don't worry")
        assertIs<InterpretedDateTime.Relative>(result?.dateTime)
        assertEquals(2.hours, (result?.dateTime as InterpretedDateTime.Relative).duration)
    }

    @Test
    fun testParseFromMessageExtractsDayOfWeek() {
        val result = parser.parseFromMessage("let's reschedule to friday")
        assertIs<InterpretedDateTime.AbsoluteDate>(result?.dateTime)
        assertEquals(LocalDate(2025, 1, 17), (result?.dateTime as InterpretedDateTime.AbsoluteDate).date)
    }

    @Test
    fun testParseFromMessageExtractsMonthDayCommaYear() {
        val result = parser.parseFromMessage("the deadline is August 24, 2026")
        assertIs<InterpretedDateTime.AbsoluteDate>(result?.dateTime)
        assertEquals(LocalDate(2026, 8, 24), (result?.dateTime as InterpretedDateTime.AbsoluteDate).date)
    }

    @Test
    fun testParseFromMessageExtractsMonthsFromNow() {
        val result = parser.parseFromMessage("let's revisit this in 6 months")
        assertIs<InterpretedDateTime.Relative>(result?.dateTime)
        assertEquals(DatePeriod(months = 6), (result?.dateTime as InterpretedDateTime.Relative).period)
    }

    @Test
    fun testParseFromMessageExtractsTomorrowMorning() {
        val result = parser.parseFromMessage("remind me tomorrow morning to stretch")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result?.dateTime)
        assertEquals(LocalDateTime(2025, 1, 16, 9, 0), (result?.dateTime as InterpretedDateTime.AbsoluteDateTime).dateTime)
        assertEquals("tomorrow morning", result.matchedText.lowercase())
    }

    @Test
    fun testParseFromMessageExtractsTomorrowEvening() {
        val result = parser.parseFromMessage("let's catch up tomorrow evening after work")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result?.dateTime)
        assertEquals(LocalDateTime(2025, 1, 16, 19, 0), (result?.dateTime as InterpretedDateTime.AbsoluteDateTime).dateTime)
        assertEquals("tomorrow evening", result.matchedText.lowercase())
    }

    @Test
    fun testParseFromMessageExtractsTomorrowAfternoon() {
        val result = parser.parseFromMessage("the meeting is tomorrow afternoon")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result?.dateTime)
        assertEquals(LocalDateTime(2025, 1, 16, 14, 0), (result?.dateTime as InterpretedDateTime.AbsoluteDateTime).dateTime)
        assertEquals("tomorrow afternoon", result.matchedText.lowercase())
    }

    @Test
    fun testParseFromMessageExtractsTomorrowNight() {
        val result = parser.parseFromMessage("dinner is tomorrow night at the restaurant")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result?.dateTime)
        assertEquals(LocalDateTime(2025, 1, 16, 21, 0), (result?.dateTime as InterpretedDateTime.AbsoluteDateTime).dateTime)
        assertEquals("tomorrow night", result.matchedText.lowercase())
    }

    @Test
    fun testParseFromMessageExtractsThisMorning() {
        val result = parser.parseFromMessage("I need to finish this morning before lunch")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result?.dateTime)
        assertEquals(LocalDateTime(2025, 1, 15, 9, 0), (result?.dateTime as InterpretedDateTime.AbsoluteDateTime).dateTime)
        assertEquals("this morning", result.matchedText.lowercase())
    }

    @Test
    fun testParseFromMessageExtractsThisEvening() {
        val result = parser.parseFromMessage("plans for this evening include groceries")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result?.dateTime)
        assertEquals(LocalDateTime(2025, 1, 15, 19, 0), (result?.dateTime as InterpretedDateTime.AbsoluteDateTime).dateTime)
        assertEquals("this evening", result.matchedText.lowercase())
    }

    @Test
    fun testParseFromMessageTimeOfDayRangeIsCorrect() {
        val message = "remind me tomorrow morning to call the dentist"
        val result = parser.parseFromMessage(message)
        assertIs<ParsedDateTimeResult>(result)
        assertEquals(result.matchedText, message.substring(result.range))
    }

    @Test
    fun testParseFromMessageExtractsNumericDate() {
        val result = parser.parseFromMessage("the deadline is 2/14")
        assertIs<InterpretedDateTime.AbsoluteDate>(result?.dateTime)
        assertEquals(LocalDate(2025, 2, 14), (result?.dateTime as InterpretedDateTime.AbsoluteDate).date)
    }

    @Test
    fun testParseFromMessageExtractsTimeWithMinutes() {
        val result = parser.parseFromMessage("meet me at 3:30pm by the office")
        assertIs<InterpretedDateTime.AbsoluteTime>(result?.dateTime)
        assertEquals(LocalTime(15, 30), (result?.dateTime as InterpretedDateTime.AbsoluteTime).time)
    }

    @Test
    fun testParseFromMessageExtractsAtTimeOnDay() {
        val result = parser.parseFromMessage("let's do it at 7pm on february 14")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result?.dateTime)
        assertEquals(LocalDateTime(2025, 2, 14, 19, 0), (result?.dateTime as InterpretedDateTime.AbsoluteDateTime).dateTime)
    }

    @Test
    fun testParseFromMessageNoFalsePositiveOnNumbers() {
        val result = parser.parseFromMessage("I bought 3 apples")
        assertNull(result)
    }

    // ===== TIME STRING BOUNDARY TESTS =====

    @Test
    fun testBareNumberWithoutAmPmReturnsNull() {
        val result = parser.parse("3")
        assertNull(result)
    }

    @Test
    fun testHourOutOfRange13pm() {
        // 13pm is ambiguous but parser treats the pm as redundant, resulting in 13:00
        val result = parser.parse("at 13pm")
        assertIs<InterpretedDateTime.AbsoluteTime>(result)
        assertEquals(LocalTime(13, 0), result.time)
    }

    @Test
    fun testHourOutOfRange25ColonZero() {
        val result = parser.parse("at 25:00")
        assertNull(result)
    }

    @Test
    fun testMinuteOutOfRange() {
        val result = parser.parse("at 3:60pm")
        assertNull(result)
    }

    @Test
    fun testMidnightWithMinutes() {
        val result = parser.parse("at 12:30am")
        assertIs<InterpretedDateTime.AbsoluteTime>(result)
        assertEquals(LocalTime(0, 30), result.time)
    }

    @Test
    fun testZeroHour24Format() {
        val result = parser.parse("at 0:00")
        assertIs<InterpretedDateTime.AbsoluteTime>(result)
        assertEquals(LocalTime(0, 0), result.time)
    }

    // ===== DATE BOUNDARY TESTS =====

    @Test
    fun testInvalidDayForMonthReturnsNull() {
        val result = parser.parse("february 30")
        assertNull(result)
    }

    @Test
    fun testNumericDateInvalidMonthReturnsNull() {
        val result = parser.parse("13/5")
        assertNull(result)
    }

    @Test
    fun testNumericDateZeroMonthReturnsNull() {
        val result = parser.parse("0/15")
        assertNull(result)
    }

    @Test
    fun testNumericDatePastRollsForward() {
        // Jan 10 is past the Jan 15 reference, should roll to 2026
        val result = parser.parse("1/10")
        assertIs<InterpretedDateTime.AbsoluteDate>(result)
        assertEquals(LocalDate(2026, 1, 10), result.date)
    }

    @Test
    fun testLeapYearFeb29InNonLeapYear() {
        // 2025 is not a leap year, february 29 should return null
        val result = parser.parse("february 29")
        assertNull(result)
    }

    @Test
    fun testLeapYearFeb29InLeapYear() {
        // 2028 is a leap year
        val result = parser.parse("february 29, 2028")
        assertIs<InterpretedDateTime.AbsoluteDate>(result)
        assertEquals(LocalDate(2028, 2, 29), result.date)
    }

    // ===== MISSING PATTERN COVERAGE =====

    @Test
    fun testTodayMorning() {
        val result = parser.parse("today morning")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 15, 9, 0), result.dateTime)
    }

    @Test
    fun testTodayAtTimeWithMinutes() {
        val result = parser.parse("today at 3:30pm")
        assertIs<InterpretedDateTime.AbsoluteDateTime>(result)
        assertEquals(LocalDateTime(2025, 1, 15, 15, 30), result.dateTime)
    }

    @Test
    fun testOnMonday() {
        // Reference is Wednesday Jan 15, next Monday is Jan 20
        val result = parser.parse("on monday")
        assertIs<InterpretedDateTime.AbsoluteDate>(result)
        assertEquals(LocalDate(2025, 1, 20), result.date)
    }

    @Test
    fun testBareFriday() {
        // Reference is Wednesday Jan 15, Friday is Jan 17
        val result = parser.parse("friday")
        assertIs<InterpretedDateTime.AbsoluteDate>(result)
        assertEquals(LocalDate(2025, 1, 17), result.date)
    }

    // ===== RELATIVE UNTESTED PATHS =====

    @Test
    fun testRelativeStandaloneAWeek() {
        val result = parser.parse("a week")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(7.days, result.duration)
    }

    @Test
    fun testRelativeInOneWeek() {
        val result = parser.parse("in 1 week")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(7.days, result.duration)
    }

    @Test
    fun testRelativeStandaloneAYear() {
        val result = parser.parse("a year")
        assertIs<InterpretedDateTime.Relative>(result)
        assertEquals(DatePeriod(years = 1), result.period)
    }
}