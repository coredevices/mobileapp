package coredevices.ring.agent

import co.touchlab.kermit.Logger
import coredevices.ring.service.indexfeed.RecordingSessionContext
import kotlinx.coroutines.currentCoroutineContext

suspend inline fun currentSessionContext(): RecordingSessionContext? {
    return currentCoroutineContext()[RecordingSessionContext] ?: run {
        Logger.withTag("currentSessionContext")
            .e { "No RecordingSessionContext found in the current coroutine context!" }
        return@run null
    }
}