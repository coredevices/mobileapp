package coredevices.ring

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.ring.database.firestore.dao.FirestoreRecordingsDao
import coredevices.util.CoreConfigHolder
import coredevices.util.Permission
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

private const val LAST_UID_CHECKED_KEY = "ring_index_enable_last_uid_checked"

internal fun listenForUserPresent(recordingsDao: FirestoreRecordingsDao, configHolder: CoreConfigHolder, settings: Settings) {
    flow {
        emit(Firebase.auth.currentUser)
        emitAll(Firebase.auth.authStateChanged)
    }
        .distinctUntilChanged { old, new ->
            old?.uid == new?.uid
        }.onEach {
            if (it != null && it.uid != settings.getStringOrNull(LAST_UID_CHECKED_KEY)) {
                settings.putString(LAST_UID_CHECKED_KEY, it.uid)
                try {
                    if (recordingsDao.hasAnyRecordings()) {
                        Logger.d { "User signed in and has recordings, Index will be enabled" }
                        configHolder.update(configHolder.config.value.copy(enableIndex = true))
                    }
                } catch (e: Exception) {
                    Logger.w(e) { "Failed to check recordings for user, Index will not be enabled" }
                }
            }
        }.launchIn(GlobalScope)
}

expect class RingDelegate {
    suspend fun init()
    fun requiredRuntimePermissions(): Set<Permission>
    /** Should be called whenever an iOS background sync is triggered. */
    fun onBackgroundSync()
    /** Restarts the haversine pre-emptive transfer loop. iOS only; no-op on Android. */
    fun restartPreemptiveTransfer()
}