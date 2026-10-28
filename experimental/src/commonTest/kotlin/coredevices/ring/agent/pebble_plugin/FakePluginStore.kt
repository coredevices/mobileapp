package coredevices.ring.agent.pebble_plugin

import androidx.compose.runtime.Composable
import io.rebble.libpebblecommon.permissions.InstallReview
import kotlin.uuid.Uuid

/** A store listing [plugins]; [available] false makes every fetch fail like an unreachable store. */
internal class FakePluginStore(
    private val plugins: List<StorePlugin> = emptyList(),
    var available: Boolean = true,
) : PluginStore {
    var fetches = 0
    val installed = mutableListOf<Pair<Uuid, Map<String, Boolean?>>>()

    override suspend fun indexPlugins(): List<StorePlugin>? {
        fetches++
        return plugins.takeIf { available }
    }

    override suspend fun reviewInstall(pluginUuid: Uuid): InstallReview? = null

    override suspend fun install(pluginUuid: Uuid, granted: Map<String, Boolean?>): Boolean {
        installed += pluginUuid to granted
        return true
    }

    @Composable
    override fun ReviewInstallDialog(review: InstallReview, onAnswer: (Map<String, Boolean?>?) -> Unit) {
    }
}
