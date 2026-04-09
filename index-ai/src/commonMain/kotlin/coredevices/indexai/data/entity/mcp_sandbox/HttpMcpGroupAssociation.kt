package coredevices.indexai.data.entity.mcp_sandbox

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["httpMcpId"])
    ],
    primaryKeys = ["groupId", "httpMcpId"],
    foreignKeys = [
        ForeignKey(
            entity = McpSandboxGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = HttpMcpServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["httpMcpId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class HttpMcpGroupAssociation(
    val groupId: Long,
    val httpMcpId: Long,
)