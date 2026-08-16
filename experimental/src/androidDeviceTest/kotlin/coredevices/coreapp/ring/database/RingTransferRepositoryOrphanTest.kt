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
 * Covers [RingTransferRepository.failOrphanedStartedTransfersBelow] — the MOB-8727 fix that stops
 * a transfer whose audio never arrived from lingering in Started (and showing a stuck
 * "Transferring" notification) once the ring advances to a later collection index.
 */
class RingTransferRepositoryOrphanTest {
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
    fun failsOnlyStartedTransfersStrictlyBelowIndex() = runBlocking {
        // Mirrors MOB-8727: collection 78's transfer is orphaned in Started when the ring
        // advances to 79. Only it should be failed.
        val orphan = seed(78, RingTransferStatus.Started)
        val completedBelow = seed(77, RingTransferStatus.Completed)
        val discardedBelow = seed(76, RingTransferStatus.Discarded)
        // Received its final data and is still writing audio to disk when the ring advances —
        // must NOT be orphaned even though its Completed write hasn't landed yet.
        val savingBelow = seed(75, RingTransferStatus.Saving)
        val startedAtIndex = seed(79, RingTransferStatus.Started)
        val startedAbove = seed(80, RingTransferStatus.Started)

        val failed = repo.failOrphanedStartedTransfersBelow(79)

        assertEquals(listOf(orphan), failed.map { it.id })
        assertEquals(RingTransferStatus.Failed, dao.getById(orphan)!!.status)
        // Terminal / in-progress transfers that aren't orphaned are left alone.
        assertEquals(RingTransferStatus.Completed, dao.getById(completedBelow)!!.status)
        assertEquals(RingTransferStatus.Discarded, dao.getById(discardedBelow)!!.status)
        assertEquals(RingTransferStatus.Saving, dao.getById(savingBelow)!!.status)
        assertEquals(RingTransferStatus.Started, dao.getById(startedAtIndex)!!.status)
        assertEquals(RingTransferStatus.Started, dao.getById(startedAbove)!!.status)
    }

    @Test
    fun ignoresPreviousIndexIterationTransfers() = runBlocking {
        // A rollover flips isCurrentIndexIteration to false; those transfers must not be touched.
        val previousIteration = seed(50, RingTransferStatus.Started, currentIteration = false)

        val failed = repo.failOrphanedStartedTransfersBelow(79)

        assertEquals(emptyList<Long>(), failed.map { it.id })
        assertEquals(RingTransferStatus.Started, dao.getById(previousIteration)!!.status)
    }
}
