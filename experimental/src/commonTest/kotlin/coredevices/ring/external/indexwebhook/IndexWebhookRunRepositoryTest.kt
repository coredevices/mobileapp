package coredevices.ring.external.indexwebhook

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import coredevices.ring.service.button.RingGesture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class IndexWebhookRunRepositoryTest {

    private val settings: Settings = MapSettings()
    private val repository = IndexWebhookRunRepository(settings)

    private suspend fun record(gesture: RingGesture, index: Int) = repository.record(
        gesture = gesture,
        ok = true,
        status = "200 OK",
        detail = "run $index",
        byteSize = index.toLong(),
        durationMs = 10L,
        timestamp = Instant.fromEpochMilliseconds(index * 1000L),
    )

    @Test
    fun retentionKeepsTheMostRecentRunsPerGesture() = runTest {
        val extra = 5
        repeat(IndexWebhookRunRepository.MAX_RUNS_PER_GESTURE + extra) {
            record(RingGesture.Hold, it)
        }

        val kept = repository.runs(RingGesture.Hold).first()
        assertEquals(IndexWebhookRunRepository.MAX_RUNS_PER_GESTURE, kept.size)
        assertEquals(
            IndexWebhookRunRepository.MAX_RUNS_PER_GESTURE + extra - 1,
            kept.first().byteSize.toInt(),
        )
        assertEquals(extra, kept.last().byteSize.toInt())
    }

    @Test
    fun retentionIsCountedPerGesture() = runTest {
        repeat(IndexWebhookRunRepository.MAX_RUNS_PER_GESTURE + 3) { record(RingGesture.Hold, it) }
        repeat(2) { record(RingGesture.ClickHold, it) }

        assertEquals(
            IndexWebhookRunRepository.MAX_RUNS_PER_GESTURE,
            repository.runs(RingGesture.Hold).first().size,
        )
        assertEquals(2, repository.runs(RingGesture.ClickHold).first().size)
    }

    @Test
    fun recordedRunKeepsItsDetail() = runTest {
        repository.record(
            gesture = RingGesture.ClickHold,
            ok = false,
            status = "500 ERROR",
            detail = "server error",
            byteSize = 42L,
            durationMs = 7L,
        )

        val run = repository.runs(RingGesture.ClickHold).first().single()
        assertEquals("500 ERROR", run.status)
        assertEquals("server error", run.detail)
        assertEquals(42L, run.byteSize)
        assertEquals(7L, run.durationMs)
    }

    @Test
    fun runsSurviveANewRepositoryOverTheSameSettings() = runTest {
        repeat(3) { record(RingGesture.Hold, it) }

        val reloaded = IndexWebhookRunRepository(settings).runs(RingGesture.Hold).first()

        assertEquals(3, reloaded.size)
        assertEquals(2, reloaded.first().byteSize.toInt())
    }

    @Test
    fun triggerKeyedRunsAreRekeyedOntoTheirGesture() = runTest {
        val store = MapSettings(
            "index_webhook_runs_SingleClickHold" to storedRun("hold run"),
            "index_webhook_runs_DoubleClickHold" to storedRun("click hold run"),
        )

        val migrated = IndexWebhookRunRepository(store)

        assertEquals("hold run", migrated.runs(RingGesture.Hold).first().single().detail)
        assertEquals("click hold run", migrated.runs(RingGesture.ClickHold).first().single().detail)
        assertEquals(null, store.getStringOrNull("index_webhook_runs_SingleClickHold"))
        assertEquals(null, store.getStringOrNull("index_webhook_runs_DoubleClickHold"))
    }

    @Test
    fun existingGestureRunsWinOverTriggerKeyedOnes() = runTest {
        val store = MapSettings(
            "index_webhook_runs_SingleClickHold" to storedRun("old run"),
            "index_webhook_runs_Hold" to storedRun("new run"),
        )

        val migrated = IndexWebhookRunRepository(store)

        assertEquals("new run", migrated.runs(RingGesture.Hold).first().single().detail)
        assertEquals(null, store.getStringOrNull("index_webhook_runs_SingleClickHold"))
    }

    private fun storedRun(detail: String) =
        """[{"timestampMs":1000,"ok":true,"status":"200 OK","detail":"$detail",""" +
            """"byteSize":5,"durationMs":9}]"""
}
