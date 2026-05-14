package coredevices.ring

import coredevices.ring.external.indexwebhook.IndexWebhookSettingsViewModel
import coredevices.ring.ui.components.recording.RecordingTraceTimeline
import coredevices.ring.ui.components.recording.RecordingTraceTimelineViewModel
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
    viewModelOf(::IndexWebhookSettingsViewModel)
    viewModelOf(::McpSandboxSettingsViewModel)
    viewModelOf(::RecordingTraceTimelineViewModel)
}