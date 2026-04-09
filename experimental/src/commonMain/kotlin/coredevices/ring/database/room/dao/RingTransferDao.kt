package coredevices.ring.database.room.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import coredevices.indexai.data.entity.RingTransferInfo
import coredevices.indexai.database.dao.RecordingFeedItem
import coredevices.ring.data.entity.room.RingTransfer
import coredevices.ring.data.entity.room.RingTransferStatus
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface RingTransferDao {
    @Insert
    suspend fun insert(ringTransfer: RingTransfer): Long

    @Query("SELECT * FROM RingTransfer WHERE id = :id")
    suspend fun getById(id: Long): RingTransfer?

    @Query("SELECT * FROM RingTransfer WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<RingTransfer?>

    @Query("SELECT * FROM RingTransfer WHERE isCurrentIndexIteration = 1 AND transferInfo_collectionStartIndex = :startIndex")
    suspend fun getValidTransfersByStartIndex(startIndex: Int): List<RingTransfer>

    @Query("SELECT * FROM RingTransfer WHERE isCurrentIndexIteration = 1 AND transferInfo_collectionStartIndex >= :startIndex AND transferInfo_collectionStartIndex <= :endIndex")
    suspend fun getValidTransfersByRange(startIndex: Int, endIndex: Int): List<RingTransfer>

    @Query("UPDATE RingTransfer SET isCurrentIndexIteration = 0 WHERE isCurrentIndexIteration = 1")
    suspend fun markTransfersAsPreviousIndexIteration()

    @Query("UPDATE RingTransfer SET isCurrentIndexIteration = 0 WHERE id IN (:id)")
    suspend fun markTransferAsPreviousIndexIteration(id: List<Long>)

    @Query("UPDATE RingTransfer SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: RingTransferStatus)

    @Query("SELECT * FROM RingTransfer ORDER BY createdAt DESC LIMIT 1")
    suspend fun getMostRecentTransfer(): RingTransfer?

    @Query("SELECT * FROM RingTransfer WHERE createdAt > :timestamp")
    fun getTransfersAfterFlow(timestamp: Instant): Flow<List<RingTransfer>>

    @Query("UPDATE RingTransfer SET fileId = :fileId WHERE id = :id")
    suspend fun updateTransferFileId(
        id: Long,
        fileId: String
    )

    @Update(entity = RingTransfer::class)
    suspend fun updateTransferInfo(update: TransferInfoUpdate)

    @Query("UPDATE RingTransfer SET recordingId = :recordingId WHERE id = :id")
    suspend fun linkRecordingToTransfer(
        id: Long,
        recordingId: Long,
    )

    @Query("UPDATE RingTransfer SET recordingEntryId = :recordingId WHERE id = :id")
    suspend fun linkRecordingEntryToTransfer(
        id: Long,
        recordingId: Long,
    )

    @Transaction
    @Query("""
        SELECT * FROM RingTransferFeedItem
        WHERE
        (:includeDiscarded OR transfer_id IS NULL OR transfer_status != 'Discarded')
        AND
        (transfer_id IS NOT NULL OR feedItem_rootRecordingId IS NOT NULL)
        ORDER BY COALESCE(feedItem_localTimestamp, transfer_createdAt) DESC
    """)
    fun getPaginatedTransfersWithFeedItem(includeDiscarded: Boolean): PagingSource<Int, RingTransferFeedItem>

    @Transaction
    @Query("""
        SELECT * FROM RingTransferFeedItem
        WHERE transfer_id = :transferId
        LIMIT 1
    """)
    fun getTransferWithFeedItemByIdFlow(transferId: Long): Flow<RingTransferFeedItem?>
}

data class TransferInfoUpdate(
    val id: Long,
    @Embedded("transferInfo_")
    val info: RingTransferInfo
)

@DatabaseView(
    """
    SELECT
            RT.id AS transfer_id,
            RT.recordingId AS transfer_recordingId,
            RT.recordingEntryId AS transfer_recordingEntryId,
            RT.isCurrentIndexIteration AS transfer_isCurrentIndexIteration,
            RT.status AS transfer_status,
            RT.fileId AS transfer_fileId,
            RT.createdAt AS transfer_createdAt,
            RT.transferInfo_collectionStartIndex AS transfer_transferInfo_collectionStartIndex,
            RT.transferInfo_collectionEndIndex AS transfer_transferInfo_collectionEndIndex,
            RT.transferInfo_buttonPressed AS transfer_transferInfo_buttonPressed,
            RT.transferInfo_buttonReleased AS transfer_transferInfo_buttonReleased,
            RT.transferInfo_advertisementReceived AS transfer_transferInfo_advertisementReceived,
            RT.transferInfo_transferCompleted AS transfer_transferInfo_transferCompleted,
            RT.transferInfo_buttonReleaseAdvertisementLatencyMs AS transfer_transferInfo_buttonReleaseAdvertisementLatencyMs,

            RF.rootRecordingId AS feedItem_rootRecordingId,
            RF.localTimestamp AS feedItem_localTimestamp,
            RF.semantic_result AS feedItem_semantic_result,
            RF.id AS feedItem_id,
            RF.recordingId AS feedItem_recordingId,
            RF.timestamp AS feedItem_timestamp,
            RF.fileName AS feedItem_fileName,
            RF.status AS feedItem_status,
            RF.transcription AS feedItem_transcription,
            RF.error AS feedItem_error,
            RF.ringTransferInfo AS feedItem_ringTransferInfo,
            RF.userMessageId AS feedItem_userMessageId

        FROM RingTransfer AS RT
        LEFT JOIN RecordingFeedItem AS RF ON RT.recordingId = RF.rootRecordingId
        UNION ALL
        SELECT 
            RT.id AS transfer_id,
            RT.recordingId AS transfer_recordingId,
            RT.recordingEntryId AS transfer_recordingEntryId,
            RT.isCurrentIndexIteration AS transfer_isCurrentIndexIteration,
            RT.status AS transfer_status,
            RT.fileId AS transfer_fileId,
            RT.createdAt AS transfer_createdAt,
            RT.transferInfo_collectionStartIndex AS transfer_transferInfo_collectionStartIndex,
            RT.transferInfo_collectionEndIndex AS transfer_transferInfo_collectionEndIndex,
            RT.transferInfo_buttonPressed AS transfer_transferInfo_buttonPressed,
            RT.transferInfo_buttonReleased AS transfer_transferInfo_buttonReleased,
            RT.transferInfo_advertisementReceived AS transfer_transferInfo_advertisementReceived,
            RT.transferInfo_transferCompleted AS transfer_transferInfo_transferCompleted,
            RT.transferInfo_buttonReleaseAdvertisementLatencyMs AS transfer_transferInfo_buttonReleaseAdvertisementLatencyMs,

            RF.rootRecordingId AS feedItem_rootRecordingId,
            RF.localTimestamp AS feedItem_localTimestamp,
            RF.semantic_result AS feedItem_semantic_result,
            RF.id AS feedItem_id,
            RF.recordingId AS feedItem_recordingId,
            RF.timestamp AS feedItem_timestamp,
            RF.fileName AS feedItem_fileName,
            RF.status AS feedItem_status,
            RF.transcription AS feedItem_transcription,
            RF.error AS feedItem_error,
            RF.ringTransferInfo AS feedItem_ringTransferInfo,
            RF.userMessageId AS feedItem_userMessageId
            
        FROM RecordingFeedItem AS RF
        LEFT JOIN RingTransfer AS RT ON RF.rootRecordingId = RT.recordingId
        WHERE RT.id IS NULL
"""
)
data class RingTransferFeedItem(
    @Embedded("transfer_")
    val ringTransfer: RingTransfer?,
    @Embedded("feedItem_")
    val feedItem: RecordingFeedItem?
)