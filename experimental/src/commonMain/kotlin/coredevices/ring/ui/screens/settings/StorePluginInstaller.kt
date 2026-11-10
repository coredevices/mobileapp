package coredevices.ring.ui.screens.settings

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import coredevices.ring.agent.pebble_plugin.PluginStore
import coredevices.ring.agent.pebble_plugin.StorePlugin
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ui.M3Dialog
import io.rebble.libpebblecommon.permissions.InstallReview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

/**
 * Installs a store plugin picked from a destination sheet: the permission review first, then
 * the plugin's settings page once it is in the locker, as connecting an installed plugin would.
 * Hosts must render [Dialogs] or the review never shows.
 */
internal class StorePluginInstaller(
    private val scope: CoroutineScope,
    private val reviewInstall: suspend (StorePlugin) -> InstallReview?,
    private val installPlugin: suspend (StorePlugin, granted: Map<String, Boolean?>) -> Boolean,
    private val onConfigurePlugin: (uuid: Uuid, title: String) -> Unit,
) {
    private var review by mutableStateOf<Pair<StorePlugin, InstallReview>?>(null)
    private var failed by mutableStateOf<StorePlugin?>(null)

    fun install(plugin: StorePlugin) {
        scope.launch {
            val pending = reviewInstall(plugin)
            if (pending == null) installGranted(plugin, emptyMap()) else review = plugin to pending
        }
    }

    private fun installGranted(plugin: StorePlugin, granted: Map<String, Boolean?>) {
        scope.launch {
            if (!installPlugin(plugin, granted)) {
                failed = plugin
            } else if (plugin.configurable) {
                onConfigurePlugin(plugin.pluginUuid, plugin.title)
            }
        }
    }

    @Composable
    fun Dialogs() {
        review?.let { (plugin, pending) ->
            koinInject<PluginStore>().ReviewInstallDialog(pending) { granted ->
                review = null
                if (granted != null) installGranted(plugin, granted)
            }
        }
        failed?.let { plugin ->
            M3Dialog(
                onDismissRequest = { failed = null },
                title = { Text("Couldn't add ${plugin.title}") },
                buttons = { TextButton(onClick = { failed = null }) { Text("OK") } },
            ) {
                Text(
                    "Check your connection and try again.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = IndexTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun rememberStorePluginInstaller(
    reviewInstall: suspend (StorePlugin) -> InstallReview?,
    installPlugin: suspend (StorePlugin, granted: Map<String, Boolean?>) -> Boolean,
    onConfigurePlugin: (uuid: Uuid, title: String) -> Unit,
): StorePluginInstaller {
    val scope = rememberCoroutineScope()
    return remember(scope) { StorePluginInstaller(scope, reviewInstall, installPlugin, onConfigurePlugin) }
}
