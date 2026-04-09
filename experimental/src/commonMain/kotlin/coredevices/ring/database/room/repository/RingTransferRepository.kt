package coredevices.ring.database.room.repository

import androidx.paging.PagingSource
import androidx.room.Transactor
import androidx.room.useWriterConnection
import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.RingTransferInfo
import coredevices.ring.data.entity.room.RingTransfer
import coredevices.ring.data.entity.room.RingTransferStatus
import coredevices.ring.database.room.RingDatabase
import coredevices.ring.database.room.dao.RingTransferDao
import coredevices.ring.database.room.dao.RingTransferFeedItem
import coredevices.ring.database.room.dao.TransferInfoUpdate
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

class RingTransferRepository(
    private val ringTransferDao: RingTransferDao,
    private val db: RingDatabase
) {
    companion object {
        private val logger = Logger.withTag("RingTransferRepository")
    }

    suspend fun createRingTransfer(
        status: RingTransferStatus = RingTransferStatus.Started,
        advertisementReceived: Instant,
        startIndex: Int,
        endIndex: Int? = null
    ): Long {
        val existing = ringTransferDao.getValidTransfersByStartIndex(startIndex)
        if (existing.isNotEmpty()) {
            val summary = existing.joinToString("\n") {it.id.toString() + " " + it.transferInfo.toString()}
            logger.w("Creating a new transfer for startIndex $startIndex but valid transfers already exist - this should have rolled over so we will pretend it did:\n${summary}}")
            ringTransferDao.markTransferAsPreviousIndexIteration(existing.map { it.id } )
        }
        return ringTransferDao.insert(
            RingTransfer(
                recordingId = null,
                recordingEntryId = null,
                isCurrentIndexIteration = true,
                transferInfo = RingTransferInfo(
                    collectionStartIndex = startIndex,
                    collectionEndIndex = endIndex,
                    advertisementReceived = advertisementReceived.toEpochMilliseconds()
                ),
                status = status
            )
        )
    }

    fun getRingTransferFlowById(id: Long) = ringTransferDao.getByIdFlow(id)

    suspend fun linkRecordingToTransfer(
        transferId: Long,
        recordingId: Long
    ) {
        ringTransferDao.linkRecordingToTransfer(
            id = transferId,
            recordingId = recordingId
        )
    }

    suspend fun linkRecordingEntryToTransfer(
        transferId: Long,
        recordingEntryId: Long
    ) {
        ringTransferDao.linkRecordingEntryToTransfer(
            id = transferId,
            recordingId = recordingEntryId
        )
    }

    suspend fun getRingTransferById(id: Long): RingTransfer? {
        return ringTransferDao.getById(id)
    }

    suspend fun getMostRecentTransfer(): RingTransfer? {
        return ringTransferDao.getMostRecentTransfer()
    }

    fun getTransfersAfterFlow(timestamp: Instant): Flow<List<RingTransfer>> {
        return ringTransferDao.getTransfersAfterFlow(timestamp)
    }

    suspend fun getLastValidTransferByStartIndex(startIndex: Int): RingTransfer? {
        val transfers = ringTransferDao.getValidTransfersByStartIndex(startIndex)
        return if (transfers.size > 1) {
            val summary = transfers.joinToString("\n") {it.id.toString() + " " + it.transferInfo.toString()}
            logger.w("Multiple valid transfers found for startIndex $startIndex - this should have rolled over so we will pretend it did:\n${summary}}")
            null
        } else {
            transfers.firstOrNull()
        }
    }

    suspend fun getPendingTransfersByRange(range: IntRange): List<RingTransfer> {
        val transfers = ringTransferDao.getValidTransfersByRange(range.first, range.last)
        return transfers.filter { it.status == RingTransferStatus.Started }
    }

    suspend fun markTransfersAsPreviousIndexIteration() {
        ringTransferDao.markTransfersAsPreviousIndexIteration()
    }

    suspend fun updateTransferStatus(
        id: Long,
        status: RingTransferStatus
    ) {
        ringTransferDao.updateStatus(id, status)
    }

    suspend fun updateTransferFileId(
        id: Long,
        fileId: String
    ) {
        ringTransferDao.updateTransferFileId(id, fileId)
    }

    suspend fun markTransferCompleteAndSetFileId(
        id: Long,
        fileId: String
    ) {
        db.useWriterConnection { tr ->
            tr.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
                ringTransferDao.updateStatus(id, RingTransferStatus.Completed)
                ringTransferDao.updateTransferFileId(id, fileId)
            }
        }
    }

    suspend fun updateTransferInfo(
        id: Long,
        transferInfo: RingTransferInfo
    ) {
        ringTransferDao.updateTransferInfo(TransferInfoUpdate(
            id = id,
            info = transferInfo
        ))
    }

    fun getPaginatedTransfersWithFeedItem(includeDiscarded: Boolean = false): PagingSource<Int, RingTransferFeedItem> {
        return ringTransferDao.getPaginatedTransfersWithFeedItem(includeDiscarded)
    }

    fun getTransferWithFeedItemFlow(transferId: Long): Flow<RingTransferFeedItem?> {
        return ringTransferDao.getTransferWithFeedItemByIdFlow(transferId)
    }
}