package coredevices.util

import io.rebble.libpebblecommon.connection.AppContext

expect fun AppContext.isDebugBuild(): Boolean
