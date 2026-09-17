package io.rebble.libpebblecommon.database.dao

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.rebble.libpebblecommon.database.Database
import io.rebble.libpebblecommon.database.entity.Spo2ReadingEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class Spo2DaoTest {
    private lateinit var db: Database
    private lateinit var spo2Dao: Spo2Dao

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, Database::class.java)
            .allowMainThreadQueries()
            .build()
        spo2Dao = db.spo2Dao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRead() = runBlocking {
        val reading = Spo2ReadingEntity(
            timestamp = 1600000000L,
            spo2Percent = 97,
            quality = 6,
        )
        spo2Dao.insertSpo2Readings(listOf(reading))

        val latest = spo2Dao.getLatestSpo2Reading()
        assertNotNull(latest)
        assertEquals(97, latest.spo2Percent)
        assertEquals(6, latest.quality)
        assertEquals(1600000000L, latest.timestamp)
    }
}
