package coredevices.indexai.data.entity.mcp_sandbox

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class McpSandboxGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
)