package coredevices.indexai.agent

import coredevices.indexai.data.McpServerDefinition
import coredevices.mcp.client.McpIntegration

interface ServletRepository {
    fun getAllServlets(): List<McpServerDefinition>
    fun resolveName(name: String): McpIntegration?
}