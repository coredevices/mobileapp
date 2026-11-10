package coredevices.indexai.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Out-of-sample acceptance battery for natural reminder-time phrasings.
 *
 * Written as a holdout set before the connector/normalizer generalization was implemented:
 * none of these exact strings were used to drive the implementation. Reference clock is
 * Wednesday, January 15, 2025 at 10:30 (same as HumanDateTimeParserTest), so a weekday name
 * resolves to its next occurrence strictly after today (wednesday -> Jan 22).
 *
 * Failures print as a scored report rather than stopping at the first assert, so the
 * pass-rate of the whole battery is visible in one run.
 */
class HumanDateTimeParserOutOfSampleTest {

    // Wednesday, January 15, 2025 at 10:30
    private val referenceDateTime = LocalDateTime(2025, 1, 15, 10, 30)
    private val parser = HumanDateTimeParser(object : Clock {
        override fun now(): kotlin.time.Instant = referenceDateTime.toInstant(TimeZone.UTC)
    }, TimeZone.UTC)

    private sealed interface Want {
        data class DateTime(val v: LocalDateTime) : Want
        data class Time(val v: LocalTime, val amPmExplicit: Boolean? = null) : Want
        data class DateOnly(val v: LocalDate) : Want
        data class Rel(val v: Duration) : Want
        data object Null : Want
    }

    private fun check(input: String, want: Want, failures: MutableList<String>) {
        val got = try { parser.parse(input) } catch (e: Exception) { "THREW ${e::class.simpleName}" }
        val ok = when (want) {
            is Want.DateTime -> (got as? InterpretedDateTime.AbsoluteDateTime)?.dateTime == want.v
            is Want.Time -> (got as? InterpretedDateTime.AbsoluteTime)?.let {
                it.time == want.v && (want.amPmExplicit == null || it.amPmExplicit == want.amPmExplicit)
            } ?: false
            is Want.DateOnly -> (got as? InterpretedDateTime.AbsoluteDate)?.date == want.v
            is Want.Rel -> (got as? InterpretedDateTime.Relative)?.duration == want.v
            Want.Null -> got == null
        }
        if (!ok) failures += "parse(\"$input\") => $got, wanted $want"
    }

    private fun dt(month: Int, day: Int, hour: Int, min: Int) = Want.DateTime(LocalDateTime(2025, month, day, hour, min))

