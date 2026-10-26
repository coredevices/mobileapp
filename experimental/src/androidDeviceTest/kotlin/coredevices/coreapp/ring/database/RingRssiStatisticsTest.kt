package coredevices.coreapp.ring.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import coredevices.ring.bugreport.RingRssiStatistics
import coredevices.ring.database.room.RingDatabase
import coredevices.ring.database.room.dao.RingRssiReadingDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RingRssiStatisticsTest {
    private lateinit var db: RingDatabase
    private lateinit var dao: RingRssiReadingDao
    private lateinit var stats: RingRssiStatistics

    private val fixedNow = Instant.fromEpochSeconds(1_800_000_000)
    private val clock = object : Clock {
        override fun now() = fixedNow
    }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<RingDatabase>(context = context.applicationContext)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        dao = db.ringRssiReadingDao()
        stats = RingRssiStatistics(dao, clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(age: Duration, ringRx: Float, phone: Float?, serial: String = "RING-A") =
        stats.record(ringSerial = serial, ringRxRssi = ringRx, phoneRssi = phone, timestamp = fixedNow - age)

    @Test
    fun emptyWindowHasNoSamples() = runBlocking {
        val s = stats.windowStats("All time", null)
        assertEquals(0, s.sampleCount)
        assertNull(s.latest)
        assertNull(s.medianAsymmetry)
        assertNull(s.upperTailProportion)
        assertTrue(stats.summary().contains("n: 0 / 0 / 0 / 0"))
    }

    @Test
    fun windowsSelectByAge() = runBlocking {
        seed(1.minutes, -90f, -60f)
        seed(5.hours, -91f, -61f)
        seed(3.days, -92f, -62f)
        seed(30.days, -93f, -63f)

        assertEquals(1, stats.windowStats("3h", 3.hours).sampleCount)
        assertEquals(2, stats.windowStats("24h", 24.hours).sampleCount)
        assertEquals(3, stats.windowStats("7d", 7.days).sampleCount)
        assertEquals(4, stats.windowStats("all", null).sampleCount)
        assertEquals(-90f, stats.windowStats("all", null).latest!!.ringRxRssi)
    }

    @Test
    fun extremesMedianAndTails() = runBlocking {
        // asymmetry = phone - ringRx: 10, 12, 20, 30, 31
        seed(50.minutes, -80f, -70f)
        seed(40.minutes, -82f, -70f)
        seed(30.minutes, -90f, -70f)
        seed(20.minutes, -95f, -65f)
        seed(10.minutes, -96f, -65f)

        val s = stats.windowStats("All time", null)
        assertEquals(5, s.sampleCount)
        assertEquals(-65f, s.maxPhoneRssi!!.phoneRssi)
        assertEquals(31f, s.maxAsymmetry!!.asymmetryDb)
        assertEquals(10f, s.minAsymmetry!!.asymmetryDb)
        assertEquals(20f, s.medianAsymmetry!!.asymmetryDb)
        // 30 and 31 are within 6 dB of the max; 10 and 12 within 6 dB of the min.
        assertEquals(0.4f, s.upperTailProportion!!, 1e-6f)
        assertEquals(0.4f, s.lowerTailProportion!!, 1e-6f)
    }

    @Test
    fun evenCountUsesLowerMedian() = runBlocking {
        seed(4.minutes, -80f, -70f) // 10
        seed(3.minutes, -82f, -70f) // 12
        seed(2.minutes, -90f, -70f) // 20
        seed(1.minutes, -95f, -65f) // 30

        assertEquals(12f, stats.windowStats("All time", null).medianAsymmetry!!.asymmetryDb)
    }

    @Test
    fun readingsWithoutPhoneRssiCountButSkipAsymmetry() = runBlocking {
        seed(2.minutes, -90f, null)
        seed(1.minutes, -92f, -62f)

        val s = stats.windowStats("All time", null)
        assertEquals(2, s.sampleCount)
        assertEquals(-92f, s.maxPhoneRssi!!.ringRxRssi)
        assertEquals(30f, s.medianAsymmetry!!.asymmetryDb)
        assertEquals(1f, s.upperTailProportion!!, 1e-6f)
    }

    @Test
    fun recordingForAnotherRingDropsOldRows() = runBlocking {
        seed(3.minutes, -90f, -60f, serial = "RING-A")
        seed(2.minutes, -91f, -61f, serial = "RING-A")
        seed(1.minutes, -80f, -70f, serial = "RING-B")

        val s = stats.windowStats("All time", null)
        assertEquals(1, s.sampleCount)
        assertEquals(RingRssiStatistics.ringSerialHash("RING-B"), s.latest!!.ringSerialHash)
        assertEquals(8, s.latest!!.ringSerialHash.length)
    }

    @Test
    fun heartbeatMetricsCoverOnlyReadingsSinceLastHeartbeat() = runBlocking {
        seed(2.hours, -95f, -60f) // before the last heartbeat
        seed(30.minutes, -90f, -70f)
        seed(10.minutes, -92f, -62f)

        val metrics = stats.heartbeatMetrics(since = fixedNow - 1.hours, now = fixedNow)
            .associate { it.name to it.value }
        assertEquals(2, metrics["ring.rssi.sample_count"])
        assertEquals(30f, metrics["ring.rssi.asymmetry_max_db"])
        assertEquals(20f, metrics["ring.rssi.asymmetry_min_db"])
        assertEquals(-62f, metrics["ring.rssi.latest_phone_dbm"])
    }

    @Test
    fun summaryFormatsReadings() = runBlocking {
        seed(1.minutes, -92.76f, -61f)

        val text = stats.summary()
        assertTrue(text, text.contains("n: 1 / 1 / 1 / 1"))
        assertTrue(text, text.contains("Latest sample: -92.8, -61.0, +31.8 @ "))
        assertTrue(text, text.contains("Upper tail: 100% / 100% / 100% / 100%"))
    }
}
