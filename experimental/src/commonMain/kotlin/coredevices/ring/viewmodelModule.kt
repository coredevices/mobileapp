package coredevices.ring

import coredevices.ring.external.indexwebhook.IndexWebhookSettingsViewModel
import coredevices.ring.ui.components.recording.RecordingTraceTimelineViewModel
import coredevices.ring.ui.viewmodel.AllAnswersViewModel
import coredevices.ring.ui.viewmodel.AllListsViewModel
import coredevices.ring.ui.viewmodel.BackupViewModel
import coredevices.ring.ui.viewmodel.EncryptionViewModel
import coredevices.ring.ui.viewmodel.FullFeedViewModel
import coredevices.ring.ui.viewmodel.IndexFeedViewModel
import coredevices.ring.ui.viewmodel.ListenDialogViewModel
import coredevices.ring.ui.viewmodel.ObjectDetailViewModel
import coredevices.ring.ui.viewmodel.RecordingDetailsViewModel
import coredevices.ring.ui.viewmodel.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

internal val viewmodelModule = module {
    viewModelOf(::FullFeedViewModel)
    viewModelOf(::IndexFeedViewModel)
    viewModelOf(::AllListsViewModel)
    viewModelOf(::AllAnswersViewModel)
    viewModelOf(::ObjectDetailViewModel)
    viewModelOf(::RecordingDetailsViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::BackupViewModel)
    viewModelOf(::EncryptionViewModel)
    viewModelOf(::ListenDialogViewModel)
    viewModelOf(::IndexWebhookSettingsViewModel)
    viewModelOf(::RecordingTraceTimelineViewModel)
}