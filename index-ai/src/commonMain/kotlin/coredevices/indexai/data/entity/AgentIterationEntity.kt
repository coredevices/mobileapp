package coredevices.indexai.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import coredevices.indexai.agent.ToolDeclarationRecord
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

/** One inference round of an agent run, as given to the model. */
@Serializable
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = LocalRecording::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("recordingId")]
)
data class AgentIterationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    @Serializable(with = TolerantInstantSerializer::class)
    val timestamp: Instant = Clock.System.now(),
    val agent: String,
    val toolDeclarations: List<ToolDeclarationRecord>,
    val conversation: List<ConversationMessageDocument>,
)
