package coredevices.pebble.account

import co.touchlab.kermit.Logger
import coredevices.database.AppstoreSource
import coredevices.pebble.services.AppstoreService
import coredevices.pebble.services.RealPebbleWebServices
import coredevices.pebble.services.toLockerEntry
import coredevices.pebble.ui.CommonAppType
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FieldPath
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.code
import io.rebble.libpebblecommon.web.LockerEntry
import io.rebble.libpebblecommon.web.LockerModel
import io.rebble.libpebblecommon.web.LockerModelWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
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

class FirestoreLocker(
    private val dao: FirestoreLockerDao,
): KoinComponent {
    companion object {
        private val logger = Logger.withTag("FirestoreLocker")
    }
    /**
     * Imports locker entries from the Pebble API locker into Firestore.
     * @param equivalentSourceUrl The appstore source URL to associate with the imported entries.
     */
    fun importPebbleLocker(webServices: RealPebbleWebServices, equivalentSourceUrl: String) = flow {
        val user = Firebase.auth.currentUser ?: error("No authenticated user")
        val pebbleLocker = webServices.fetchPebbleLocker() ?: error("Failed to fetch Pebble locker")
        val size = pebbleLocker.applications.size
        emit(0 to size)
        for (i in pebbleLocker.applications.indices) {
            val entry = pebbleLocker.applications[i]
            val firestoreEntry = FirestoreLockerEntry(
                uuid = Uuid.parse(entry.uuid),
                appstoreId = entry.id,
                appstoreSource = equivalentSourceUrl,
                timelineToken = entry.userToken,
            )
            dao.addLockerEntryForUser(user.uid, firestoreEntry)
            emit((i + 1) to size)
        }
    }

    suspend fun fetchLocker(forceRefresh: Boolean = false): LockerModelWrapper? {
        val user = Firebase.auth.currentUser ?: return null
        val fsLocker = try {
            dao.getLockerEntriesForUser(user.uid)
        } catch (e: FirestoreDaoException) {
            logger.e(e) { "Error fetching locker entries from Firestore (uid ${user.uid}): ${e.message}" }
            return null
        }
        logger.d { "Fetched ${fsLocker.size} locker UUIDs from Firestore" }
        val appsBySource = fsLocker.groupBy { it.appstoreSource }
        val apps = appsBySource.flatMap { (source, entries) ->
            val appstore: AppstoreService = get { parametersOf(AppstoreSource(url = source, title = "")) }
            appstore.fetchAppStoreApps(entries, useCache = !forceRefresh)
        }
        return LockerModelWrapper(
            locker = LockerModel(
                applications = apps
            ),
            failedToFetchUuids = fsLocker.map { it.uuid }.toSet().minus(apps.map { Uuid.parse(it.uuid) }.toSet()),
        )
    }

    suspend fun isLockerEmpty(): Boolean {
        val user = Firebase.auth.currentUser ?: return true
        return dao.isLockerEntriesEmptyForUser(user.uid)
    }

    suspend fun addApp(entry: CommonAppType.Store, timelineToken: String?): Boolean {
        val user = Firebase.auth.currentUser ?: run {
            logger.e { "No authenticated user" }
            return false
        }
        if (entry.storeApp == null) {
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

    suspend fun removeApp(uuid: Uuid): Boolean {
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