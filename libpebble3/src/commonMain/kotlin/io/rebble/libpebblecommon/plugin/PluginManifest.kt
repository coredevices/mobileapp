package io.rebble.libpebblecommon.plugin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Declarative description of a plugin — what sources it serves and what actions it exposes.
 * For JS plugins this is parsed from the plugin's `manifest.json`; built-in Kotlin plugins
 * declare the same types directly. See `new-plugin-api.md`.
 */
@Serializable
data class PluginManifest(
    val uuid: String,
    val name: String,
    val description: String = "",
    val script: String = "plugin.js",
    /**
     * Settings page. Either an http(s) URL or, for a page shipped alongside the script, a
     * bundled filename.
     */
    val configPage: String? = null,
    /**
     * What the user has to let this plugin do. Declared once for the whole plugin rather than
     * per source: a plugin reaches its API the same way whichever of its sources is being read,
     * and the network permissions are enforced at the plugin's one way out.
     */
    val usesPermissions: List<PluginPermission> = emptyList(),
    val sources: List<SourceDeclaration> = emptyList(),
    val actions: List<ActionDeclaration> = emptyList(),
)

@Serializable
data class SourceDeclaration(
    val category: String,
    /** Kinds of thing this block describes. Items listed together share everything below. */
    val items: List<String>,
    /** Property name -> the shapes it can be rendered as, in the plugin's preferred order. */
    val properties: Map<String, List<String>> = emptyMap(),
    val supportsMultiple: Boolean = false,
    /** What a caller has to have been granted before it may read this source. */
    val callerPermissions: List<PluginPermission> = emptyList(),
    val suggestedRefreshIntervalSec: Int = DEFAULT_REFRESH_SEC,
) {
    fun serves(category: String, item: String) = category == this.category && item in items

    companion object {
        const val DEFAULT_REFRESH_SEC = 300
    }
}

@Serializable
data class ActionDeclaration(
    val name: String,
    val description: String = "",
    /** JSON Schema for the action's arguments. Passed through verbatim to the MCP tool definition. */
    val parameters: JsonObject? = null,
    /**
     * `"<category>/<item>"`, optionally `"<category>/<item>/<property>"`, of sources in the same
     * plugin whose `instanceId` this action takes. Naming the property is what lets a tile
     * showing one reading of a thing find the action that writes that reading.
     */
    val targets: List<String> = emptyList(),
    val destructive: Boolean = false,
    val requiresConfirmation: Boolean = false,
    /** What a caller has to have been granted before it may invoke this action. */
    val callerPermissions: List<PluginPermission> = emptyList(),
) {
    /** Names listed in the schema's `required` array. */
    val requiredParams: List<String>
        get() = parameters?.get("required")?.jsonArray
            ?.map { it.jsonPrimitive.content }
            ?: emptyList()

    /**
     * Whether a watch can fire this action with no keyboard: every required parameter is one the
     * host can fill from the tile the user tapped — which instance it is showing, and which kind
     * of thing that is when the action targets several.
     */
    val bindable: Boolean
        get() = requiredParams.all { it == PARAM_INSTANCE_ID || it == PARAM_ITEM }

    companion object {
        const val PARAM_INSTANCE_ID = "instanceId"
        const val PARAM_ITEM = "item"
    }
}

@Serializable
data class ActionResult(
    val ok: Boolean,
    /** Shown as a toast on the watch; returned verbatim to an LLM caller. */
    val text: String? = null,
    /** `"<category>/<item>"` entries whose cached envelopes this action invalidated. */
    val refreshed: List<String> = emptyList(),
    val code: String? = null,
    val message: String? = null,
) {
    companion object {
        fun error(code: String, message: String? = null) =
            ActionResult(ok = false, code = code, message = message)
    }
}

object PluginErrors {
    const val PLUGIN_UNAVAILABLE = "PLUGIN_UNAVAILABLE"
    const val PERMISSION_DENIED = "PERMISSION_DENIED"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val TIMEOUT = "TIMEOUT"
    const val AUTH_REQUIRED = "AUTH_REQUIRED"
    const val INVALID_ARGS = "INVALID_ARGS"
    const val INVALID_REQUEST = "INVALID_REQUEST"
    const val UNKNOWN = "UNKNOWN"
}
