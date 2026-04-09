package coredevices.indexai.data.entity.mcp_sandbox

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    primaryKeys = ["groupId", "builtinMcpName"],
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["groupId", "builtinMcpName"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = McpSandboxGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BuiltinMcpGroupAssociation(
    val groupId: Long,
    val builtinMcpName: String,
)