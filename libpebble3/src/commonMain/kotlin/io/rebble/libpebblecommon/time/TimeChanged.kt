package io.rebble.libpebblecommon.time

import io.rebble.libpebblecommon.connection.AppContext

internal interface TimeChanged {
    fun registerForTimeChanges(onChanged: () -> Unit)
}

internal expect fun createTimeChanged(appContext: AppContext): TimeChanged