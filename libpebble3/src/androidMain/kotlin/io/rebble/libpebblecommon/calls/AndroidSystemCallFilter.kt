package io.rebble.libpebblecommon.calls

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidSystemCallFilter(context: Context) : SystemCallFilter {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    override suspend fun allowsCall(number: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationManager.matchesCallFilter(Uri.fromParts("tel", number, null))
            } else {
                notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
            }
        } catch (e: SecurityException) {
            logger.w(e) { "Can't check call filter" }
            true
        }
    }

    companion object {
        private val logger = Logger.withTag("AndroidSystemCallFilter")
    }
}
