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
        val normalized = normalizeTimeExpressions(input.trim().lowercase())

        return parseRelative(normalized)
            ?: parseAbsoluteDateTime(normalized)
            ?: parseAbsoluteTime(normalized)
            ?: parseAbsoluteDate(normalized)
    }

    /**
     * Rewrites spoken-time renderings (as STT engines emit them) into canonical digit form so
     * downstream regex patterns (which only recognise digits) can match. Covers:
     *
     *  - Hour + minute in any digit/word mix: "seven fifty am" → "7:50 am", "9 15 a.m." → "9:15 a.m.",
     *    "9 fifteen" → "9:15", "nine 15" → "9:15", "eight twenty five" → "8:25"
     *  - "oh" minutes: "nine oh five" / "9 oh 5" → "9:05"
     *  - "<minute> past/after <hour>", and "<minute> to/till/before/of <hour>" when anchored by
     *    am/pm or "at" (or the minute is half/quarter) — "ten to twelve" alone stays a range
     *  - Hour word with time context: "eight pm" → "8 pm", "at eight" → "at 8", "eight o'clock" → "8"
     *  - "noon" → "12 pm", "midnight" → "12 am"
     *  - "in the morning/afternoon/evening/night" / "at night" → am/pm suffix
     *
     * Hour words with no adjacent time context are left alone so date phrases like
     * "march twenty one" and durations like "in twenty five minutes" are untouched.
     * Digit-digit pairs ("9 15") are only rewritten next to am/pm or after "at".
     */
    private fun normalizeTimeExpressions(s: String): String {
        var r = s

        r = r.replace(noonRegex, "12 pm")
        r = r.replace(midnightRegex, "12 am")

        // "<hour word> o'clock" → digit (o'clock is the time context), then strip residual
        // "o'clock" after digit hours ("8 o'clock")
        r = r.replace(hourWordOclockRegex) { match ->
            wordToHour(match.groupValues[1])?.toString() ?: match.value
        }
        r = r.replace(oclockRegex, "")

        r = r.replace(inTheTimeOfDayRegex) { match ->
            if (match.groupValues[1] == "morning") " am" else " pm"
        }
        r = r.replace(atNightRegex, " pm")

        r = r.replace(minutesPastHourRegex) { match ->
            val minute = pastToMinute(match.groupValues[1]) ?: return@replace match.value
            val hour = hourOf(match.groupValues[2], match.groupValues[3]) ?: return@replace match.value
            "$hour:${minute.toString().padStart(2, '0')}"
        }
        r = r.replace(minutesToHourRegex) { match ->
            val atPrefix = match.groupValues[1]
            val minuteWord = match.groupValues[2]
            val amPm = match.groupValues[5]
            // Number-word minutes double as range endpoints ("free ten to twelve"), so those need
            // an am/pm or "at" anchor; half/quarter are unambiguous
            val anchored = minuteWord == "half" || minuteWord == "quarter" ||
                atPrefix.isNotEmpty() || amPm.isNotEmpty()
            if (!anchored) return@replace match.value
            val minute = pastToMinute(minuteWord) ?: return@replace match.value
            val hour = hourOf(match.groupValues[3], match.groupValues[4]) ?: return@replace match.value
            val prev = if (hour == 1) 12 else hour - 1
            // Crossing 12 flips the meridiem: "quarter to noon" is 11:45 am
            val outAmPm = if (hour == 12) flipAmPm(amPm) else amPm
            "$atPrefix$prev:${(60 - minute).toString().padStart(2, '0')}$outAmPm"
        }

        r = r.replace(ohMinutesRegex) { match ->
            val hour = hourOf(match.groupValues[1], match.groupValues[2]) ?: return@replace match.value
            val ones = match.groupValues[3].takeIf { it.isNotEmpty() }?.let { wordToHour(it) }
                ?: match.groupValues[4].toIntOrNull()
                ?: return@replace match.value
            "$hour:0$ones"
        }

        // Hour + minute compounds. Word on either side is unambiguous time context; digit-digit
        // ("9 15") needs adjacent am/pm or "at" so addresses/quantities are left alone.
        r = r.replace(wordHourMinuteRegex) { match ->
            val hour = wordToHour(match.groupValues[1]) ?: return@replace match.value
            val minute = minuteOf(match.groupValues[2]) ?: return@replace match.value
            "$hour:${minute.toString().padStart(2, '0')}"
        }
        r = r.replace(digitHourWordMinuteRegex) { match ->
            val hour = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return@replace match.value
            val minute = parseMinuteWord(match.groupValues[2]) ?: return@replace match.value
            "$hour:${minute.toString().padStart(2, '0')}"
        }
        r = r.replace(digitPairAmPmRegex) { match ->
            val hour = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return@replace match.value
            val minute = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return@replace match.value
            "$hour:${minute.toString().padStart(2, '0')}${match.groupValues[3]}"
        }
        r = r.replace(atDigitPairRegex) { match ->
            val hour = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return@replace match.value
            val minute = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return@replace match.value
            "at $hour:${minute.toString().padStart(2, '0')}"
        }

        // Hour word with time context: trailing am/pm ("eight pm" → "8 pm") or preceding "at"
        // ("at eight" → "at 8"). Bare hour words elsewhere are left alone.
        r = r.replace(hourWordAmPmRegex) { match ->
            val hour = wordToHour(match.groupValues[1]) ?: return@replace match.value
            "$hour${match.groupValues[2]}"
        }
        r = r.replace(atHourWordRegex) { match ->
            wordToHour(match.groupValues[1])?.let { "at $it" } ?: match.value
        }

        return r
    }

    private fun hourOf(word: String, digits: String): Int? =
        word.takeIf { it.isNotEmpty() }?.let { wordToHour(it) }
            ?: digits.toIntOrNull()?.takeIf { it in 0..23 }

    private fun minuteOf(minute: String): Int? =
        minute.toIntOrNull()?.takeIf { it in 0..59 } ?: parseMinuteWord(minute)

    private fun pastToMinute(minute: String): Int? = when (minute) {
        "half" -> 30
        "quarter" -> 15
        else -> minuteOf(minute)?.takeIf { it in 1..59 }
    }

    private fun flipAmPm(amPm: String): String =
        if ('p' in amPm) amPm.replace('p', 'a') else amPm.replace('a', 'p')

    private fun wordToHour(word: String): Int? = wordToNumber(word)?.toInt()?.takeIf { it in 1..12 }

    private fun parseMinuteWord(word: String): Int? {
        val trimmed = word.trim()
        wordToNumber(trimmed)?.let { return it.toInt() }
        // Compound tens + ones: "twenty five" → 25
        val parts = trimmed.split(whitespaceRegex)
        if (parts.size == 2) {
            val tens = wordToNumber(parts[0])?.toInt()?.takeIf { it in 20..50 && it % 10 == 0 } ?: return null
            val ones = wordToNumber(parts[1])?.toInt()?.takeIf { it in 1..9 } ?: return null
            return tens + ones
        }
        return null
    }

    /**
     * Scans a full user message for a date/time expression and extracts it.
     * Returns the parsed result along with the matched substring and its range,
     * or null if no date/time expression is found.
     *
     * Spoken-time forms are normalized before matching; when that rewrites the text, the returned
     * matchedText/range refer to the normalized lowercased message, not the original.
     *
     * Example: "remind me to buy groceries tomorrow at 3pm" -> ParsedDateTimeResult(AbsoluteDateTime(...), "tomorrow at 3pm", 32..48)
     */
    fun parseFromMessage(message: String): ParsedDateTimeResult? {
        val lowered = message.lowercase()
        val normalized = normalizeTimeExpressions(lowered)
        val source = if (normalized == lowered) message else normalized

        for (pattern in messagePatterns) {
            pattern.find(normalized)?.let { match ->
                val candidate = match.value.trim()
                parse(candidate)?.let { result ->
                    val originalText = source.substring(match.range).trim()
                    val trimStart = source.indexOf(originalText, match.range.first)
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
        tonightPattern.find(input)?.let { match ->
            val time = resolveTimeOfDay(input, match.range, "night") ?: return null
            return InterpretedDateTime.AbsoluteDateTime(LocalDateTime(currentDateTime.date, time))
        }

        dayWordTimeOfDayPattern.find(input)?.let { match ->
            val dayWord = match.groupValues[1].let { if (it == "this") "today" else it }
            val date = parseDayWord(dayWord) ?: return null
            val time = resolveTimeOfDay(input, match.range, match.groupValues[2]) ?: return null
            return InterpretedDateTime.AbsoluteDateTime(LocalDateTime(date, time))
        }

        dayOfWeekTimeOfDayPattern.find(input)?.let { match ->
            val date = parseNextDayOfWeek(match.groupValues[1]) ?: return null
            val time = resolveTimeOfDay(input, match.range, match.groupValues[2]) ?: return null
            return InterpretedDateTime.AbsoluteDateTime(LocalDateTime(date, time))
        }

        dayWordTimePattern.find(input)?.let { match ->
            val dayWord = match.groupValues[1]
            val timeStr = match.groupValues[2]
            val date = parseDayWord(dayWord) ?: return null
            // The pattern contains an explicit "at", so a bare hour is a time ("tomorrow at 8")
            val time = parseTimeString(timeStr, allowBareHour = true) ?: return null
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
            val time = parseTimeString(timeStr, allowBareHour = true) ?: return null
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
            val day = parseDayOfMonth(match.groupValues[2]) ?: return null
            val year = match.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull()
            val timeStr = match.groupValues[4]
            val date = parseMonthDay(monthName, day, year) ?: return null
            val time = parseTimeString(timeStr) ?: return null
            return InterpretedDateTime.AbsoluteDateTime(LocalDateTime(date, time))
        }

        timeMonthDayPattern.find(input)?.let { match ->
            val timeStr = match.groupValues[1]
            val monthName = match.groupValues[2]
            val day = parseDayOfMonth(match.groupValues[3]) ?: return null
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

    private fun resolveTimeOfDay(input: String, matchRange: IntRange, timeOfDay: String): LocalTime? {
        val remainder = input.removeRange(matchRange)
        val atClause = atTimePattern.find(remainder)
            ?.takeIf { it.groupValues[1].any { c -> c.isDigit() } }
        val timeStr = atClause?.groupValues?.get(1)
            ?: remainder.takeIf { r -> r.any { c -> c.isDigit() } }
            ?: return parseTimeOfDay(timeOfDay)
        // A numeric clause is an explicit time; if it can't parse, fail rather than
        // silently falling back to the vague time-of-day default the user overrode.
        val parsed = parseTimeString(timeStr, allowBareHour = true) ?: return null
        val amPmMissing = !amPmPattern.containsMatchIn(timeStr)
        return if (amPmMissing && parsed.hour in 1..11 && timeOfDay != "morning") {
            LocalTime(parsed.hour + 12, parsed.minute)
        } else {
            parsed
        }
    }

    private fun parseAbsoluteTime(input: String): InterpretedDateTime.AbsoluteTime? {
        atTimePattern.find(input)?.let { match ->
            val timeStr = match.groupValues[1]
            // An explicit "at" makes a bare hour like "at 5" unambiguously a time, so allow it
            // here. amPmExplicit stays false so the caller resolves it to the next 5 (am/pm).
            val time = parseTimeString(timeStr, allowBareHour = true) ?: return null
            return InterpretedDateTime.AbsoluteTime(time, amPmExplicit = amPmPattern.containsMatchIn(timeStr))
        }

        parseTimeString(input)?.let { time ->
            return InterpretedDateTime.AbsoluteTime(time, amPmExplicit = amPmPattern.containsMatchIn(input))
        }

        return null
    }

    private fun parseAbsoluteDate(input: String): InterpretedDateTime.AbsoluteDate? {
        weekendPattern.find(input)?.let { match ->
            val nextWeek = match.groupValues[1].lowercase() == "next"
            return InterpretedDateTime.AbsoluteDate(parseWeekend(nextWeek))
        }

        // "next week" resolves to the start of that week; the caller defaults a bare date to 9am.
        if (nextWeekPattern.matches(input)) {
            return InterpretedDateTime.AbsoluteDate(nextDayOfWeek(DayOfWeek.MONDAY))
        }

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
            val day = parseDayOfMonth(match.groupValues[2]) ?: return null
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

    private fun parseTimeString(timeStr: String, allowBareHour: Boolean = false): LocalTime? {
        val lowered = timeStr.trim().lowercase()
        namedTimePattern.find(lowered)?.let { match ->
            namedTimes[match.groupValues[1]]?.let { return it }
        }

        val cleaned = lowered
            .replace(".", "")
            .replace(" ", "")

        timePattern.find(cleaned)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val amPm = match.groupValues[3].takeIf { it.isNotEmpty() }
            val hasMinutes = match.groupValues[2].isNotEmpty()

            if (amPm != null) {
                val adjustedHour = adjustHour(hour, isPm = amPm == "pm")
                if (adjustedHour !in 0..23 || minute !in 0..59) return null
                return LocalTime(adjustedHour, minute)
            }

            if (hasMinutes) {
                // 24-hour format, e.g. "15:00"
                if (hour !in 0..23 || minute !in 0..59) return null
                return LocalTime(hour, minute)
            }

            // Bare hour, no minutes and no am/pm (e.g. "5"). Ambiguous in isolation, so only
            // honoured when the caller signals an explicit time context (like "at 5").
            if (!allowBareHour || hour !in 0..23) return null
            return LocalTime(hour, 0)
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
        return nextDayOfWeek(targetDay)
    }

    /** Next [targetDay] strictly after today, so the same weekday resolves a week out. */
    private fun nextDayOfWeek(targetDay: DayOfWeek): LocalDate {
        val daysUntil = (targetDay.ordinal - currentDateTime.dayOfWeek.ordinal + 7) % 7
        return currentDateTime.date + DatePeriod(days = if (daysUntil == 0) 7 else daysUntil)
    }

    /**
     * Resolves a "weekend" expression to the start of the weekend (Saturday). The caller (reminder
     * tools) defaults a bare date to 9am, so "this weekend" becomes 9am Saturday. If today is
     * already Saturday but the default 9am slot has passed, we roll to the next Saturday — otherwise
     * the bare-date 9am would be in the past and the scheduler would reject it. "next weekend" always
     * jumps to the following Saturday.
     */
    private fun parseWeekend(nextWeek: Boolean): LocalDate {
        val current = currentDateTime
        var daysUntilSaturday = (DayOfWeek.SATURDAY.ordinal - current.dayOfWeek.ordinal + 7) % 7
        // Only "this weekend" on Saturday can resolve to today and hit a past 9am slot; "next
        // weekend" is always >= 7 days out, so the roll-forward guard doesn't apply there.
        if (!nextWeek && daysUntilSaturday == 0 && current.time >= DEFAULT_BARE_DATE_TIME) {
            daysUntilSaturday = 7
        }
        val offset = daysUntilSaturday + if (nextWeek) 7 else 0
        return current.date + DatePeriod(days = offset)
    }

    /**
     * Day-of-month token that may be numeric ("5", "21st") or written out as a cardinal
     * ("twenty", "twenty one") or ordinal ("twentieth", "twenty-first"). Range validation
     * (1..31) stays in parseMonthDay.
     */
    private fun parseDayOfMonth(raw: String): Int? {
        val token = raw.trim().lowercase()
        Regex("""^(\d{1,2})(?:st|nd|rd|th)?$""").find(token)?.let {
            return it.groupValues[1].toIntOrNull()
        }
        val parts = token.split(' ', '-').filter { it.isNotBlank() }
        return when (parts.size) {
            1 -> dayWordToNumber(parts[0])?.toInt()
            2 -> {
                val tens = wordToNumber(parts[0]) ?: return null
                val ones = dayWordToNumber(parts[1]) ?: return null
                if ((tens == 20L || tens == 30L) && ones in 1L..9L) (tens + ones).toInt() else null
            }
            else -> null
        }
    }

    private fun dayWordToNumber(word: String): Long? = wordToNumber(word) ?: ordinalWordToNumber(word)

    private fun ordinalWordToNumber(word: String): Long? {
        return when (word) {
            "first" -> 1L
            "second" -> 2L
            "third" -> 3L
            "fourth" -> 4L
            "fifth" -> 5L
            "sixth" -> 6L
            "seventh" -> 7L
            "eighth" -> 8L
            "ninth" -> 9L
            "tenth" -> 10L
            "eleventh" -> 11L
            "twelfth" -> 12L
            "thirteenth" -> 13L
            "fourteenth" -> 14L
            "fifteenth" -> 15L
            "sixteenth" -> 16L
            "seventeenth" -> 17L
            "eighteenth" -> 18L
            "nineteenth" -> 19L
            "twentieth" -> 20L
            "thirtieth" -> 30L
            else -> null
        }
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
        // Time the reminder tools assign to a bare date (no explicit time). Kept in sync with the
        // 9am default in ReminderTool/ListTool so weekend resolution doesn't land on a past slot.
        private val DEFAULT_BARE_DATE_TIME = LocalTime(9, 0)

        // Word-number patterns used by normalizeTimeExpressions
        private const val HOUR_WORD_PATTERN = """(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)"""
        private const val MINUTE_ONES_BODY = """one|two|three|four|five|six|seven|eight|nine"""
        private const val MINUTE_TENS_PATTERN = """(?:twenty|thirty|forty|fifty)"""
        private const val MINUTE_ONES_PATTERN = """(?:$MINUTE_ONES_BODY)"""
        private const val MINUTE_TEENS_PATTERN = """(?:ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen)"""
        private const val MINUTE_COMPOUND_PATTERN = """(?:$MINUTE_TEENS_PATTERN|$MINUTE_TENS_PATTERN(?:\s+$MINUTE_ONES_PATTERN)?)"""
        private const val MINUTE_ONES_CAPTURE = """($MINUTE_ONES_BODY)"""
        private const val PAST_TO_MINUTE_PATTERN = """half|quarter|$MINUTE_COMPOUND_PATTERN|$MINUTE_ONES_PATTERN|\d{1,2}"""
        private const val TO_MINUTE_PATTERN = """half|quarter|$MINUTE_COMPOUND_PATTERN"""
        private const val AMPM_EXPR = """[ap]\.?\s*m\.?(?![a-z])"""

        // Normalizer rewrite patterns
        private val noonRegex = Regex("""\bnoon\b""")
        private val midnightRegex = Regex("""\bmidnight\b""")
        private val hourWordOclockRegex = Regex("""\b$HOUR_WORD_PATTERN\s+o'?clock\b""")
        private val oclockRegex = Regex("""\s*\bo'?clock\b""")
        private val inTheTimeOfDayRegex = Regex("""\s+in\s+the\s+(morning|afternoon|evening|night)\b""")
        private val atNightRegex = Regex("""\s+at\s+night\b""")
        private val minutesPastHourRegex =
            Regex("""\b(?:a\s+)?($PAST_TO_MINUTE_PATTERN)\s+(?:past|after)\s+(?:$HOUR_WORD_PATTERN|(\d{1,2}))\b""")
        private val minutesToHourRegex =
            Regex("""(\bat\s+)?\b(?:a\s+)?($TO_MINUTE_PATTERN)\s+(?:to|till|before|of)\s+(?:$HOUR_WORD_PATTERN\b|(\d{1,2})(?![0-9]))((?:\s*$AMPM_EXPR)?)""")
        private val ohMinutesRegex =
            Regex("""\b(?:$HOUR_WORD_PATTERN|(\d{1,2}))\s+(?:oh|o)\s+(?:$MINUTE_ONES_CAPTURE|(\d))\b""")
        private val wordHourMinuteRegex = Regex("""\b$HOUR_WORD_PATTERN\s+($MINUTE_COMPOUND_PATTERN|\d{2})\b""")
        private val digitHourWordMinuteRegex = Regex("""\b(\d{1,2})\s+($MINUTE_COMPOUND_PATTERN)\b""")
        private val digitPairAmPmRegex = Regex("""\b(\d{1,2})\s+(\d{2})(\s*$AMPM_EXPR)""")
        private val atDigitPairRegex = Regex("""\bat\s+(\d{1,2})\s+(\d{2})\b""")
        private val hourWordAmPmRegex = Regex("""\b$HOUR_WORD_PATTERN(\s*$AMPM_EXPR)(?:\b|$)""")
        private val atHourWordRegex = Regex("""\bat\s+$HOUR_WORD_PATTERN\b""")
        private val whitespaceRegex = Regex("""\s+""")

        // Shared regex fragments
        private const val TIME_EXPR = """\d{1,2}(?::\d{2})?\s*(?:a\.?\s*m\.?|p\.?\s*m\.?)"""
        private const val TIME_24_EXPR = """\d{1,2}:\d{2}"""
        private const val DAY_WORD_EXPR = """today|tomorrow"""
        private const val DAY_OF_WEEK_EXPR = """(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)"""
        private const val MONTH_EXPR = """(?:january|february|march|april|may|june|july|august|september|october|november|december)"""
        private const val TIME_OF_DAY_EXPR = """(?:morning|afternoon|evening|night)"""
        private const val NAMED_TIME_EXPR = """noon|midnight"""
        private const val ANY_TIME_EXPR = """$TIME_EXPR|$TIME_24_EXPR|$NAMED_TIME_EXPR"""
        private const val NUMBER_WORDS_EXPR = """two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty"""

        // Spelled-out day-of-month numbers, cardinal ("twenty one") or ordinal ("twenty-first").
        // Compound forms come first so "twenty one" matches before the bare "twenty", and each
        // ordinal precedes the cardinal it starts with so "tenth" doesn't match as "ten".
        private const val DAY_NUM_WORD_EXPR =
            """(?:twenty|thirty)[\s-](?:one|two|three|four|five|six|seven|eight|nine""" +
            """|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth)""" +
            """|tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|seventeenth""" +
            """|eighteenth|nineteenth|twentieth|thirtieth""" +
            """|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen""" +
            """|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth""" +
            """|twenty|thirty|one|two|three|four|five|six|seven|eight|nine"""
        private const val DAY_OF_MONTH_EXPR = """\d{1,2}(?:st|nd|rd|th)?|$DAY_NUM_WORD_EXPR"""
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
        // "tonight" is a single token, so it can't go through the day-word + time-of-day patterns.
        private val tonightPattern = Regex("""\btonight\b""")
        private val dayWordTimeOfDayPattern = Regex("""(today|tomorrow|this)\s+(morning|afternoon|evening|night)""")
        private val dayOfWeekTimeOfDayPattern = Regex("""(?:next|on)?\s*(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\s+(morning|afternoon|evening|night)""")
        private val dayWordTimePattern = Regex("""(today|tomorrow)\s+at\s+(.+)""")
        private val timeDayWordPattern = Regex("""(?:at\s+)?(.+?)\s+(today|tomorrow)""")
        private val dayOfWeekTimePattern = Regex("""(?:next|on)?\s*(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\s+at\s+(.+)""")
        private val timeDayOfWeekPattern = Regex("""(?:at\s+)?(.+?)\s+(?:next|on)?\s*(monday|tuesday|wednesday|thursday|friday|saturday|sunday)""")
        private val monthDayTimePattern = Regex("""(?:on\s+)?(january|february|march|april|may|june|july|august|september|october|november|december)\s+($DAY_OF_MONTH_EXPR)(?:,?\s+(\d{4}))?\s+at\s+(.+)""")
        private val timeMonthDayPattern = Regex("""(?:at\s+)?(.+?)\s+(?:on\s+)?(january|february|march|april|may|june|july|august|september|october|november|december)\s+($DAY_OF_MONTH_EXPR)(?:,?\s+(\d{4}))?""")
        private val numericDateTimePattern = Regex("""(\d{1,2})/(\d{1,2})\s+at\s+(.+)""")

        // Absolute time patterns
        private val atTimePattern = Regex("""at\s+(.+)""")
        private val timePattern = Regex("""^(\d{1,2})(?::(\d{2}))?(am|pm)?$""")
        // Word boundaries keep "noon" off "afternoon", which resolves to 14:00 instead.
        private val namedTimePattern = Regex("""\b($NAMED_TIME_EXPR)\b""")
        private val namedTimes = mapOf(
            "noon" to LocalTime(12, 0),
            "midnight" to LocalTime(0, 0),
        )
        private val amPmPattern = Regex(AMPM_EXPR)

        // Absolute date patterns
        private val weekendPattern = Regex("""^(?:(this|the|next|coming|this\s+coming)\s+)?weekend$""")
        private val nextWeekPattern = Regex("""^next\s+week$""")
        private val dayWordOnlyPattern = Regex("""^(today|tomorrow)$""")
        private val dayOfWeekPattern = Regex("""(?:next|on)?\s*(monday|tuesday|wednesday|thursday|friday|saturday|sunday)$""")
        private val monthDayPattern = Regex("""(?:on\s+)?(january|february|march|april|may|june|july|august|september|october|november|december)\s+($DAY_OF_MONTH_EXPR)(?:,?\s+(\d{4}))?$""")
        private val numericDatePattern = Regex("""^(\d{1,2})/(\d{1,2})$""")

        // Patterns for parseFromMessage, ordered by specificity (most specific first)
        private val messagePatterns = listOf(
            // Date + time combinations
            Regex("""(?:$DAY_WORD_EXPR)\s+at\s+(?:$ANY_TIME_EXPR)"""),
            Regex("""at\s+(?:$ANY_TIME_EXPR)\s+(?:$DAY_WORD_EXPR)"""),
            Regex("""(?:next\s+|on\s+)?$DAY_OF_WEEK_EXPR\s+at\s+(?:$ANY_TIME_EXPR)"""),
            Regex("""at\s+(?:$ANY_TIME_EXPR)\s+(?:next\s+|on\s+)?$DAY_OF_WEEK_EXPR"""),
            Regex("""(?:on\s+)?$MONTH_EXPR\s+(?:$DAY_OF_MONTH_EXPR)(?:,?\s+\d{4})?\s+at\s+(?:$ANY_TIME_EXPR)"""),
            Regex("""at\s+(?:$ANY_TIME_EXPR)\s+(?:on\s+)?$MONTH_EXPR\s+(?:$DAY_OF_MONTH_EXPR)(?:,?\s+\d{4})?"""),
            Regex("""\d{1,2}/\d{1,2}\s+at\s+(?:$ANY_TIME_EXPR)"""),
            // Date + time-of-day combinations. Explicit-time variants first so they aren't
            // truncated to the vague form; bare hour is allowed since "at" anchors it as a time.
            Regex("""(?:$DAY_WORD_EXPR|this)\s+$TIME_OF_DAY_EXPR\s+at\s+(?:$ANY_TIME_EXPR|\d{1,2})"""),
            Regex("""at\s+\d{1,2}\s+(?:$DAY_WORD_EXPR|this)\s+$TIME_OF_DAY_EXPR"""),
            Regex("""(?:$DAY_WORD_EXPR|this)\s+$TIME_OF_DAY_EXPR\s+at\s+(?:$TIME_EXPR|$TIME_24_EXPR|\d{1,2})"""),
            Regex("""at\s+(?:$TIME_EXPR|$TIME_24_EXPR|\d{1,2})\s+(?:$DAY_WORD_EXPR|this)\s+$TIME_OF_DAY_EXPR"""),
            Regex("""(?:$TIME_EXPR|$TIME_24_EXPR)\s+(?:$DAY_WORD_EXPR|this)\s+$TIME_OF_DAY_EXPR"""),
            Regex("""(?:$DAY_WORD_EXPR|this)\s+$TIME_OF_DAY_EXPR"""),
            Regex("""tonight\s+at\s+(?:$TIME_EXPR|$TIME_24_EXPR|\d{1,2})"""),
            Regex("""at\s+(?:$TIME_EXPR|$TIME_24_EXPR|\d{1,2})\s+tonight"""),
            Regex("""(?:$TIME_EXPR|$TIME_24_EXPR)\s+tonight"""),
            Regex("""(?:next\s+|on\s+)?$DAY_OF_WEEK_EXPR\s+$TIME_OF_DAY_EXPR\s+at\s+(?:$TIME_EXPR|$TIME_24_EXPR|\d{1,2})"""),
            Regex("""at\s+(?:$TIME_EXPR|$TIME_24_EXPR|\d{1,2})\s+(?:next\s+|on\s+)?$DAY_OF_WEEK_EXPR\s+$TIME_OF_DAY_EXPR"""),
            Regex("""(?:$TIME_EXPR|$TIME_24_EXPR)\s+(?:next\s+|on\s+)?$DAY_OF_WEEK_EXPR\s+$TIME_OF_DAY_EXPR"""),
            Regex("""(?:next\s+|on\s+)?$DAY_OF_WEEK_EXPR\s+$TIME_OF_DAY_EXPR"""),
            // Relative durations
            Regex("""(?:in\s+)?half\s+an?\s+hour(?:\s+from\s+now)?"""),
            Regex("""(?:in\s+)?half\s+a\s+day(?:\s+from\s+now)?"""),
            Regex("""in\s+$QUANTIFIER_EXPR\s+$UNIT_EXPR$COMPOUND_SEP$QUANTIFIER_EXPR\s+$UNIT_EXPR"""),
            Regex("""$QUANTIFIER_EXPR\s+$UNIT_EXPR$COMPOUND_SEP$QUANTIFIER_EXPR\s+$UNIT_EXPR\s+from\s+now"""),
            Regex("""in\s+$QUANTIFIER_EXPR\s+$UNIT_EXPR"""),
            Regex("""$QUANTIFIER_EXPR\s+$UNIT_EXPR\s+from\s+now"""),
            // "at <time>" (standalone)
            Regex("""at\s+(?:$ANY_TIME_EXPR)"""),
            // Date patterns
            Regex("""(?:next|on)\s+$DAY_OF_WEEK_EXPR"""),
            Regex("""(?:on\s+)?$MONTH_EXPR\s+(?:$DAY_OF_MONTH_EXPR)(?:,?\s+\d{4})?"""),
            Regex("""\b\d{1,2}/\d{1,2}\b"""),
            Regex("""\b(?:$DAY_WORD_EXPR)\b"""),
            Regex("""\btonight\b"""),
            Regex("""\b$DAY_OF_WEEK_EXPR\b"""),
            // Upcoming weekend. Unsupported past/recurring qualifiers ("last", "every", "this past")
            // are captured too so the candidate fails parse() and is skipped, rather than silently
            // matching the bare "weekend" token. (Lookbehind is avoided for Kotlin/Native support.)
            Regex("""\b(?:(?:last|every|past|this\s+past|this\s+coming|this|the|next|coming)\s+)?weekend\b"""),
            // The trailing \b keeps this off "next weekend", handled by the pattern above.
            Regex("""\bnext\s+week\b"""),
            // Bare time (e.g. "3pm", "noon")
            Regex("""\b$TIME_EXPR"""),
            Regex("""\b(?:$NAMED_TIME_EXPR)\b"""),
        )
    }
}

sealed class InterpretedDateTime {
    data class Relative(val duration: Duration = Duration.ZERO, val period: DatePeriod? = null) : InterpretedDateTime()
    data class AbsoluteDateTime(val dateTime: LocalDateTime) : InterpretedDateTime()
    data class AbsoluteTime(val time: LocalTime, val amPmExplicit: Boolean = false) : InterpretedDateTime()
    data class AbsoluteDate(val date: LocalDate) : InterpretedDateTime()
}

data class ParsedDateTimeResult(
    val dateTime: InterpretedDateTime,
    val matchedText: String,
    val range: IntRange
)