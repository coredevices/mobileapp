package coredevices.ring

import coredevices.ring.agent.BuiltinServletRepository
import coredevices.ring.agent.McpSessionFactory
import coredevices.ring.agent.builtin_servlets.notes.CreateNoteTool
import coredevices.ring.agent.builtin_servlets.notes.LocalNoteClient
import coredevices.ring.agent.builtin_servlets.notes.NoteIntegrationFactory
import coredevices.ring.agent.integrations.NotionIntegration
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val mcpModule = module {
    singleOf(::BuiltinServletRepository)
    singleOf(::McpSessionFactory)
    factoryOf(::CreateNoteTool)
    factoryOf(::NotionIntegration)
    factoryOf(::LocalNoteClient)
    singleOf(::NoteIntegrationFactory)
}

expect fun isBeeperAvailable(): Boolean