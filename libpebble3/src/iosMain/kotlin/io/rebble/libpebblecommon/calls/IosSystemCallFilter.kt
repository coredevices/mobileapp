package io.rebble.libpebblecommon.calls

// iOS doesn't expose Focus state to apps.
internal class IosSystemCallFilter : SystemCallFilter {
    override suspend fun allowsCall(number: String): Boolean = true
}
