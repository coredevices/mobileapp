package coredevices.ring.bugreport

import com.russhwolf.settings.MapSettings
import coredevices.haversine.KMPHaversineDebugRebootReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class IndexRebootLogStoreTest {
    private val settings = MapSettings()
    private val store = IndexRebootLogStore(settings)
    private val t1 = Instant.fromEpochSeconds(1_000)
    private val t2 = Instant.fromEpochSeconds(2_000)

    private fun reason(code: Int, context: Int = 0, description: String? = "REASON_$code") =
        KMPHaversineDebugRebootReason(code.toUInt(), context.toUInt(), description)

    @Test
    fun emptyByDefault() {
        assertTrue(store.entries().isEmpty())
    }

    @Test
    fun recordsNewestFirstAndPersists() {
        store.record(t1, listOf(reason(2), reason(1)))

        val entries = IndexRebootLogStore(settings).entries()
        assertEquals(listOf(1u, 2u), entries.map { it.code })
        assertEquals(listOf(t1, t1), entries.map { it.timestamp })
    }

    @Test
    fun laterDumpsGoInFront() {
        store.record(t1, listOf(reason(2), reason(1)))
        store.record(t2, listOf(reason(4), reason(3)))

        val entries = store.entries()
        assertEquals(listOf(3u, 4u, 1u, 2u), entries.map { it.code })
        assertEquals(listOf(t2, t2, t1, t1), entries.map { it.timestamp })
    }

    @Test
    fun emptyDumpIsIgnored() {
        store.record(t1, listOf(reason(1)))
        store.record(t2, emptyList())

        assertEquals(listOf(t1), store.entries().map { it.timestamp })
    }

    @Test
    fun capsAtFifteenEntries() {
        store.record(t1, (17 downTo 1).map { reason(it) })

        assertEquals((1..15).map { it.toUInt() }, store.entries().map { it.code })

        store.record(t2, listOf(reason(19), reason(18)))

        val entries = store.entries()
        assertEquals(IndexRebootLogStore.MAX_ENTRIES, entries.size)
        assertEquals(listOf(18u, 19u) + (1..13).map { it.toUInt() }, entries.map { it.code })
        assertEquals(listOf(t2, t2), entries.take(2).map { it.timestamp })
    }

    @Test
    fun unreadableStoredValueIsDiscarded() {
        settings.putString("index_reboot_log", "nope")

        assertTrue(store.entries().isEmpty())
        store.record(t1, listOf(reason(1)))
        assertEquals(listOf(1u), store.entries().map { it.code })
    }

    @Test
    fun reasonFormatsCodeAndContextAsHex() {
        val entry = IndexRebootLogEntry(t1, 0x1Au, 0xDEADBEEFu, "WATCHDOG")
        assertEquals("WATCHDOG (code=0x0000001a, context=0xdeadbeef)", entry.reason)
        assertEquals("UNKNOWN (code=0x00000001, context=0x00000000)", IndexRebootLogEntry(t1, 1u, 0u, null).reason)
    }
}
