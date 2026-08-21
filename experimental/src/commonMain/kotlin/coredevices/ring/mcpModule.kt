package coredevices.ring

import coredevices.ring.agent.BuiltinServletRepository
import coredevices.ring.agent.IndexActionsRepository
import coredevices.ring.agent.McpSessionFactory
import coredevices.ring.agent.builtin_servlets.calendar.phoneCalendarConnected
import coredevices.ring.agent.builtin_servlets.messaging.beeperUnavailableReason
import coredevices.ring.agent.builtin_servlets.notes.CreateNoteTool
import coredevices.ring.agent.builtin_servlets.notes.LocalNoteClient
import coredevices.ring.agent.builtin_servlets.notes.NoteIntegrationFactory
import coredevices.ring.database.Preferences
import coredevices.ring.database.room.repository.McpSandboxRepository
import coredevices.ring.agent.integrations.NotionIntegration
import coredevices.ring.agent.integrations.obsidian.ObsidianIntegration
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val mcpModule = module {
    singleOf(::BuiltinServletRepository)
    singleOf(::McpSessionFactory)
    single {
        IndexActionsRepository(
            servletRepository = get(),
            defaultGroupEntries = get<McpSandboxRepository>()::defaultGroupEntriesFlow,
            setEnabledInDefaultGroup = get<McpSandboxRepository>()::setBuiltinEnabledInDefaultGroup,
            llmMode = get<Preferences>().llmMode,
            calendarConnected = phoneCalendarConnected(get(), get()),
            beeperUnavailable = beeperUnavailableReason(get()),
        )
    }
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