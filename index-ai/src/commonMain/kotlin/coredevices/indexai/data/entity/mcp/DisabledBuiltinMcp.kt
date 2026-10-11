package coredevices.indexai.data.entity.mcp

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A built-in MCP server the user has switched off. Absence means enabled, so a built-in added
 *  in a later version needs no rows. */
@Entity
data class DisabledBuiltinMcp(
    @PrimaryKey val builtinMcpName: String,
)
