package coredevices.pebble.account

import co.touchlab.kermit.Logger
import coredevices.database.AppstoreSource
import coredevices.pebble.services.AppstoreService
import coredevices.pebble.services.RealPebbleWebServices
import coredevices.pebble.services.toLockerEntry
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FieldPath
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.code
import io.rebble.libpebblecommon.web.LockerEntry
import io.rebble.libpebblecommon.web.LockerModel
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
            firestore.collection("locker")
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

    suspend fun getLockerEntryForUser(
        uid: String,
        appstoreId: String,
        uuid: Uuid
    ): FirestoreLockerEntry? {
        try {
            val document = firestore.collection("locker")
                .document(uid)
                .collection("entries")
                .document("${appstoreId}-${uuid}")
                .get()
            return if (document.exists) {
                document.data()
            } else {
                null
            }
        } catch (e: FirebaseFirestoreException) {
            throw FirestoreDaoException.fromFirebaseException(e)
        }
    }
}

class FirestoreLocker(
    private val dao: FirestoreLockerDao,
): KoinComponent {
    private val scope = CoroutineScope(Dispatchers.Default)
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
                appstoreSource = equivalentSourceUrl
            )
            dao.addLockerEntryForUser(user.uid, firestoreEntry)
            emit((i + 1) to size)
        }
    }

    private suspend fun getLockerEntryFromStore(entry: FirestoreLockerEntry, useCache: Boolean = true): LockerEntry? {
        val appstore: AppstoreService = get { parametersOf(AppstoreSource(url = entry.appstoreSource, title = "")) }
        val appstoreApp = appstore.fetchAppStoreApp(entry.appstoreId, null, useCache)
            ?: return null
        return appstoreApp.toLockerEntry(entry.appstoreSource)
    }

    suspend fun fetchLocker(forceRefresh: Boolean = false): LockerModel? {
        val user = Firebase.auth.currentUser ?: return null
        val fsLocker = try {
            dao.getLockerEntriesForUser(user.uid)
        } catch (e: FirestoreDaoException) {
            logger.e(e) { "Error fetching locker entries from Firestore (uid ${user.uid}): ${e.message}" }
            return null
        }
        logger.d { "Fetched ${fsLocker.size} locker UUIDs from Firestore" }
        val applications = fsLocker.chunked(10).also {
            logger.d { "Fetching locker entries in ${it.size} chunks" }
        }.flatMap { lockerEntries ->
            val result = lockerEntries.map { lockerEntry ->
                scope.async {
                    getLockerEntryFromStore(lockerEntry, useCache = !forceRefresh) ?: run {
                        logger.w { "Failed to fetch locker entry for appstoreId=${lockerEntry.appstoreId}, uuid=${lockerEntry.uuid}" }
                        null
                    }
                }
            }.awaitAll()
            if (fsLocker.size > 20) {
                delay(50)
            }
            result
        }
        if (applications.all { it == null }) {
            logger.e { "Failed to fetch any locker entries from appstores" }
            return null
        }
        return try {
            LockerModel(
                applications = applications.filterNotNull()
            )
        } catch (e: IllegalStateException) {
            logger.e(e) { "Error fetching locker entries" }
            null
        }
    }

    suspend fun isLockerEmpty(): Boolean {
        val user = Firebase.auth.currentUser ?: return true
        return dao.isLockerEntriesEmptyForUser(user.uid)
    }

    suspend fun addApp(id: String, sourceUrl: String): Boolean {
        val user = Firebase.auth.currentUser ?: run {
            logger.e { "No authenticated user" }
            return false
        }
        val appstore: AppstoreService = get { parametersOf(AppstoreSource(url = sourceUrl, title = "")) }
        val appstoreApp = appstore.fetchAppStoreApp(id, null, useCache = false)
            ?: run {
                logger.e {"Failed to fetch appstore app for id=$id from source=$sourceUrl" }
                return false
            }
        val lockerEntry = appstoreApp.toLockerEntry(sourceUrl) ?: run {
            logger.e { "Failed to convert appstore app to locker entry for id=$id from source=$sourceUrl" }
            return false
        }
        val firestoreEntry = FirestoreLockerEntry(
            uuid = Uuid.parse(lockerEntry.uuid),
            appstoreId = lockerEntry.id,
            appstoreSource = sourceUrl
        )
        return try {
            dao.addLockerEntryForUser(user.uid, firestoreEntry)
            true
        } catch (e: FirestoreDaoException) {
            logger.e(e) { "Error adding locker entry to Firestore for user ${user.uid}, appstoreId=$id: ${e.message}" }
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
)