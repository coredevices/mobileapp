package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Built-in find-my-phone plugin, reachable two ways:
 *  - As a source: subscribing to `phone_state/find_my_phone` starts ringing and the subscription
 *    ending (watchapp closed, unsubscribe, disconnect) stops it. This suits a watchapp, which has
 *    no reliable "I'm closing" hook to stop with.
 *  - As `ring`/`stop` actions: one-shot callers (the Index agent) that don't hold a subscription.
 *
 * Either way the ring stops itself after [RING_TIMEOUT] if nothing stops it first.
 */
class FindMyPhonePlugin(
    private val soundPlayer: PhoneSoundPlayer,
    private val scope: LibPebbleCoroutineScope,
) : NativePlugin {
    override val pluginUuid: Uuid = BUILT_IN_FIND_MY_PHONE_UUID
    override val name: String = "Find My Phone"
    override val description: String = "Ring the phone from the watch to find it."

    private var timeoutJob: Job? = null

    override val sources: List<SourceDeclaration> = listOf(
        SourceDeclaration(
            category = CATEGORY,
            items = listOf(ITEM_FIND_MY_PHONE),
            properties = mapOf(
                PROPERTY_RINGING to listOf(SourceShapeNames.BOOLEAN),
            ),
            supportsMultiple = false,
            callerPermissions = listOf(PluginPermission(PluginPermissions.FIND_MY_PHONE)),
        ),
    )

    override val actions: List<ActionDeclaration> = listOf(
        ActionDeclaration(
            name = ACTION_RING,
            description = "Ring the phone to help find it. Rings for up to 60 seconds, or until stopped.",
            callerPermissions = listOf(PluginPermission(PluginPermissions.FIND_MY_PHONE)),
        ),
        ActionDeclaration(
            name = ACTION_STOP,
            description = "Stop the phone ringing.",
            callerPermissions = listOf(PluginPermission(PluginPermissions.FIND_MY_PHONE)),
        ),
    )

    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
    ): Flow<SourceEnvelope> {
        if (!serves(category, item)) return emptyFlow()
        return channelFlow {
            ring()
            soundPlayer.isRinging.collect { ringing -> send(toEnvelope(ringing)) }
        }.onCompletion { stop() }
    }

    override suspend fun invoke(action: String, args: JsonObject): ActionResult = when (action) {
        ACTION_RING -> {
            ring()
            ActionResult(ok = true, text = "Ringing your phone.")
        }
        ACTION_STOP -> {
            stop()
            ActionResult(ok = true, text = "Stopped ringing your phone.")
        }
        else -> ActionResult.error(PluginErrors.PLUGIN_UNAVAILABLE, "no action $action")
    }

    private fun ring() {
        soundPlayer.startRinging()
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(RING_TIMEOUT)
            stop()
        }
    }

    private fun stop() {
        timeoutJob?.cancel()
        timeoutJob = null
        soundPlayer.stopRinging()
    }

    private fun toEnvelope(ringing: Boolean): SourceEnvelope = SourceEnvelope(
        pluginUuid = pluginUuid.toString(),
        validUntilMs = null,
        instances = listOf(
            SourceInstance(
                instanceId = INSTANCE_ID,
                properties = mapOf(
                    PROPERTY_RINGING to mapOf(
                        SourceShapeNames.BOOLEAN to encode(BooleanShape(ringing)),
                    ),
                ),
            ),
        ),
    )

    private inline fun <reified T> encode(value: T): JsonElement =
        Json.encodeToJsonElement(value)

    companion object {
        const val CATEGORY = "phone_state"
        const val ITEM_FIND_MY_PHONE = "find_my_phone"
        const val PROPERTY_RINGING = "ringing"
        const val ACTION_RING = "ring"
        const val ACTION_STOP = "stop"
        private const val INSTANCE_ID = "phone"
        private val RING_TIMEOUT = 60.seconds

        val BUILT_IN_FIND_MY_PHONE_UUID: Uuid =
            Uuid.parse("00000000-0000-0000-0001-000000000008")
    }
}
