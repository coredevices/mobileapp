package io.rebble.libpebblecommon.connection

import io.rebble.libpebblecommon.services.appmessage.AppMessageData
import kotlinx.coroutines.flow.Flow

interface CompanionApp {
    suspend fun start(incomingAppMessages: Flow<AppMessageData>)
    suspend fun stop()
}
