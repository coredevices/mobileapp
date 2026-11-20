package io.rebble.libpebblecommon

import io.rebble.libpebblecommon.packets.PhoneAppVersion

internal actual fun getPlatform(): PhoneAppVersion.OSType = PhoneAppVersion.OSType.IOS

internal actual fun performPlatformSpecificInit() {}
