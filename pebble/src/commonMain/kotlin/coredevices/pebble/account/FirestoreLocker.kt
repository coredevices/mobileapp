package coredevices.pebble.account

import co.touchlab.kermit.Logger
import coredevices.database.AppstoreSource
import coredevices.pebble.services.AppstoreService
import coredevices.pebble.services.REBBLE_FEED_URL
import coredevices.pebble.ui.CommonAppType
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FieldPath
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.code
import io.rebble.libpebblecommon.database.entity.APP_VERSION_REGEX
import io.rebble.libpebblecommon.web.LockerEntry
import io.rebble.libpebblecommon.web.LockerModel
import io.rebble.libpebblecommon.web.LockerModelWrapper
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

class FirestoreLockerDao(private val firestore: FirebaseFirestore) {
    suspend fun getLockerEntriesForUser(uid: String): List<FirestoreLockerEntry> {
        try {
            return firestore.collection("lockers")
                .document(uid)
                .collection("entries")
                .get()
                .documents
                .map {
                    it.data()
                }
        } catch (e: FirebaseFirestoreException) {
            throw FirestoreDaoException.fromFirebaseException(e)
        }
    }

    suspend fun isLockerEntriesEmptyForUser(uid: String): Boolean {
        try {
            val querySnapshot = firestore.collection("lockers")
                .document(uid)
                .collection("entries")
                .limit(1)
                .get()
            return querySnapshot.documents.isEmpty()
        } catch (e: FirebaseFirestoreException) {
            throw FirestoreDaoException.fromFirebaseException(e)
        }
    }

    suspend fun addLockerEntryForUser(
        uid: String,
        entry: FirestoreLockerEntry
    ) {
        try {
            firestore.collection("lockers")
                .document(uid)
                .collection("entries")
                .document("${entry.appstoreId}-${entry.uuid}")
                .set(entry)
        } catch (e: FirebaseFirestoreException) {
            throw FirestoreDaoException.fromFirebaseException(e)
        }
    }

    suspend fun removeLockerEntryForUser(
        uid: String,
        uuid: Uuid
    ) {
        try {
            firestore.collection("lockers")
                .document(uid)
                .collection("entries")
                .where {
                    FieldPath("uuid") equalTo uuid.toString()
                }
                .get()
                .documents
                .forEach { it.reference.delete() }
        } catch (e: FirebaseFirestoreException) {
            throw FirestoreDaoException.fromFirebaseException(e)
        }
    }
}

interface FirestoreLocker {
    suspend fun readLocker(): List<FirestoreLockerEntry>?
    suspend fun fetchLocker(forceRefresh: Boolean = false): LockerModelWrapper?
    suspend fun addApp(entry: CommonAppType.Store, timelineToken: String?): Boolean
    suspend fun removeApp(uuid: Uuid): Boolean
}

