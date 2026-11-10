package coredevices.coreapp.ring.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import coredevices.indexai.data.entity.RingTransferInfo
import coredevices.libindex.database.dao.RingTransferDao
import coredevices.libindex.database.entity.RingTransfer
import coredevices.libindex.database.entity.RingTransferStatus
import coredevices.libindex.database.repository.RingTransferRepository
import coredevices.ring.database.room.RingDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Covers [RingTransferRepository.failStartedTransfersAtStartIndex]: a best-effort completion for a
 * buffer whose transfer row was retired by a re-pair is saved under a fresh row, and the stale row
 * must be failed instead of staying stuck in Started.
 */
class RingTransferRepositoryStaleStartIndexTest {
    private lateinit var db: RingDatabase
    private lateinit var dao: RingTransferDao
    private lateinit var repo: RingTransferRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<RingDatabase>(context = context.applicationContext)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        dao = db.ringTransferDao()
        repo = RingTransferRepository(dao, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(
        startIndex: Int,
        status: RingTransferStatus,
        currentIteration: Boolean = true,
    ): Long = dao.insert(
        RingTransfer(
            recordingId = null,
            recordingEntryId = null,
            isCurrentIndexIteration = currentIteration,
            transferInfo = RingTransferInfo(collectionStartIndex = startIndex),
            status = status,
        )
    )

    @Test
    fun failsStartedTransfersAtIndexInAnyIteration() = runBlocking {
        // Mirrors MOB-15078: the 402 transfer was retired by a re-pair (previous iteration) before
        // haversine flushed its buffer, so no valid row matched the completion.
        val retired = seed(402, RingTransferStatus.Started, currentIteration = false)
        val current = seed(402, RingTransferStatus.Started)
        val completedAtIndex = seed(402, RingTransferStatus.Completed, currentIteration = false)
        val startedBelow = seed(401, RingTransferStatus.Started)
        val startedAbove = seed(403, RingTransferStatus.Started)

        val failed = repo.failStartedTransfersAtStartIndex(402)

        assertEquals(setOf(retired, current), failed.map { it.id }.toSet())
        assertEquals(RingTransferStatus.Failed, dao.getById(retired)!!.status)
        assertEquals(RingTransferStatus.Failed, dao.getById(current)!!.status)
        assertEquals(RingTransferStatus.Completed, dao.getById(completedAtIndex)!!.status)
        assertEquals(RingTransferStatus.Started, dao.getById(startedBelow)!!.status)
        assertEquals(RingTransferStatus.Started, dao.getById(startedAbove)!!.status)
    }

    @Test
    fun returnsEmptyWhenNothingIsStuck() = runBlocking {
        seed(402, RingTransferStatus.Discarded)
        seed(402, RingTransferStatus.Saving)

        assertEquals(emptyList<Long>(), repo.failStartedTransfersAtStartIndex(402).map { it.id })
    }
}
