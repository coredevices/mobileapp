package coredevices.ring.agent.integrations

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class GTasksIntegrationTest {

    @Test
    fun eveningWestOfUtcKeepsTheLocalDate() {
        val la = TimeZone.of("America/Los_Angeles")
        // 11pm on the 28th in LA is already the 29th in UTC.
        val deadline = LocalDateTime(2026, 6, 28, 23, 0).toInstant(la)
        assertEquals(LocalDate(2026, 6, 28), deadline.toDueDate(la))
    }

    @Test
    fun morningEastOfUtcKeepsTheLocalDate() {
        val sydney = TimeZone.of("Australia/Sydney")
        // 8am on the 29th in Sydney is still the 28th in UTC.
        val deadline = LocalDateTime(2026, 6, 29, 8, 0).toInstant(sydney)
        assertEquals(LocalDate(2026, 6, 29), deadline.toDueDate(sydney))
    }

    @Test
    fun middayIsUnaffected() {
        val la = TimeZone.of("America/Los_Angeles")
        val deadline = LocalDateTime(2026, 6, 28, 12, 0).toInstant(la)
        assertEquals(LocalDate(2026, 6, 28), deadline.toDueDate(la))
    }

    @Test
    fun noDeadlineHasNoDueDate() {
        val deadline: Instant? = null
        assertNull(deadline.toDueDate(TimeZone.of("America/Los_Angeles")))
    }
}