    @Test
    fun outOfSampleBattery() {
        val f = mutableListOf<String>()
        var total = 0
        fun case(input: String, want: Want) { total++; check(input, want, f) }

        // ===== A. day + marked time, no "at", both orders, comma tolerated =====
        case("thursday 7pm", dt(1, 16, 19, 0))
        case("thursday, 7pm", dt(1, 16, 19, 0))
        case("thursday 7:20pm", dt(1, 16, 19, 20))
        case("saturday, 9 a.m.", dt(1, 18, 9, 0))
        case("sunday 6:45 am", dt(1, 19, 6, 45))
        case("tomorrow 6:15am", dt(1, 16, 6, 15))
        case("tomorrow, 6:15am", dt(1, 16, 6, 15))
        case("today 11:59pm", dt(1, 15, 23, 59))
        case("today, 4pm", dt(1, 15, 16, 0))
        case("next friday 10am", dt(1, 17, 10, 0))
        case("next monday, 10:30am", dt(1, 20, 10, 30))
        case("7pm thursday", dt(1, 16, 19, 0))
        case("6:15am tomorrow", dt(1, 16, 6, 15))
        case("9 a.m. saturday", dt(1, 18, 9, 0))
        case("10:30am next monday", dt(1, 20, 10, 30))
        case("sunday noon", dt(1, 19, 12, 0))
        case("noon sunday", dt(1, 19, 12, 0))
        case("tomorrow noon", dt(1, 16, 12, 0))
        case("tomorrow midnight", dt(1, 16, 0, 0))
        case("friday noon", dt(1, 17, 12, 0))
        case("april 12 3pm", Want.DateTime(LocalDateTime(2025, 4, 12, 15, 0)))
        case("april 12, 3pm", Want.DateTime(LocalDateTime(2025, 4, 12, 15, 0)))
        case("3pm april 12", Want.DateTime(LocalDateTime(2025, 4, 12, 15, 0)))
        case("february 1 noon", Want.DateTime(LocalDateTime(2025, 2, 1, 12, 0)))

        // ===== B. o'clock with digits (and words), all contexts =====
        case("7 o'clock", Want.Time(LocalTime(7, 0), amPmExplicit = false))
        case("11 o'clock", Want.Time(LocalTime(11, 0), amPmExplicit = false))
        case("at 11 o'clock", Want.Time(LocalTime(11, 0), amPmExplicit = false))
        case("4 o'clock pm", Want.Time(LocalTime(16, 0), amPmExplicit = true))
        case("4 oclock pm", Want.Time(LocalTime(16, 0), amPmExplicit = true))
        case("11 o'clock tomorrow", dt(1, 16, 11, 0))
        case("tomorrow at 7 o'clock", dt(1, 16, 7, 0))
        case("tomorrow 7 o'clock", dt(1, 16, 7, 0))
        case("seven o'clock friday", dt(1, 17, 7, 0))
        case("monday at 10 o'clock", dt(1, 20, 10, 0))
        case("12 o'clock", Want.Time(LocalTime(12, 0), amPmExplicit = false))

        // ===== C. "at <bare hour>" next to a day word (consistency bug) =====
        case("at 9 tomorrow", dt(1, 16, 9, 0))
        case("at 4 today", dt(1, 15, 4, 0))
        case("at 6 friday", dt(1, 17, 6, 0))
        case("at 2 o'clock today", dt(1, 15, 2, 0))
        case("today at 2 o'clock", dt(1, 15, 2, 0))

        // ===== D. period-separated clock times =====
        case("2.30pm", Want.Time(LocalTime(14, 30), amPmExplicit = true))
        case("12.05 p.m.", Want.Time(LocalTime(12, 5), amPmExplicit = true))
        case("1.45 p.m.", Want.Time(LocalTime(13, 45), amPmExplicit = true))
        case("tomorrow 4.15pm", dt(1, 16, 16, 15))
        case("at 7.30 pm", Want.Time(LocalTime(19, 30), amPmExplicit = true))
        case("thursday 8.05am", dt(1, 16, 8, 5))

        // ===== E. duplicated day word (LLM stutter tolerance) =====
        case("tomorrow at 5pm tomorrow", dt(1, 16, 17, 0))
        case("today at 2 o'clock today", dt(1, 15, 2, 0))
        case("today 3pm today", dt(1, 15, 15, 0))

        // ===== F. day-of-month date forms =====
        case("the 21st", Want.DateOnly(LocalDate(2025, 1, 21)))
        case("on the 3rd", Want.DateOnly(LocalDate(2025, 2, 3)))
        case("the 12th", Want.DateOnly(LocalDate(2025, 2, 12)))
        case("22nd of july", Want.DateOnly(LocalDate(2025, 7, 22)))
        case("june the first", Want.DateOnly(LocalDate(2025, 6, 1)))
        case("12th of august at noon", Want.DateTime(LocalDateTime(2025, 8, 12, 12, 0)))
        case("the 21st at 5pm", dt(1, 21, 17, 0))

        // ===== G. vocabulary and modifiers =====
        case("midday", Want.Time(LocalTime(12, 0), amPmExplicit = true))
        case("midday tomorrow", dt(1, 16, 12, 0))
        case("around 4pm", Want.Time(LocalTime(16, 0), amPmExplicit = true))
        case("around noon", Want.Time(LocalTime(12, 0), amPmExplicit = true))
        case("by 10am", Want.Time(LocalTime(10, 0), amPmExplicit = true))
        case("by 5pm tomorrow", dt(1, 16, 17, 0))
        case("6pm sharp", Want.Time(LocalTime(18, 0), amPmExplicit = true))
        case("8am sharp tomorrow", dt(1, 16, 8, 0))

        // ===== H. regression spot checks (already pass today; must keep passing) =====
        case("tomorrow at 3pm", dt(1, 16, 15, 0))
        case("in 45 minutes", Want.Rel(45.minutes))
        case("next saturday morning", dt(1, 18, 9, 0))
        case("quarter past 9 am", Want.Time(LocalTime(9, 15), amPmExplicit = true))
        case("nine fifteen am", Want.Time(LocalTime(9, 15), amPmExplicit = true))
        case("wednesday at 8am", dt(1, 22, 8, 0))
        case("tonight", dt(1, 15, 21, 0))
        case("3:00pm today", dt(1, 15, 15, 0))

        // ===== I. negatives: must stay unparsed (false-positive guard) =====
        case("8 apples", Want.Null)
        case("2 pizzas", Want.Null)
        case("1.45 dollars", Want.Null)
        case("1.45", Want.Null)
        case("2.30", Want.Null)
        case("version 2.30", Want.Null)
        case("chapter 7", Want.Null)
        case("route 66", Want.Null)
        case("5 stars", Want.Null)
        case("80 kilos", Want.Null)
        case("number 12", Want.Null)
        case("tomorrow 8", Want.Null)          // bare unmarked hour: deliberately rejected
        case("tomorrow eight", Want.Null)      // bare unmarked word hour: deliberately rejected
        case("march 5 8", Want.Null)           // digit beside date: ambiguous, rejected
        case("the five of us", Want.Null)
        case("soon", Want.Null)
        case("later", Want.Null)
        case("sometime", Want.Null)
        case("", Want.Null)
        case("o'clock", Want.Null)

        // ===== J. parseFromMessage: extraction from full sentences =====
        fun msg(message: String, expectMatch: String?, expect: Want?) {
            total++
            val got = try { parser.parseFromMessage(message) } catch (e: Exception) { null }
            if (expectMatch == null) {
                if (got != null) f += "parseFromMessage(\"$message\") => matched '${got.matchedText}', wanted no match"
            } else {
                if (got == null) { f += "parseFromMessage(\"$message\") => null, wanted '$expectMatch'"; return }
                if (!got.matchedText.equals(expectMatch, ignoreCase = true)) {
                    f += "parseFromMessage(\"$message\") matched '${got.matchedText}', wanted '$expectMatch'"
                    return
                }
                if (expect != null) {
                    val ok = when (expect) {
                        is Want.DateTime -> (got.dateTime as? InterpretedDateTime.AbsoluteDateTime)?.dateTime == expect.v
                        is Want.Time -> (got.dateTime as? InterpretedDateTime.AbsoluteTime)?.time == expect.v
                        is Want.DateOnly -> (got.dateTime as? InterpretedDateTime.AbsoluteDate)?.date == expect.v
                        is Want.Rel -> (got.dateTime as? InterpretedDateTime.Relative)?.duration == expect.v
                        Want.Null -> false
                    }
                    if (!ok) f += "parseFromMessage(\"$message\") value ${got.dateTime}, wanted $expect"
                }
            }
        }

        msg("remind me tomorrow 6pm to call the dentist", "tomorrow 6pm", dt(1, 16, 18, 0))
        msg("set a reminder for friday 8am about the invoice", "friday 8am", dt(1, 17, 8, 0))
        // matchedText refers to the normalized message when normalization rewrites it (see
        // parseFromMessage docs): "7.45pm" -> "7:45pm", "around " is stripped.
        msg("remind me at 7.45pm to stretch", "at 7:45pm", Want.Time(LocalTime(19, 45)))
        msg("remind me around 4pm to leave early", "4pm", Want.Time(LocalTime(16, 0)))
        msg("remind me thursday noon to submit the report", "thursday 12:00 pm", dt(1, 16, 12, 0))
        msg("remind me tomorrow at 11 o'clock to check the oven", "tomorrow at 11:00", dt(1, 16, 11, 0))
        // false-positive guard in sentences: quantities must not become times
        msg("remind me to buy 8 apples", null, null)
        msg("remind me to order 2 pizzas for the party", null, null)
        msg("note that the part costs 1.45 dollars", null, null)
        msg("remind me to read chapter 7", null, null)
        msg("add 15 eggs to the shopping list", null, null)
        msg("remind me to buy a 2 liter bottle", null, null)
        // quantity + real date: only the date must be extracted
        msg("buy 8 apples tomorrow", "tomorrow", Want.DateOnly(LocalDate(2025, 1, 16)))

        println("OOS SUMMARY | total=$total failures=${f.size}")
        f.forEach { println("OOS FAIL | $it") }
        assertTrue(f.isEmpty(), "Out-of-sample battery failures (${f.size}/$total):\n" + f.joinToString("\n"))
    }
}
