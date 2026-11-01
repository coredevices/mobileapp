package coredevices.util

import android.content.pm.ApplicationInfo
import io.rebble.libpebblecommon.connection.AppContext

actual fun AppContext.isDebugBuild(): Boolean = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
