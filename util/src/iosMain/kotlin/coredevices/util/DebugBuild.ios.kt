package coredevices.util

import io.rebble.libpebblecommon.connection.AppContext
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform as NativePlatform

@OptIn(ExperimentalNativeApi::class)
actual fun AppContext.isDebugBuild(): Boolean = NativePlatform.isDebugBinary
