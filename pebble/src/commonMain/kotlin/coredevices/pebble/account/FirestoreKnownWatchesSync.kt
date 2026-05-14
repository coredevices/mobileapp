package coredevices.pebble.account

import co.touchlab.kermit.Logger
import coredevices.pebble.hasConnectGoal
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.rebble.libpebblecommon.connection.KnownPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

interface FirestoreKnownWatchesSync {
    fun init()
}

class RealFirestoreKnownWatchesSync(
    private val dao: FirestoreKnownWatchesDao,
    private val libPebble: LibPebble,
) : FirestoreKnownWatchesSync {
    private val logger = Logger.withTag("FirestoreKnownWatchesSync")
    private val lastSynced = mutableMapOf<String, FirestoreKnownWatch>()

    override fun init() {
        GlobalScope.launch {
            Firebase.auth.authStateChanged.collect { user ->
                if (user == null) {
                    lastSynced.clear()
                    return@collect
                }
                try {
                    lastSynced.clear()
                    lastSynced.putAll(dao.getAll())
                    logger.d { "Loaded ${lastSynced.size} existing watches from Firestore" }
                } catch (e: Throwable) {
                    logger.w(e) { "failed to load existing watches from Firestore" }
                }
                libPebble.watches
                    .catch { e -> logger.w(e) { "error in watches flow" } }
                    .collect { watches ->
                        val snapshot = watches
                            .filterIsInstance<KnownPebbleDevice>()
                            .associate { watch ->
                                watch.serial to FirestoreKnownWatch(
                                    serial = watch.serial,
                                    lastConnectedMs = watch.lastConnected.toEpochMilliseconds(),
                                    runningFwVersion = watch.runningFwVersion,
                                    connectGoal = watch.hasConnectGoal(),
                                    watchType = watch.watchType.revision,
                                    color = watch.color?.name,
                                    nickname = watch.nickname,
                                )
                            }
                        for ((serial, new) in snapshot) {
                            if (lastSynced[serial] == new) continue
                            try {
                                dao.set(new)
                                lastSynced[serial] = new
                            } catch (e: Throwable) {
                                logger.w(e) { "failed to write known watch $serial" }
                            }
                        }
                    }
            }
        }
    }
}
