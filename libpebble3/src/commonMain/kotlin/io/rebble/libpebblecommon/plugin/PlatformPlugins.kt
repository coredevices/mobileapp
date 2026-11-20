package io.rebble.libpebblecommon.plugin

/**
 * Built-in plugins only one platform can offer. Bound by each platform module, so a plugin that
 * has no meaning on a platform is absent there rather than present and permanently empty.
 */
internal class PlatformPlugins(val plugins: Set<NativePlugin>)
