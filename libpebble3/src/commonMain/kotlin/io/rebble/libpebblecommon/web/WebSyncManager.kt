package io.rebble.libpebblecommon.web

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.RequestSync
import io.rebble.libpebblecommon.connection.WebServices
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.locker.Locker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

/**
 * This will manage scheduling background tasks etc. For now, it just triggers stuff to happen
 * immediately (in a way that might be killed if we're not in the foreground etc).
 */
class WebSyncManager(
    private val webServices: WebServices,
    private val locker: Locker,
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
) : RequestSync {
    private val logger = Logger.withTag("WebSyncManager")

    override fun requestLockerSync(): Deferred<Unit> {
        logger.d { "requestLockerSync()" }
        val result = CompletableDeferred<Unit>()
        libPebbleCoroutineScope.launch {
            // TODO probably don't want the logic in this class
            val response = webServices.fetchLocker()
            if (response == null) {
                logger.i("locker response is null")
                result.complete(Unit)
                return@launch
            }
            locker.update(response)
            result.complete(Unit)
            locker.prefetchStaleAppUpdates()
        }
        return result
    }
}
