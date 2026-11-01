package coredevices.ring.ui.screens.settings

import coredevices.ring.agent.integrations.providers.IntegrationProvider
import coredevices.ring.ui.viewmodel.DestinationItem
import coredevices.ring.ui.viewmodel.isConnected

/** A connected destination with settings to reopen: its locker page, or [hasDialog] for a built-in. */
internal fun DestinationItem.isConfigurable(hasDialog: (IntegrationProvider) -> Boolean = { false }): Boolean =
    this is DestinationItem.Installed && connected && (hasLockerPage || hasDialog(provider))

internal fun DestinationItem.rowSubtitle(configurable: Boolean): String? = when {
    !isConnected -> "Not connected"
    configurable -> "Hold to reconfigure"
    else -> null
}