class RealFirestoreLocker(
    private val dao: FirestoreLockerDao,
): KoinComponent, FirestoreLocker {
    companion object {
        private val logger = Logger.withTag("FirestoreLocker")
    }

    override suspend fun readLocker(): List<FirestoreLockerEntry>? {
        val user = Firebase.auth.currentUser ?: return null
        return try {
            dao.getLockerEntriesForUser(user.uid)
        } catch (e: FirestoreDaoException) {
            logger.e(e) { "Error fetching locker entries from Firestore (uid ${user.uid}): ${e.message}" }
            null
        }
    }

    override suspend fun fetchLocker(forceRefresh: Boolean): LockerModelWrapper? {
        val user = Firebase.auth.currentUser ?: return null
        val fsLocker = try {
            dao.getLockerEntriesForUser(user.uid)
        } catch (e: FirestoreDaoException) {
            logger.e(e) { "Error fetching locker entries from Firestore (uid ${user.uid}): ${e.message}" }
            return null
        }
        logger.d { "Fetched ${fsLocker.size} locker UUIDs from Firestore" }
        val appsBySource = fsLocker.groupBy { it.appstoreSource }.let {
            if (REBBLE_FEED_URL in it) {
                it
            } else {
                // Force it to at least maybe call rebble sync
                it + (REBBLE_FEED_URL to emptyList())
            }
        }
        val apps = appsBySource.flatMap { (source, entries) ->
            val appstore: AppstoreService = get { parametersOf(AppstoreSource(url = source, title = "")) }
            val appsForSource = appstore.fetchAppStoreApps(entries, useCache = !forceRefresh)
            if (source == REBBLE_FEED_URL) {
                appsForSource.filter { f -> entries.none { e -> Uuid.parse(f.uuid) == e.uuid } }.forEach { entry ->
                    // Add to firestore locker
                    val firestoreEntry = FirestoreLockerEntry(
                        uuid = Uuid.parse(entry.uuid),
                        appstoreId = entry.id,
                        appstoreSource = source,
                        timelineToken = entry.userToken,
                    )
                    dao.addLockerEntryForUser(user.uid, firestoreEntry)
                }
            }
            appsForSource
        }
        // Deduplicate by UUID (same app can exist in multiple stores).
        // Prefer the entry with the higher version, or the earlier source if tied.
        val sourceCount = appsBySource.keys.size
        val sourcePriority = appsBySource.keys.withIndex().associate { (i, url) -> url to (sourceCount - i) }
        val dedupedApps = apps.groupBy { it.uuid }.values.map { duplicates ->
            if (duplicates.size == 1) {
                duplicates.first()
            } else {
                duplicates.maxWith(
                    Comparator<LockerEntry> { a, b -> compareVersionStrings(a.version, b.version) }
                        .thenBy { sourcePriority[it.source] ?: 0 }
                )
            }
        }
        return LockerModelWrapper(
            locker = LockerModel(
                applications = dedupedApps
            ),
            failedToFetchUuids = fsLocker.map { it.uuid }.toSet().minus(dedupedApps.map { Uuid.parse(it.uuid) }.toSet()),
        )
    }

    override suspend fun addApp(entry: CommonAppType.Store, timelineToken: String?): Boolean {
        val user = Firebase.auth.currentUser ?: run {
            logger.e { "No authenticated user" }
            return false
        }
        if (entry.storeApp?.uuid == null) {
            return false
        }
        val firestoreEntry = FirestoreLockerEntry(
            uuid = Uuid.parse(entry.storeApp.uuid),
            appstoreId = entry.storeApp.id,
            appstoreSource = entry.storeSource.url,
            timelineToken = timelineToken,
        )
        return try {
            dao.addLockerEntryForUser(user.uid, firestoreEntry)
            true
        } catch (e: FirestoreDaoException) {
            logger.e(e) { "Error adding locker entry to Firestore for user ${user.uid}, appstoreId=${entry.storeApp.id}: ${e.message}" }
            false
        }
    }

    override suspend fun removeApp(uuid: Uuid): Boolean {
        val user = Firebase.auth.currentUser ?: run {
            logger.e { "No authenticated user" }
            return false
        }
        return try {
            dao.removeLockerEntryForUser(user.uid, uuid)
            true
        } catch (e: FirestoreDaoException) {
            logger.e(e) { "Error removing locker entry from Firestore for user ${user.uid}, uuid=$uuid: ${e.message}" }
            false
        }
    }
}

sealed class FirestoreDaoException(override val cause: Throwable? = null, private val code: FirestoreExceptionCode?) : Exception() {
    class NetworkException(cause: Throwable? = null, code: FirestoreExceptionCode?) : FirestoreDaoException(cause, code)
    class UnknownException(cause: Throwable? = null, code: FirestoreExceptionCode?) : FirestoreDaoException(cause, code)

    override val message: String?
        get() = "FirestoreDaoException with code: ${code?.name}"

    companion object {
        fun fromFirebaseException(e: FirebaseFirestoreException): FirestoreDaoException {
            return when (e.code) {
                FirestoreExceptionCode.UNAVAILABLE, FirestoreExceptionCode.DEADLINE_EXCEEDED -> NetworkException(e, e.code)
                else -> UnknownException(e, e.code)
            }
        }
    }
}

@Serializable
data class FirestoreLockerEntry(
    val uuid: Uuid,
    val appstoreId: String,
    val appstoreSource: String,
    val timelineToken: String?,
)

/**
 * Compare two version strings numerically by major.minor segments.
 * null versions sort lower than non-null.
 */
private fun compareVersionStrings(a: String?, b: String?): Int {
    if (a == null && b == null) return 0
    if (a == null) return -1
    if (b == null) return 1
    val aMatch = APP_VERSION_REGEX.find(a)
    val bMatch = APP_VERSION_REGEX.find(b)
    val aMajor = aMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    val aMinor = aMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
    val bMajor = bMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    val bMinor = bMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
    return compareValuesBy(aMajor to aMinor, bMajor to bMinor, { it.first }, { it.second })
}