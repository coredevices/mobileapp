package coredevices.ring.agent

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentTimeContextTest {

    // 2026-07-01 is a Wednesday (2026-06-10 is Wednesday per ShareActionHandlerTest, +21 days).
    private val newYork = TimeZone.of("America/New_York")
    private val instant = LocalDateTime(2026, 7, 1, 14, 30).toInstant(newYork)

    @Test
    fun formatsCurrentTimeInGivenZone() {
        assertEquals(
            "For reference, the current date and time is Wednesday 2026-07-01 14:30 (America/New_York). " +
                "Only use this if the request depends on the current time (e.g. weather, news, or a " +
                "relative date like \"today\"); otherwise ignore it.",
            currentTimeContext(instant, newYork),
        )
    }

    @Test
    fun convertsSameInstantIntoTheRequestedZone() {
        // Same physical moment, expressed in Los Angeles (UTC-7 in July) -> 11:30.
        assertEquals(
            "For reference, the current date and time is Wednesday 2026-07-01 11:30 (America/Los_Angeles). " +
                "Only use this if the request depends on the current time (e.g. weather, news, or a " +
                "relative date like \"today\"); otherwise ignore it.",
            currentTimeContext(instant, TimeZone.of("America/Los_Angeles")),
        )
    }
}
