package coredevices.ring.agent.pebble_plugin

import androidx.compose.runtime.Composable
import coredevices.ring.agent.integrations.providers.IntegrationSupport
import io.rebble.libpebblecommon.permissions.InstallReview
import io.rebble.libpebblecommon.plugin.PluginMetadata
import kotlin.uuid.Uuid

/** A plugin the app store lists for Index, offered as a destination before it is installed. */
data class StorePlugin(
    val metadata: PluginMetadata,
    /** Whether the plugin ships a settings page to open once installed. */
    val configurable: Boolean,
) {
    val pluginUuid: Uuid get() = metadata.pluginUuid
    val title: String get() = metadata.name
    val supports: Set<IntegrationSupport> get() = PblPluginIntegrationProvider.destinationSupports(metadata)
}

/**
 * The app store's Index plugin collection and the locker's install flow. Implemented by the app,
 * which owns the store client; the ring module only sees plugins as [StorePlugin]s.
 */
interface PluginStore {
    /** Every plugin in the Index collection, installed or not. Null when the store can't be reached. */
    suspend fun indexPlugins(): List<StorePlugin>?

    /** What installing [pluginUuid] would ask the user, or null when there is nothing to ask. */
    suspend fun reviewInstall(pluginUuid: Uuid): InstallReview?

    /** Adds [pluginUuid] to the locker with the permission decisions from [ReviewInstallDialog]. */
    suspend fun install(pluginUuid: Uuid, granted: Map<String, Boolean?>): Boolean

    /** [onAnswer] gets null if the user cancelled. */
    @Composable
    fun ReviewInstallDialog(review: InstallReview, onAnswer: (Map<String, Boolean?>?) -> Unit)
}
