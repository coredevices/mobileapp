package coredevices.ring

import coredevices.ring.agent.BuiltinServletRepository
import coredevices.ring.agent.McpSessionFactory
import coredevices.ring.agent.builtin_servlets.notes.CreateNoteTool
import coredevices.ring.agent.builtin_servlets.notes.LocalNoteClient
import coredevices.ring.agent.builtin_servlets.notes.NoteIntegrationFactory
import coredevices.ring.agent.integrations.NotionIntegration
import coredevices.ring.agent.integrations.obsidian.ObsidianIntegration
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val mcpModule = module {
    singleOf(::BuiltinServletRepository)
    singleOf(::McpSessionFactory)
    factoryOf(::CreateNoteTool)
    factoryOf(::NotionIntegration)
    // Explicit factory (not factoryOf) so ObsidianIntegration's clock/timeZone
    // constructor defaults are used — Koin's factoryOf would try to resolve every
    // parameter from the graph and fail on kotlinx.datetime.TimeZone.
    factory { ObsidianIntegration(get(), get()) }
    factoryOf(::LocalNoteClient)
    singleOf(::NoteIntegrationFactory)
}

expect fun isBeeperAvailable(): Boolean