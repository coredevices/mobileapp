package io.rebble.libpebblecommon.connection

import io.rebble.libpebblecommon.services.appmessage.AppMessageData
import kotlinx.coroutines.flow.Flow

public interface CompanionApp {
    public suspend fun start(incomingAppMessages: Flow<AppMessageData>)
    public suspend fun stop()
}
