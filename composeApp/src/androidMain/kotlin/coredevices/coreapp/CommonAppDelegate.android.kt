package coredevices.coreapp

import coredevices.util.CoreConfig
import io.rebble.libpebblecommon.connection.AppContext

actual fun rescheduleBgRefreshTask(appContext: AppContext, coreConfig: CoreConfig) {
    scheduleBackgroundJob(appContext, coreConfig)
}