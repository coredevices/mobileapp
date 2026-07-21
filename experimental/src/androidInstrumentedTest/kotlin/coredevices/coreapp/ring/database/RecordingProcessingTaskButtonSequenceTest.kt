package coredevices.coreapp.ring.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import coredevices.indexai.data.entity.RingTransferInfo
import coredevices.libindex.database.entity.RingTransfer
import coredevices.libindex.database.entity.RingTransferStatus
import coredevices.ring.data.entity.room.RecordingProcessingTaskEntity
import coredevices.ring.data.entity.room.RecordingProcessingTaskType
import coredevices.ring.database.room.RingDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RecordingProcessingTaskButtonSequenceTest {
    private lateinit var db: RingDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<RingDatabase>(context.applicationContext)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun latestKnownButtonSequenceSurvivesRetryTaskWithoutSequence() = runBlocking {
        val transferId = db.ringTransferDao().insert(
            RingTransfer(
                recordingId = null,
                recordingEntryId = null,
                isCurrentIndexIteration = true,
                transferInfo = RingTransferInfo(collectionStartIndex = 42),
                status = RingTransferStatus.Completed,
            )
        )
        val taskDao = db.recordingProcessingTaskDao()
        taskDao.insertTask(audioTask(transferId, "double-click-hold"))
        taskDao.insertTask(audioTask(transferId, null))

        assertEquals(
            "double-click-hold",
            taskDao.getLatestButtonSequenceForTransfer(transferId),
        )
    }

    @Test
    fun unknownButtonSequenceRemainsUnknown() = runBlocking {
        assertNull(db.recordingProcessingTaskDao().getLatestButtonSequenceForTransfer(999))
    }

    private fun audioTask(transferId: Long, buttonSequence: String?) =
        RecordingProcessingTaskEntity(
            type = RecordingProcessingTaskType.AudioRecording,
            buttonSequence = buttonSequence,
            transferId = transferId,
            fileId = null,
            transcription = null,
        )
}
