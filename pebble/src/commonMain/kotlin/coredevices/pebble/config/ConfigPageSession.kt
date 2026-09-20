package coredevices.pebble.config

import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.js.PKJSApp
import io.rebble.libpebblecommon.plugin.ConfigMessageTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** A pbw ships one watchapp and at most one plugin, so its page names which it means. */
object ConfigScriptIds {
    const val PKJS = "pkjs"
    const val PLUGIN = "plugin"
}

data class ConfigScript(
    val name: String,
    val target: ConfigMessageTarget,
)

/**
 * The scripts behind one open config page, and their lifecycle. A page is identified only by the
 * uuid it configures; whether that resolves to a running watchapp, a plugin, or both is this
 * class's problem rather than the screen's.
 */
class ConfigPageSession(
    val scripts: Map<String, ConfigScript>,
    private val pkjs: PKJSApp?,
) {
    /**
     * Nothing behind this page any more: no plugin, and no running watchapp. A watchapp with no
     * plugin has no scripts to message and is still perfectly configurable, so this is not the
     * same as [scripts] being empty.
     */
    val isOrphaned: Boolean get() = scripts.isEmpty() && pkjs == null

    /** Everything the scripts push at the page, tagged with which one sent it. */
    val messages: Flow<Pair<String, String>> =
        scripts.map { (id, script) -> script.target.configMessages.map { id to it } }.merge()

    /** Bring up anything that only runs while its page is open, reporting each as it lands. */
    suspend fun open(onReady: (String) -> Unit) = scripts.forEach { (id, script) ->
        script.target.openConfigSession()
        onReady(id)
    }

    suspend fun close() = scripts.values.forEach { it.target.closeConfigSession() }

    /** Whatever the page put in its close URL. */
    fun onPageClosed(data: String) {
        pkjs?.triggerOnWebviewClosed(data)
    }
}

/** Resolves a uuid to the scripts that can configure it, and keeps up as watchapps come and go. */
class ConfigPageSessions(private val libPebble: LibPebble) {

    /**
     * Config messaging is part of the plugin API, so it is off with the rest of it — and a page
     * that can reach nothing is given no `Pebble` object at all.
     */
    val enabled: Boolean get() = libPebble.config.value.watchConfig.enablePlugins

    @OptIn(ExperimentalCoroutinesApi::class)
    fun sessionsFor(uuid: String, title: String): Flow<ConfigPageSession> = libPebble.watches
        .map { watches -> watches.filterIsInstance<ConnectedPebbleDevice>() }
        .flatMapLatest { devices ->
            if (devices.isEmpty()) flowOf(null)
            else combine(devices.map { it.currentPKJSSession }) { sessions ->
                sessions.firstOrNull { it?.uuid?.toString() == uuid }
            }
        }
        .distinctUntilChanged()
        .map { pkjs ->
            ConfigPageSession(
                scripts = if (!enabled) emptyMap() else {
                    buildMap {
                        pkjs?.let { put(ConfigScriptIds.PKJS, ConfigScript(title, it)) }
                        libPebble.configMessageTarget(uuid)?.let {
                            put(ConfigScriptIds.PLUGIN, ConfigScript(title, it))
                        }
                    }
                },
                pkjs = pkjs,
            )
        }
}
