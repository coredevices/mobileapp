package coredevices.ring.agent.pebble_plugin

import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.ActionResult
import io.rebble.libpebblecommon.plugin.IconPixelSize
import io.rebble.libpebblecommon.plugin.NativePlugin
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import io.rebble.libpebblecommon.plugin.SourceEnvelope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

/**
 * A plugin that records every action invocation and answers with [result]. A declared source serves
 * its `category/item` entry in [envelopes], else [envelope].
 */
internal class FakePlugin(
    override val actions: List<ActionDeclaration> = emptyList(),
    override val name: String = "Music",
    override val description: String = "",
    override val pluginUuid: Uuid = Uuid.parse("00000000-0000-0000-0002-00000000000b"),
    private val result: ActionResult = ActionResult(ok = true, text = "done"),
    override val sources: List<SourceDeclaration> = emptyList(),
    private val envelope: SourceEnvelope? = null,
    private val envelopes: Map<String, SourceEnvelope> = emptyMap(),
) : NativePlugin {
    val invoked = mutableListOf<Pair<String, JsonObject>>()

    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
    ): Flow<SourceEnvelope> {
        if (!serves(category, item)) return emptyFlow()
        val served = envelopes["$category/$item"] ?: envelope ?: return emptyFlow()
        return flowOf(served)
    }

    override suspend fun invoke(action: String, args: JsonObject): ActionResult {
        invoked += action to args
        return result
    }
}
