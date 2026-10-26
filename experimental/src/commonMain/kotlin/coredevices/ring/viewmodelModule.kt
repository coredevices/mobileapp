package coredevices.ring

import coredevices.ring.agent.integrations.providers.pluginsEnabled
import coredevices.ring.external.indexwebhook.IndexWebhookSettingsViewModel
import coredevices.ring.ui.components.recording.RecordingTraceTimelineViewModel
import coredevices.ring.ui.viewmodel.AllAnswersViewModel
import coredevices.ring.ui.viewmodel.AllListsViewModel
import coredevices.ring.ui.viewmodel.BackupViewModel
import coredevices.ring.ui.viewmodel.EncryptionViewModel
import coredevices.ring.ui.viewmodel.FullFeedViewModel
import coredevices.ring.ui.viewmodel.IndexActionsViewModel
import coredevices.ring.ui.viewmodel.IndexFeedViewModel
import coredevices.ring.ui.viewmodel.ListenDialogViewModel
import coredevices.ring.ui.viewmodel.ObjectDetailViewModel
import coredevices.ring.ui.viewmodel.RecordingDetailsViewModel
import coredevices.ring.ui.viewmodel.RingButtonViewModel
import coredevices.ring.ui.viewmodel.SettingsViewModel
import io.rebble.libpebblecommon.connection.LibPebble
import org.koin.core.module.dsl.viewModel
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
    viewModel {
        val libPebble = get<LibPebble>()
        IndexActionsViewModel(
            indexActionsRepository = get(),
            mcpServerRepository = get(),
            pluginStore = get(),
            providers = get(),
            preferences = get(),
            gestureRouting = get(),
            platform = get(),
            pluginsEnabled = libPebble.pluginsEnabled(),
            lockerUuids = libPebble.getAllLockerUuids(),
        )
    }
    viewModel {
        RingButtonViewModel(
            gestureRouting = get(),
            webhookPreferences = get(),
            cactusModelProvider = get(),
            appScope = get(),
            platform = get(),
            providers = get(),
            pluginsEnabled = get<LibPebble>().pluginsEnabled(),
        )
    }
    viewModelOf(::BackupViewModel)
    viewModelOf(::EncryptionViewModel)
    viewModelOf(::ListenDialogViewModel)
    viewModelOf(::IndexWebhookSettingsViewModel)
    viewModelOf(::RecordingTraceTimelineViewModel)
}