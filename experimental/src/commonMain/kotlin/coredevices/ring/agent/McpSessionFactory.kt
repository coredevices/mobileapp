package coredevices.ring.agent

import coredevices.indexai.data.entity.mcp.HttpMcpServerEntity
import coredevices.mcp.client.HttpMcpIntegration
import coredevices.mcp.client.HttpMcpProtocol
import coredevices.mcp.client.LazyLoadingMcpSession
import coredevices.mcp.client.McpIntegration
import coredevices.mcp.client.McpServerCache
import coredevices.mcp.client.McpSession
import coredevices.ring.agent.builtin_servlets.plugins.PluginsServlet
import coredevices.ring.agent.pebble_plugin.PblPluginMcpIntegration
import coredevices.ring.agent.pebble_plugin.PluginGrant
import coredevices.ring.database.room.repository.McpServerRepository
import coredevices.ring.database.room.repository.McpServerEntry
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.rebble.libpebblecommon.connection.Plugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

private val implementation = Implementation(
    name = "CoreApp",
    version = "0.0.1"
)

class McpSessionFactory(
    private val mcpServerRepository: McpServerRepository,
    private val builtinServletRepository: BuiltinServletRepository,
    private val mcpServerCache: McpServerCache,
    private val plugins: Plugins,
    private val indexActions: IndexActionsRepository,
) {
    private suspend fun pluginsWith(grant: PluginGrant) = indexActions.grantedPlugins(grant).first()

    /** One integration per plugin granting actions, plus the sources servlet if any plugin grants those. */
    private suspend fun getPluginServers(): List<McpIntegration> {
        val actions = pluginsWith(PluginGrant.Actions).map { PblPluginMcpIntegration(it, plugins) }
        val sources = PluginsServlet({ pluginsWith(PluginGrant.Sources) }, plugins)
            .takeIf { pluginsWith(PluginGrant.Sources).isNotEmpty() }
        return actions + listOfNotNull(sources)
    }

    /**
     * Serves every enabled server, built-ins and HTTP alike. Cloud agents get lazy tool loading;
     * built-in servlets are always loaded.
     */
    suspend fun create(scope: CoroutineScope, lazyToolLoading: Boolean): McpSession {
        val entries = mcpServerRepository.enabledServerEntriesFlow().first()
        val integrations = entries.mapNotNull {
            when (it) {
                is McpServerEntry.BuiltinMcpEntry -> builtinServletRepository.resolveName(it.builtinMcpName)
                is McpServerEntry.HttpServerEntry -> it.server.toMcpIntegration(mcpServerCache)
            }
        } + getPluginServers()
        if (!lazyToolLoading) return McpSession(integrations, scope)
        val builtinNames = entries.filterIsInstance<McpServerEntry.BuiltinMcpEntry>().map { it.builtinMcpName }.toSet()
        return LazyLoadingMcpSession(integrations, scope, eagerIntegrations = builtinNames + PluginsServlet.NAME)
    }
}

private fun HttpMcpServerEntity.toMcpIntegration(cache: McpServerCache): HttpMcpIntegration {
    return HttpMcpIntegration(
        name = this.name,
        implementation = implementation,
        url = this.url,
        protocol = if (this.streamable) HttpMcpProtocol.Streaming else HttpMcpProtocol.Sse,
        authHeader = this.authHeader,
        cache = cache,
        description = this.description?.takeIf { it.isNotBlank() },
    )
}
