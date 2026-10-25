package io.rebble.libpebblecommon.plugin

import kotlin.uuid.Uuid

/**
 * Built-in plugins only one platform can offer. Bound by each platform module, so a plugin that
 * has no meaning on a platform is absent there rather than present and permanently empty.
 */
class PlatformPlugins(val plugins: Set<NativePlugin>) {
    /** Declared here so common code can name them; reserved built-in prefix 00000000-0000-0000-0001-*. */
    companion object {
        val BUILT_IN_NOTIFICATIONS_UUID: Uuid = Uuid.parse("00000000-0000-0000-0001-000000000005")
    }
}
