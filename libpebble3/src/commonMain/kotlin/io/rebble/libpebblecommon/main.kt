package io.rebble.libpebblecommon

import io.rebble.libpebblecommon.packets.PhoneAppVersion

internal expect fun getPlatform(): PhoneAppVersion.OSType

internal expect fun performPlatformSpecificInit()
