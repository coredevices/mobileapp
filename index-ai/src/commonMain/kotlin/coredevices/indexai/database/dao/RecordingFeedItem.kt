package coredevices.indexai.database.dao

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.mcp.data.SemanticResult
import kotlin.time.Instant

@DatabaseView("""
    SELECT 
        lr.id AS rootRecordingId, 
        lr.localTimestamp,
        re.*, 
        cm.semantic_result
    FROM LocalRecording AS lr
    LEFT JOIN RecordingEntryEntity AS re ON re.id = (
        SELECT id 
        FROM RecordingEntryEntity 
        WHERE recordingId = lr.id 
        ORDER BY timestamp DESC
        LIMIT 1
    )
    LEFT JOIN ConversationMessageEntity AS cm ON cm.id = (
        SELECT id 
        FROM ConversationMessageEntity 
        WHERE recordingId = lr.id 
        AND role = 'tool' 
        ORDER BY timestamp DESC 
        LIMIT 1
    )
""")
data class RecordingFeedItem(
    @ColumnInfo(name = "rootRecordingId")
    val id: Long,
    val localTimestamp: Instant,
    @Embedded val entry: RecordingEntryEntity?,
    @ColumnInfo(name = "semantic_result")
    val semanticResult: SemanticResult?
)
