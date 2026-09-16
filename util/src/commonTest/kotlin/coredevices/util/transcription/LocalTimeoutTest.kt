package coredevices.util.transcription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val SAMPLE_RATE = 16000

private fun clip(seconds: Double) =
    ByteArray((seconds * SAMPLE_RATE * 2).toInt())

private fun budgetFor(seconds: Double) =
    HybridTranscriptionService.localTimeout(clip(seconds), SAMPLE_RATE)

private fun budgetFor(seconds: Double, remaining: Duration, reserve: Duration = Duration.ZERO) =
    HybridTranscriptionService.localTimeout(clip(seconds), SAMPLE_RATE, remaining, reserve)

class LocalTimeoutTest {

    @Test
    fun tenSecondClipSurvivesALockedWeakDevice() {
        assertTrue(budgetFor(10.0) >= 26.5.seconds,
            "a 10s clip pays the 1300 bucket's 26.5s on little cores")
    }

    @Test
    fun everyBucketFitsItsBudgetAtTheWorstCaseClipLength() {
        val shortestClipToCost = listOf(
            1.1 to 5.9,
            2.1 to 7.7,
            3.1 to 11.1,
            5.1 to 16.2,
            8.1 to 26.5,
            13.1 to 40.5,
        )
        for ((seconds, lockedCost) in shortestClipToCost) {
            val budget = budgetFor(seconds)
            assertTrue(budget >= lockedCost.seconds,
                "${seconds}s clip: budget $budget < measured ${lockedCost}s")
        }
    }

    @Test
    fun scalesWithLength() {
        assertTrue(budgetFor(12.0) > budgetFor(6.0))
        assertEquals(40.seconds, budgetFor(10.0))
    }

    @Test
    fun shortClipsStayTightlyBounded() {
        assertEquals(8.seconds, budgetFor(0.5))
        assertEquals(8.seconds, budgetFor(2.0))
    }

    @Test
    fun longClipsStopAtTheCap() {
        assertEquals(45.seconds, budgetFor(20.0))
        assertEquals(45.seconds, budgetFor(600.0))
    }

    @Test
    fun cappedByRemainingBudgetMinusReserve() {
        assertEquals(7.seconds, budgetFor(10.0, remaining = 14.seconds, reserve = 7.seconds))
        assertEquals(35.seconds, budgetFor(20.0, remaining = 45.seconds, reserve = 10.seconds))
        assertEquals(14.seconds, budgetFor(10.0, remaining = 14.seconds))
    }

    @Test
    fun clipBudgetWinsWhenSmallerThanRemaining() {
        assertEquals(8.seconds, budgetFor(1.0, remaining = 45.seconds, reserve = 7.seconds))
        assertEquals(budgetFor(10.0), budgetFor(10.0, remaining = 45.seconds))
    }

    @Test
    fun exhaustedBudgetIsZeroNotNegative() {
        assertEquals(Duration.ZERO, budgetFor(5.0, remaining = 5.seconds, reserve = 7.seconds))
        assertEquals(Duration.ZERO, budgetFor(5.0, remaining = (-1).seconds))
    }

    @Test
    fun degenerateInputsDoNotThrow() {
        assertEquals(8.seconds, HybridTranscriptionService.localTimeout(ByteArray(0), 0))
        assertEquals(45.seconds, HybridTranscriptionService.localTimeout(ByteArray(1000), 0))
        assertEquals(45.seconds, HybridTranscriptionService.localTimeout(ByteArray(32000), -16000))
    }
}
