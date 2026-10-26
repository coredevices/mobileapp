package io.rebble.libpebblecommon.plugin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject

/**
 * A built-in plugin the app ships (a phone-side data source like Music or Weather), shown in the
 * Plugins tab's "Built-in Plugins" section. It's the root of its own data, so never prompts.
 */
interface NativePlugin : Plugin {
    fun observe(
        category: String,
        item: String,
        properties: List<String>? = null,
        iconPixelSize: IconPixelSize? = null,
    ): Flow<SourceEnvelope>

    suspend fun invoke(action: String, args: JsonObject): ActionResult =
        ActionResult.error(PluginErrors.PLUGIN_UNAVAILABLE, "$name exposes no actions")

    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
        prompt: Boolean,
    ): Flow<SourceUpdate> = observe(category, item, properties, iconPixelSize).map { SourceUpdate.Data(it) }

    override suspend fun invoke(action: String, args: JsonObject, prompt: Boolean): ActionResult =
        invoke(action, args)
}
