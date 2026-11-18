package coredevices.ring.agent.pebble_plugin

import co.touchlab.kermit.Logger
import coredevices.ring.agent.BuiltinServletRepository
import coredevices.ring.agent.builtin_servlets.clock.CLOCK_PLUGIN_UUID
import coredevices.ring.agent.builtin_servlets.clock.ClockServlet
import coredevices.ring.agent.builtin_servlets.messaging.MESSAGING_PLUGIN_UUID
import coredevices.ring.agent.builtin_servlets.messaging.MessagingServlet
import coredevices.ring.agent.builtin_servlets.notes.NoteServlet
import coredevices.ring.agent.builtin_servlets.reminders.ReminderServlet
import io.rebble.libpebblecommon.connection.Plugins
import io.rebble.libpebblecommon.plugin.NativePlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.uuid.Uuid

/** Registers the servlets that are also plugins while Index is enabled. */
class IndexServletPlugins(
    private val plugins: Plugins,
    private val indexEnabled: Flow<Boolean>,
    private val servletPlugins: () -> List<NativePlugin>,
) {
    fun start(scope: CoroutineScope) {
        indexEnabled.distinctUntilChanged().onEach { enabled ->
            if (enabled) {
                val built = runCatching { servletPlugins() }
                    .onFailure { logger.e(it) { "Failed to build servlet plugins" } }
                    .getOrDefault(emptyList())
                built.forEach(plugins::registerPlugin)
                logger.i { "Registered ${built.size} servlet plugins: ${built.map { it.metadata.name }}" }
            } else {
                UUIDS.forEach(plugins::unregisterPlugin)
                logger.i { "Unregistered servlet plugins" }
            }
        }.launchIn(scope)
    }

    companion object {
        private val logger = Logger.withTag("IndexServletPlugins")

        /** Servlet ids of every [BuiltInServletPlugin]; resolving them through the repository keeps the platform gating. */
        val SERVLETS: List<String> = listOf(
            ClockServlet.name,
            MessagingServlet.name,
            NoteServlet.NAME,
            ReminderServlet.NAME,
        )

        val UUIDS: Set<Uuid> = setOf(
            CLOCK_PLUGIN_UUID,
            MESSAGING_PLUGIN_UUID,
            NoteServlet.PLUGIN_UUID,
            ReminderServlet.PLUGIN_UUID,
        )
    }
}

/** The servlets this device can offer, as plugins. */
fun BuiltinServletRepository.indexServletPlugins(): List<NativePlugin> =
    IndexServletPlugins.SERVLETS.mapNotNull { resolveName(it) as? BuiltInServletPlugin }
