package coredevices.indexai.time

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class HumanDateTimeParser(
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault()
) {

    private val currentDateTime: LocalDateTime get() = clock.now().toLocalDateTime(timeZone)

    fun parse(input: String): InterpretedDateTime? {
        val normalized = input.trim().lowercase()

        return parseRelative(normalized)
            ?: parseAbsoluteDateTime(normalized)
            ?: parseAbsoluteTime(normalized)
            ?: parseAbsoluteDate(normalized)
    }

    /**
     * Scans a full user message for a date/time expression and extracts it.
     * Returns the parsed result along with the matched substring and its range,
     * or null if no date/time expression is found.
     *
     * Example: "remind me to buy groceries tomorrow at 3pm" -> ParsedDateTimeResult(AbsoluteDateTime(...), "tomorrow at 3pm", 32..48)
     */
    fun parseFromMessage(message: String): ParsedDateTimeResult? {
        val normalized = message.lowercase()

        for (pattern in messagePatterns) {
            pattern.find(normalized)?.let { match ->
                val candidate = match.value.trim()
                parse(candidate)?.let { result ->
                    // Map back to original message range
                    val originalText = message.substring(match.range).trim()
                    val trimStart = message.indexOf(originalText, match.range.first)
                    return ParsedDateTimeResult(
                        result,
                        originalText,
                        trimStart until trimStart + originalText.length
                    )
                }
            }
        }

        return null
    }

    private fun parseRelative(input: String): InterpretedDateTime.Relative? {
        if (halfHourPattern.matches(input)) {
            return InterpretedDateTime.Relative(30.minutes)
        }

        if (halfDayPattern.matches(input)) {
            return InterpretedDateTime.Relative(12.hours)
        }

        // Compound patterns must be tried before single-unit patterns to avoid partial matches
        inCompoundPattern.find(input)?.let { match ->
            val a1 = parseQuantifier(match.groupValues[1]) ?: return null
            val a2 = parseQuantifier(match.groupValues[3]) ?: return null
            return combineRelative(a1, match.groupValues[2], a2, match.groupValues[4])
        }

        fromNowCompoundPattern.find(input)?.let { match ->
            val a1 = parseQuantifier(match.groupValues[1]) ?: return null
            val a2 = parseQuantifier(match.groupValues[3]) ?: return null
            return combineRelative(a1, match.groupValues[2], a2, match.groupValues[4])
        }

        standaloneCompoundPattern.find(input)?.let { match ->
            val a1 = parseQuantifier(match.groupValues[1]) ?: return null
            val a2 = parseQuantifier(match.groupValues[3]) ?: return null
            return combineRelative(a1, match.groupValues[2], a2, match.groupValues[4])
        }

        inPattern.find(input)?.let { match ->
            val amount = parseQuantifier(match.groupValues[1]) ?: return null
            val unit = match.groupValues[2]
            return toRelative(amount, unit)
        }

        fromNowPattern.find(input)?.let { match ->
            val amount = parseQuantifier(match.groupValues[1]) ?: return null
            val unit = match.groupValues[2]
            return toRelative(amount, unit)
        }

        standaloneDurationPattern.find(input)?.let { match ->
            val amount = parseQuantifier(match.groupValues[1]) ?: return null
            val unit = match.groupValues[2]
            return toRelative(amount, unit)
        }

        return null
    }

    private fun combineRelative(a1: Long, unit1: String, a2: Long, unit2: String): InterpretedDateTime.Relative? {
        val r1 = toRelative(a1, unit1) ?: return null
        val r2 = toRelative(a2, unit2) ?: return null
        if (r1.period != null || r2.period != null) return null
        return InterpretedDateTime.Relative(r1.duration + r2.duration)
    }

    private fun parseQuantifier(quantifier: String): Long? {
        val normalized = quantifier.trim().lowercase()
        return when {
            normalized.matches(Regex("""\d+""")) -> normalized.toLongOrNull()
            normalized == "a" || normalized == "an" || normalized == "one" -> 1L
            normalized.contains("couple") -> 2L
            normalized.contains("few") -> 3L
            normalized == "several" -> 5L
            else -> wordToNumber(normalized)
        }
    }

    private fun wordToNumber(word: String): Long? {
        return when (word) {
            "zero" -> 0L
            "one" -> 1L
            "two" -> 2L
            "three" -> 3L
            "four" -> 4L
            "five" -> 5L
            "six" -> 6L
            "seven" -> 7L
            "eight" -> 8L
            "nine" -> 9L
            "ten" -> 10L
            "eleven" -> 11L
            "twelve" -> 12L
            "thirteen" -> 13L
            "fourteen" -> 14L
            "fifteen" -> 15L
            "sixteen" -> 16L
            "seventeen" -> 17L
            "eighteen" -> 18L
            "nineteen" -> 19L
            "twenty" -> 20L
            "thirty" -> 30L
            "forty" -> 40L
            "fifty" -> 50L
            else -> null
        }
    }

    private fun parseTimeOfDay(timeOfDay: String): LocalTime? {
        return when (timeOfDay.lowercase()) {
            "morning" -> LocalTime(9, 0)
            "afternoon" -> LocalTime(14, 0)
            "evening" -> LocalTime(19, 0)
            "night" -> LocalTime(21, 0)
            else -> null
        }
    }

    private fun parseAbsoluteDateTime(input: String): InterpretedDateTime.AbsoluteDateTime? {
        dayWordTimeOfDayPattern.find(input)?.let { match ->
            val dayWord = match.groupValues[1].let { if (it == "this") "today" else it }
            val timeOfDay = match.groupValues[2]
            val date = parseDayWord(dayWord) ?: return null
            val time = parseTimeOfDay(timeOfDay) ?: return null
            return InterpretedDateTime.AbsoluteDateTime(LocalDateTime(date, time))
        }

        dayWordTimePattern.find(input)?.let { match ->
            val dayWord = match.groupValues[1]
            val timeStr = match.groupValues[2]
            val date = parseDayWord(dayWord) ?: return null
            val time = parseTimeString(timeStr) ?: return null
            return InterpretedDateTime.AbsoluteDateTime(LocalDateTime(date, time))
        }

        timeDayWordPattern.find(input)?.let { match ->
            val timeStr = match.groupValues[1]
            val dayWord = match.groupValues[2]
            val date = parseDayWord(dayWord) ?: return null
            val time = parseTimeString(timeStr) ?: return null
            return InterpretedDateTime.AbsoluteDateTime(LocalDateTime(date, time))
        }

        dayOfWeekTimePattern.find(input)?.let { match ->
            val dayName = match.groupValues[1]
            val timeStr = match.groupValues[2]
            val date = parseNextDayOfWeek(dayName) ?: return null
            val time = parseTimeString(timeStr) ?: return null
            return InterpretedDateTime.AbsoluteDateTime(LocalDateTime(date, time))
        }

        timeDayOfWeekPattern.find(input)?.let { match ->
            val timeStr = match.groupValues[1]
            val dayName = match.groupValues[2]
            val date = parseNextDayOfWeek(dayName) ?: return null
            val time = parseTimeString(timeStr) ?: return null
            return InterpretedDateTime.AbsoluteDateTime(LocalDateTime(date, time))
        }

        monthDayTimePattern.find(input)?.let { match ->
            val monthName = match.groupValues[1]
            val day = match.groupValues[2].toIntOrNull() ?: return null
            val year = match.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull()
            val timeStr = match.groupValues[4]
            val date = parseMonthDay(monthName, day, year) ?: return null
            val time = parseTimeString(timeStr) ?: return null
            return InterpretedDateTime.AbsoluteDateTime(LocalDateTime(date, time))
        }

        timeMonthDayPattern.find(input)?.let { match ->
            val timeStr = match.groupValues[1]
            val monthName = match.groupValues[2]
            val day = match.groupValues[3].toIntOrNull() ?: return null
            val year = match.groupValues[4].takeIf { it.isNotEmpty() }?.toIntOrNull()
            val date = parseMonthDay(monthName, day, year) ?: return null
            val time = parseTimeString(timeStr) ?: return null
            return InterpretedDateTime.AbsoluteDateTime(LocalDateTime(date, time))
        }

        numericDateTimePattern.find(input)?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return null
            val day = match.groupValues[2].toIntOrNull() ?: return null
            val timeStr = match.groupValues[3]
            val date = parseNumericDate(month, day) ?: return null
            val time = parseTimeString(timeStr) ?: return null
            return InterpretedDateTime.AbsoluteDateTime(LocalDateTime(date, time))
        }

        return null
    }

    private fun parseAbsoluteTime(input: String): InterpretedDateTime.AbsoluteTime? {
        atTimePattern.find(input)?.let { match ->
            val timeStr = match.groupValues[1]
            val time = parseTimeString(timeStr) ?: return null
            return InterpretedDateTime.AbsoluteTime(time)
        }

        parseTimeString(input)?.let { time ->
            return InterpretedDateTime.AbsoluteTime(time)
        }

        return null
    }

    private fun parseAbsoluteDate(input: String): InterpretedDateTime.AbsoluteDate? {
        dayWordOnlyPattern.find(input)?.let { match ->
            val date = parseDayWord(match.groupValues[1]) ?: return null
            return InterpretedDateTime.AbsoluteDate(date)
        }

        dayOfWeekPattern.find(input)?.let { match ->
            val date = parseNextDayOfWeek(match.groupValues[1]) ?: return null
            return InterpretedDateTime.AbsoluteDate(date)
        }

        monthDayPattern.find(input)?.let { match ->
            val monthName = match.groupValues[1]
            val day = match.groupValues[2].toIntOrNull() ?: return null
            val year = match.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull()
            val date = parseMonthDay(monthName, day, year) ?: return null
            return InterpretedDateTime.AbsoluteDate(date)
        }

        numericDatePattern.find(input)?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return null
            val day = match.groupValues[2].toIntOrNull() ?: return null
            val date = parseNumericDate(month, day) ?: return null
            return InterpretedDateTime.AbsoluteDate(date)
        }

        return null
    }

    private fun toRelative(amount: Long, unit: String): InterpretedDateTime.Relative? {
        return when (unit.lowercase().removeSuffix("s")) {
            "second" -> InterpretedDateTime.Relative(duration = amount.seconds)
            "minute" -> InterpretedDateTime.Relative(duration = amount.minutes)
            "hour" -> InterpretedDateTime.Relative(duration = amount.hours)
            "day" -> InterpretedDateTime.Relative(duration = amount.days)
            "week" -> InterpretedDateTime.Relative(duration = (amount * 7).days)
            "month" -> InterpretedDateTime.Relative(period = DatePeriod(months = amount.toInt()))
            "year" -> InterpretedDateTime.Relative(period = DatePeriod(years = amount.toInt()))
            else -> null
        }
    }

    private fun parseTimeString(timeStr: String): LocalTime? {
        val cleaned = timeStr.trim().lowercase()
            .replace(".", "")
            .replace(" ", "")

        timePattern.find(cleaned)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val amPm = match.groupValues[3].takeIf { it.isNotEmpty() }

            if (amPm != null) {
                val adjustedHour = adjustHour(hour, isPm = amPm == "pm")
                if (adjustedHour !in 0..23 || minute !in 0..59) return null
                return LocalTime(adjustedHour, minute)
            } else {
                // 24-hour format — requires colon (minute must be present)
                if (match.groupValues[2].isEmpty()) return null
                if (hour !in 0..23 || minute !in 0..59) return null
                return LocalTime(hour, minute)
            }
        }

        return null
    }

    private fun adjustHour(hour: Int, isPm: Boolean): Int = when {
        isPm && hour < 12 -> hour + 12
        !isPm && hour == 12 -> 0
        else -> hour
    }

    private fun parseDayWord(word: String): LocalDate? {
        return when (word.lowercase()) {
            "today" -> currentDateTime.date
            "tomorrow" -> currentDateTime.date + DatePeriod(days = 1)
            else -> null
        }
    }

    private fun parseNextDayOfWeek(dayName: String): LocalDate? {
        val targetDay = when (dayName.lowercase()) {
            "sunday" -> DayOfWeek.SUNDAY
            "monday" -> DayOfWeek.MONDAY
            "tuesday" -> DayOfWeek.TUESDAY
            "wednesday" -> DayOfWeek.WEDNESDAY
            "thursday" -> DayOfWeek.THURSDAY
            "friday" -> DayOfWeek.FRIDAY
            "saturday" -> DayOfWeek.SATURDAY
            else -> return null
        }

        val currentDay = currentDateTime.dayOfWeek
        val daysUntil = (targetDay.ordinal - currentDay.ordinal + 7) % 7
        val adjustedDays = if (daysUntil == 0) 7 else daysUntil // If same day, go to next week

        return currentDateTime.date + DatePeriod(days = adjustedDays)
    }

    private fun parseMonthDay(monthName: String, day: Int, explicitYear: Int? = null): LocalDate? {
        val month = parseMonthName(monthName) ?: return null
        if (day !in 1..31) return null

        if (explicitYear != null) {
            return try {
                LocalDate(explicitYear, month, day)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        return resolveFutureDate(month, day)
    }

    private fun parseNumericDate(month: Int, day: Int): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        return resolveFutureDate(Month(month), day)
    }

    private fun parseMonthName(name: String): Month? {
        return when (name.lowercase()) {
            "january" -> Month.JANUARY
            "february" -> Month.FEBRUARY
            "march" -> Month.MARCH
            "april" -> Month.APRIL
            "may" -> Month.MAY
            "june" -> Month.JUNE
            "july" -> Month.JULY
            "august" -> Month.AUGUST
            "september" -> Month.SEPTEMBER
            "october" -> Month.OCTOBER
            "november" -> Month.NOVEMBER
            "december" -> Month.DECEMBER
            else -> null
        }
    }

    private fun resolveFutureDate(month: Month, day: Int): LocalDate? {
        var year = currentDateTime.year
        val candidateDate = try {
            LocalDate(year, month, day)
        } catch (_: IllegalArgumentException) {
            return null
        }

        if (candidateDate < currentDateTime.date) {
            year++
        }

        return try {
            LocalDate(year, month, day)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    companion object {
        // Shared regex fragments
        private const val TIME_EXPR = """\d{1,2}(?::\d{2})?\s*(?:a\.?\s*m\.?|p\.?\s*m\.?)"""
        private const val TIME_24_EXPR = """\d{1,2}:\d{2}"""
        private const val DAY_WORD_EXPR = """today|tomorrow"""
        private const val DAY_OF_WEEK_EXPR = """(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)"""
        private const val MONTH_EXPR = """(?:january|february|march|april|may|june|july|august|september|october|november|december)"""
        private const val TIME_OF_DAY_EXPR = """(?:morning|afternoon|evening|night)"""
        private const val NUMBER_WORDS_EXPR = """two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty"""
        private const val QUANTIFIER_EXPR = """(?:\d+|a|an|one|$NUMBER_WORDS_EXPR|a\s+couple(?:\s+of)?|a\s+few|couple(?:\s+of)?|few|several)"""
        private const val QUANTIFIER_CAPTURE = """(\d+|a|an|one|$NUMBER_WORDS_EXPR|a\s+couple(?:\s+of)?|a\s+few|couple(?:\s+of)?|few|several)"""
        private const val UNIT_EXPR = """(?:seconds?|minutes?|hours?|days?|weeks?|months?|years?)"""
        private const val UNIT_CAPTURE = """(second|seconds|minute|minutes|hour|hours|day|days|week|weeks|month|months|year|years)"""

        private const val COMPOUND_SEP = """(?:\s*,\s*(?:and\s+)?|\s+and\s+|\s+)"""

        // Relative patterns
        private val halfHourPattern = Regex("""(?:in\s+)?half\s+an?\s+hour(?:\s+from\s+now)?""")
        private val halfDayPattern = Regex("""(?:in\s+)?half\s+a\s+day(?:\s+from\s+now)?""")
        private val inCompoundPattern = Regex("""in\s+$QUANTIFIER_CAPTURE\s+$UNIT_CAPTURE$COMPOUND_SEP$QUANTIFIER_CAPTURE\s+$UNIT_CAPTURE""")
        private val fromNowCompoundPattern = Regex("""$QUANTIFIER_CAPTURE\s+$UNIT_CAPTURE$COMPOUND_SEP$QUANTIFIER_CAPTURE\s+$UNIT_CAPTURE\s+from\s+now""")
        private val standaloneCompoundPattern = Regex("""^$QUANTIFIER_CAPTURE\s+$UNIT_CAPTURE$COMPOUND_SEP$QUANTIFIER_CAPTURE\s+$UNIT_CAPTURE$""")
        private val inPattern = Regex("""in\s+$QUANTIFIER_CAPTURE\s+$UNIT_CAPTURE""")
        private val fromNowPattern = Regex("""$QUANTIFIER_CAPTURE\s+$UNIT_CAPTURE\s+from\s+now""")
        private val standaloneDurationPattern = Regex("""^$QUANTIFIER_CAPTURE\s+$UNIT_CAPTURE$""")

        // Absolute date+time patterns
        private val dayWordTimeOfDayPattern = Regex("""(today|tomorrow|this)\s+(morning|afternoon|evening|night)""")
        private val dayWordTimePattern = Regex("""(today|tomorrow)\s+at\s+(.+)""")
        private val timeDayWordPattern = Regex("""(?:at\s+)?(.+?)\s+(today|tomorrow)""")
        private val dayOfWeekTimePattern = Regex("""(?:next|on)?\s*(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\s+at\s+(.+)""")
        private val timeDayOfWeekPattern = Regex("""(?:at\s+)?(.+?)\s+(?:next|on)?\s*(monday|tuesday|wednesday|thursday|friday|saturday|sunday)""")
        private val monthDayTimePattern = Regex("""(?:on\s+)?(january|february|march|april|may|june|july|august|september|october|november|december)\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?\s+at\s+(.+)""")
        private val timeMonthDayPattern = Regex("""(?:at\s+)?(.+?)\s+(?:on\s+)?(january|february|march|april|may|june|july|august|september|october|november|december)\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?""")
        private val numericDateTimePattern = Regex("""(\d{1,2})/(\d{1,2})\s+at\s+(.+)""")

        // Absolute time patterns
        private val atTimePattern = Regex("""at\s+(.+)""")
        private val timePattern = Regex("""^(\d{1,2})(?::(\d{2}))?(am|pm)?$""")

        // Absolute date patterns
        private val dayWordOnlyPattern = Regex("""^(today|tomorrow)$""")
        private val dayOfWeekPattern = Regex("""(?:next|on)?\s*(monday|tuesday|wednesday|thursday|friday|saturday|sunday)$""")
        private val monthDayPattern = Regex("""(?:on\s+)?(january|february|march|april|may|june|july|august|september|october|november|december)\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?$""")
        private val numericDatePattern = Regex("""^(\d{1,2})/(\d{1,2})$""")

        // Patterns for parseFromMessage, ordered by specificity (most specific first)
        private val messagePatterns = listOf(
            // Date + time combinations
            Regex("""(?:$DAY_WORD_EXPR)\s+at\s+(?:$TIME_EXPR|$TIME_24_EXPR)"""),
            Regex("""at\s+(?:$TIME_EXPR|$TIME_24_EXPR)\s+(?:$DAY_WORD_EXPR)"""),
            Regex("""(?:next\s+|on\s+)?$DAY_OF_WEEK_EXPR\s+at\s+(?:$TIME_EXPR|$TIME_24_EXPR)"""),
            Regex("""at\s+(?:$TIME_EXPR|$TIME_24_EXPR)\s+(?:next\s+|on\s+)?$DAY_OF_WEEK_EXPR"""),
            Regex("""(?:on\s+)?$MONTH_EXPR\s+\d{1,2}(?:st|nd|rd|th)?(?:,?\s+\d{4})?\s+at\s+(?:$TIME_EXPR|$TIME_24_EXPR)"""),
            Regex("""at\s+(?:$TIME_EXPR|$TIME_24_EXPR)\s+(?:on\s+)?$MONTH_EXPR\s+\d{1,2}(?:st|nd|rd|th)?(?:,?\s+\d{4})?"""),
            Regex("""\d{1,2}/\d{1,2}\s+at\s+(?:$TIME_EXPR|$TIME_24_EXPR)"""),
            // Date + time-of-day combinations
            Regex("""(?:$DAY_WORD_EXPR|this)\s+$TIME_OF_DAY_EXPR"""),
            // Relative durations
            Regex("""(?:in\s+)?half\s+an?\s+hour(?:\s+from\s+now)?"""),
            Regex("""(?:in\s+)?half\s+a\s+day(?:\s+from\s+now)?"""),
            Regex("""in\s+$QUANTIFIER_EXPR\s+$UNIT_EXPR$COMPOUND_SEP$QUANTIFIER_EXPR\s+$UNIT_EXPR"""),
            Regex("""$QUANTIFIER_EXPR\s+$UNIT_EXPR$COMPOUND_SEP$QUANTIFIER_EXPR\s+$UNIT_EXPR\s+from\s+now"""),
            Regex("""in\s+$QUANTIFIER_EXPR\s+$UNIT_EXPR"""),
            Regex("""$QUANTIFIER_EXPR\s+$UNIT_EXPR\s+from\s+now"""),
            // "at <time>" (standalone)
            Regex("""at\s+(?:$TIME_EXPR|$TIME_24_EXPR)"""),
            // Date patterns
            Regex("""(?:next|on)\s+$DAY_OF_WEEK_EXPR"""),
            Regex("""(?:on\s+)?$MONTH_EXPR\s+\d{1,2}(?:st|nd|rd|th)?(?:,?\s+\d{4})?"""),
            Regex("""\b\d{1,2}/\d{1,2}\b"""),
            Regex("""\b(?:$DAY_WORD_EXPR)\b"""),
            Regex("""\b$DAY_OF_WEEK_EXPR\b"""),
            // Bare time (e.g. "3pm")
            Regex("""\b$TIME_EXPR"""),
        )
    }
}

sealed class InterpretedDateTime {
    data class Relative(val duration: Duration = Duration.ZERO, val period: DatePeriod? = null) : InterpretedDateTime()
    data class AbsoluteDateTime(val dateTime: LocalDateTime) : InterpretedDateTime()
    data class AbsoluteTime(val time: LocalTime) : InterpretedDateTime()
    data class AbsoluteDate(val date: LocalDate) : InterpretedDateTime()
}

data class ParsedDateTimeResult(
    val dateTime: InterpretedDateTime,
    val matchedText: String,
    val range: IntRange
)