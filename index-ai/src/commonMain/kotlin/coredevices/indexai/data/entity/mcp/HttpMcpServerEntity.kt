package coredevices.indexai.data.entity.mcp

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class HttpMcpServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val cachedTitle: String,
    val name: String,
    val url: String,
    val streamable: Boolean,
    val authHeader: String?,
    val includedPrompts: List<String>,
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
)
