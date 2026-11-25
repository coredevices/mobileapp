package coredevices.ring.ui.screens.settings

import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.settings_destination_hold_to_reconfigure
import coreapp.ring.generated.resources.settings_destination_not_connected
import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.ui.viewmodel.DestinationItem
import coredevices.ring.ui.viewmodel.isConnected
import org.jetbrains.compose.resources.StringResource

/** A connected destination with settings to reopen: its locker page, or [hasDialog] for a built-in. */
internal fun DestinationItem.isConfigurable(hasDialog: (IntegrationProvider) -> Boolean = { false }): Boolean =
    this is DestinationItem.Installed && connected && (hasLockerPage || hasDialog(provider))

internal fun DestinationItem.rowSubtitle(configurable: Boolean): StringResource? = when {
    !isConnected -> Res.string.settings_destination_not_connected
    configurable -> Res.string.settings_destination_hold_to_reconfigure
    else -> null
}
