package coredevices.ring

import coredevices.HackyPermissionRequesterProvider
import coredevices.mcp.client.McpServerCache
import coredevices.ring.agent.BuiltinServletRepository
import coredevices.ring.agent.IndexActionsRepository
import coredevices.ring.agent.McpSessionFactory
import coredevices.ring.agent.builtin_servlets.calendar.phoneCalendarConnected
import coredevices.ring.agent.builtin_servlets.messaging.beeperUnavailableReason
import coredevices.ring.agent.builtin_servlets.notes.CreateNoteTool
import coredevices.ring.agent.builtin_servlets.notes.LocalNoteClient
import coredevices.ring.database.Preferences
import coredevices.ring.database.room.repository.McpServerRepository
import coredevices.ring.database.room.repository.PluginGrantRepository
import coredevices.ring.service.button.GestureRoutingPreferences
import coredevices.ring.service.button.routesOnlyToLocalAgent
import coredevices.ring.agent.integrations.NotionIntegration
import coredevices.ring.agent.integrations.obsidian.ObsidianIntegration
import io.rebble.libpebblecommon.connection.KnownPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.Plugins
import io.rebble.libpebblecommon.connection.Watches
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val mcpModule = module {
    singleOf(::BuiltinServletRepository)
    single { McpServerCache() }
    single {
        McpSessionFactory(
            mcpServerRepository = get(),
            builtinServletRepository = get(),
            mcpServerCache = get(),
            plugins = get(),
            indexActions = get(),
        )
    }
    single {
        // PermissionRequester resolves on first collect, not here: on iOS it depends back on this
        // repository (RequiredPermissions -> RingDelegate -> BackgroundRingService -> ... ->
        // McpSessionFactory), and resolving it eagerly recursed until the app crashed at launch.
        val permissions = get<HackyPermissionRequesterProvider>()
        val preferences = get<Preferences>()
        IndexActionsRepository(
            servletRepository = get(),
            disabledBuiltins = get<McpServerRepository>()::disabledBuiltinsFlow,
            setBuiltinEnabled = get<McpServerRepository>()::setBuiltinEnabled,
            localAgentOnly = get<GestureRoutingPreferences>().routes.map { it.routesOnlyToLocalAgent() },
            calendarConnected = flow { emitAll(phoneCalendarConnected(preferences, permissions.get())) },
            beeperUnavailable = flow { emitAll(beeperUnavailableReason(permissions.get())) },
            installedPlugins = get<Plugins>()::getAllPlugins,
            disabledPlugins = get<PluginGrantRepository>()::disabledPlugins,
            persistPluginGrantEnabled = get<PluginGrantRepository>()::setPluginGrantEnabled,
            watchPaired = get<LibPebble>().watches.map { devices -> devices.any { it is KnownPebbleDevice } },
        )
    }
    factoryOf(::CreateNoteTool)
    factoryOf(::NotionIntegration)
    // Explicit factory (not factoryOf) so ObsidianIntegration's clock/timeZone
    // constructor defaults are used — Koin's factoryOf would try to resolve every
    // parameter from the graph and fail on kotlinx.datetime.TimeZone.
    factory { ObsidianIntegration(get(), get()) }
    factoryOf(::LocalNoteClient)
}

expect fun isBeeperAvailable(): Boolean