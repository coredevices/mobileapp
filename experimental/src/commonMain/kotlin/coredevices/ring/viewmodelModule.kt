package coredevices.ring

import coredevices.ring.external.vermillion.VermillionSettingsViewModel
import coredevices.ring.ui.screens.settings.McpSandboxSettingsViewModel
import coredevices.ring.ui.viewmodel.FeedViewModel
import coredevices.ring.ui.viewmodel.ListenDialogViewModel
import coredevices.ring.ui.viewmodel.NotesViewModel
import coredevices.ring.ui.viewmodel.RecordingDetailsViewModel
import coredevices.ring.ui.viewmodel.ReminderDetailsViewModel
import coredevices.ring.ui.viewmodel.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

internal val viewmodelModule = module {
    viewModelOf(::FeedViewModel)
    viewModelOf(::RecordingDetailsViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::ListenDialogViewModel)
    viewModelOf(::NotesViewModel)
    viewModelOf(::ReminderDetailsViewModel)
    viewModelOf(::VermillionSettingsViewModel)
    viewModelOf(::McpSandboxSettingsViewModel)
}