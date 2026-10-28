package coredevices.pebble.services

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.pebble.account.FirestoreLockerDao
import coredevices.pebble.account.FirestoreLockerEntry
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.locker.LockerWrapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * The phone app used to sideload its plugin demos; they're on the store now. Adds each copy it
 * sideloaded to the user's locker and stops treating it as sideloaded, so locker sync swaps in the
 * store version (keeping its permissions and data) and store updates reach it.
 */
class BundledAppMigration(
    private val settings: Settings,
    private val libPebble: LibPebble,
    private val firestoreLockerDao: FirestoreLockerDao,
) {
    private val logger = Logger.withTag("BundledAppMigration")

    fun init() {
        GlobalScope.launch(Dispatchers.Default) {
            val pending = STORE_IDS.filterKeys { settings.hasKey(stampKey(it)) }
            if (pending.isEmpty()) return@launch
            val user = Firebase.auth.authStateChanged.filterNotNull().first()
            pending.forEach { (uuid, storeId) ->
                try {
                    val existing = libPebble.getLockerApp(uuid).first()
                    if (existing is LockerWrapper.NormalApp && existing.sideloaded) {
                        // Waits for the server: a locker sync drops a non-sideloaded app the remote
                        // locker doesn't have.
                        firestoreLockerDao.addLockerEntryForUser(
                            user.uid,
                            FirestoreLockerEntry(uuid = uuid, appstoreId = storeId, appstoreSource = PEBBLE_FEED_URL, timelineToken = null),
                        )
                        libPebble.clearSideloaded(uuid)
                        logger.i { "moved $uuid to its store listing" }
                    }
                    settings.remove(stampKey(uuid))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(e) { "couldn't move $uuid to its store listing; will retry next launch" }
                }
            }
            libPebble.requestLockerSync()
        }
    }

    private fun stampKey(uuid: Uuid) = "bundled_app_$uuid"

    private companion object {
        val STORE_IDS = mapOf(
            Uuid.parse("8b1c6b0e-7d6a-4cf2-a9b2-2c3f8b1c6b0e") to "73ad498d42134f1eb55b32f1",
            Uuid.parse("438e789f-1f16-424e-829c-0c2f4865fbe8") to "4770d12a965e489eb6116b7b",
            Uuid.parse("3f6d2a90-5c41-4b7e-9d38-6a1e0c4f27b5") to "dbfbcb578c8944b1bdffa544",
            Uuid.parse("f07173d2-86dc-40d2-b3b1-0bcb911b8a83") to "e564351ea95c4dee9d534ff5",
            Uuid.parse("323dc117-55bd-455f-9ee4-4d0faba56ea1") to "6972cb1f33e343d69e5b788e",
            Uuid.parse("6f9c1a44-3d1e-4b8a-9c2f-0d5e7a1b3c40") to "61e3f7d77ca7421dbfdb0ab2",
            Uuid.parse("2c4b7f10-9a3d-4e6b-8f21-5d0c7e9a1b34") to "730a1d95229d42e9b66098cb",
            Uuid.parse("c7ad904e-40d5-4cb5-8f13-1d1d3c6a7e21") to "064ea881619da48c131d35d9",
            Uuid.parse("b1740936-c2e8-4dd6-b4cf-a85058109ac0") to "34cc4183258c725f01f94878",
            Uuid.parse("0d520cba-326c-4a22-a269-d1ab1ae38977") to "20415cd5ebe281b69ed9ee53",
        )
    }
}
