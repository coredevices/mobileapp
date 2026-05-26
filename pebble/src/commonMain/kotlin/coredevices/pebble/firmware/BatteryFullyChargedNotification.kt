package coredevices.pebble.firmware

import io.rebble.libpebblecommon.connection.AppContext

expect fun postWatchFullyChargedNotification(appContext: AppContext, watchName: String)
