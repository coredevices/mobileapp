package coredevices.pebble.account

import co.touchlab.kermit.Logger
import coredevices.database.AppstoreSource
import coredevices.database.AppstoreSourceDao
import coredevices.pebble.services.AppstoreService
import coredevices.pebble.services.REBBLE_FEED_URL
import coredevices.pebble.ui.CommonAppType
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FieldPath
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.QuerySnapshot
import dev.gitlive.firebase.firestore.code
import io.rebble.libpebblecommon.database.entity.APP_VERSION_REGEX
import io.rebble.libpebblecommon.web.LockerEntry
import io.rebble.libpebblecommon.web.LockerModel
import io.rebble.libpebblecommon.web.LockerModelWrapper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class FirestoreLockerDao(private val firestoreProvider: () -> FirebaseFirestore) {
    private val firestore get() = firestoreProvider()
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

    fun observeLockerEntriesForUser(uid: String): Flow<QuerySnapshot> = firestore.collection("lockers")
        .document(uid)
        .collection("entries")
        .snapshots
}

interface FirestoreLocker {
    val locker: StateFlow<List<FirestoreLockerEntry>?>
    suspend fun fetchLocker(forceRefresh: Boolean): LockerModelWrapper?
    suspend fun addApp(entry: CommonAppType.Store, timelineToken: String?): Boolean
    suspend fun removeApp(uuid: Uuid): Boolean
    fun init()
}

class RealFirestoreLocker(
    private val dao: FirestoreLockerDao,
    private val libPebbleLockerProxy: LibPebbleLockerProxy,
): KoinComponent, FirestoreLocker {
    companion object {
        private val logger = Logger.withTag("FirestoreLocker")
    }

    private val _locker = MutableStateFlow<List<FirestoreLockerEntry>?>(null)
    override val locker: StateFlow<List<FirestoreLockerEntry>?> = _locker.asStateFlow()
    private var fullSyncInProgress = false

    override fun init() {
        GlobalScope.launch {
            Firebase.auth.authStateChanged.collect { user ->
                logger.v { "User changed: $user" }
                if (user == null) {
                    return@collect
                }
                dao.observeLockerEntriesForUser(user.uid)
                    .catch { e -> logger.w(e) { "catching error in observe" } }
                    .collect {
                        if (fullSyncInProgress) {
                            logger.v { "Skipping firestore change during full sync (${it.documentChanges.size} changes)" }
                            return@collect
                        }
                        logger.d { "observeLockerEntriesForUser: changes=${it.documentChanges.size}" }
                        val lockerData: List<FirestoreLockerEntry> = it.documents.map { doc -> doc.data() }
                        handleFirestoreChanges(lockerData)
                        _locker.value = lockerData
                    }
            }
        }
    }

    /**
     * Handle individual changes from firebase pushes (only for pebble store)
     */
    private suspend fun handleFirestoreChanges(locker: List<FirestoreLockerEntry>) {
        val existingLocker = libPebbleLockerProxy.getAllLockerUuids().first()
        val added = locker.filter {
            it.uuid !in existingLocker && it.appstoreSource != REBBLE_FEED_URL
        }
        // Not handling removed here (web doesn't remove right now + we won't know if it's rebble/pebble)
        if (added.isEmpty()) {
            return
        }
        val allSources = get<AppstoreSourceDao>().getAllEnabledSources()
        val appsToAdd = allSources.flatMap {  source ->
            if (source.url == REBBLE_FEED_URL) {
                return@flatMap emptyList()
            }
            val lockerForSource = added.filter { it.appstoreSource == source.url }
            if (lockerForSource.isEmpty()) {
                return@flatMap emptyList()
            }
            val appstore: AppstoreService = get { parametersOf(source) }
            appstore.fetchAppStoreApps(lockerForSource, useCache = false) ?: emptyList()
        }
        logger.d { "Adding ${appsToAdd.size} apps to locker from firestore update" }
        libPebbleLockerProxy.addAppsToLocker(appsToAdd)
        if (appsToAdd.size == 1) {
            logger.d { "...and starting single new app on watch" }
            val app = appsToAdd.first()
            val uuid = Uuid.parse(app.uuid)
            if (!libPebbleLockerProxy.waitUntilAppSyncedToWatch(uuid, 15.seconds)) {
                logger.w { "timed out waiting for blobdb item to sync to watch" }
                return
            }
            // Give it a small bit of time to settle
            delay(0.5.seconds)
            libPebbleLockerProxy.startAppOnWatch(uuid)
        }
    }

    override suspend fun fetchLocker(forceRefresh: Boolean): LockerModelWrapper? {
        val user = Firebase.auth.currentUser ?: return null
        val fsLocker = locker.value
        if (fsLocker == null) {
            logger.w { "fetchLocker: locker is null" }
            return null
        }
        fullSyncInProgress = true
        try {
        logger.d { "Fetched ${fsLocker.size} locker UUIDs from Firestore" }
        val designatedSourceByUuid = fsLocker.associate { it.uuid.toString() to it.appstoreSource }
        val allSources = get<AppstoreSourceDao>().getAllEnabledSources()
        logger.v { "sources: $allSources" }
        val failedSources = mutableSetOf<String>()
        val apps = allSources.flatMap { source ->
            val appstore: AppstoreService = get { parametersOf(source) }
            val appsForSource = appstore.fetchAppStoreApps(fsLocker, useCache = !forceRefresh)
            if (appsForSource == null) {
                logger.w { "Failed to fetch apps from source ${source.url}" }
                failedSources.add(source.url)
                return@flatMap emptyList()
            }
            if (source.url == REBBLE_FEED_URL) {
                appsForSource.filter { f -> fsLocker.none { e -> Uuid.parse(f.uuid) == e.uuid } }.forEach { entry ->
                    // Add to firestore locker
                    val firestoreEntry = FirestoreLockerEntry(
                        uuid = Uuid.parse(entry.uuid),
                        appstoreId = entry.id,
                        appstoreSource = source.url,
                        timelineToken = entry.userToken,
                    )
                    dao.addLockerEntryForUser(user.uid, firestoreEntry)
                }
            }
            appsForSource.map { SourcedLockerEntry(source, it) }
        }
        // Deduplicate by UUID (same app can exist in multiple stores).
        // Prefer highest version, then the source the app is already associated with, then lowest source ID.
        val dedupedSourcedApps = apps.groupBy { it.entry.uuid }.values.map { duplicates ->
            if (duplicates.size == 1) {
                duplicates.first()
            } else {
                val designatedSource = designatedSourceByUuid[duplicates.first().entry.uuid]
                duplicates.maxWith(
                    Comparator<SourcedLockerEntry> { a, b -> compareVersionStrings(a.entry.version, b.entry.version) }
                        .thenBy { it.source.url == designatedSource }
                        .thenByDescending { it.source.id }
                )
            }
        }

        // Update Firestore source for any entry where the winning source changed.
        val fsLockerByUuid = fsLocker.associateBy { it.uuid.toString() }
        dedupedSourcedApps.forEach { sourced ->
            val existingEntry = fsLockerByUuid[sourced.entry.uuid] ?: return@forEach
            if (existingEntry.appstoreSource != sourced.source.url) {
                dao.addLockerEntryForUser(user.uid, existingEntry.copy(appstoreSource = sourced.source.url))
            }
        }

        val dedupedApps = dedupedSourcedApps.map { it.entry }
        val fetchedUuids = dedupedApps.map { Uuid.parse(it.uuid) }.toSet()
        val uuidsFromFailedSources = fsLocker
            .filter { it.appstoreSource in failedSources }
            .map { it.uuid }
            .toSet()
        return LockerModelWrapper(
            locker = LockerModel(
                applications = dedupedApps
            ),
            failedToFetchUuids = (fsLocker.map { it.uuid }.toSet() - fetchedUuids) + uuidsFromFailedSources,
        )
        } finally {
            // Re-read locker from Firestore since we skipped listener updates during sync
            try {
                val snapshot = dao.observeLockerEntriesForUser(user.uid).first()
                _locker.value = snapshot.documents.map { doc -> doc.data() }
            } catch (e: Exception) {
                logger.w(e) { "Failed to refresh locker after sync" }
            }
            fullSyncInProgress = false
        }
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

/**
 * Workaround for a cyclic dependency, because koin doesn't support Provider
 */
interface LibPebbleLockerProxy {
    fun getAllLockerUuids(): Flow<List<Uuid>>
    suspend fun addAppsToLocker(apps: List<LockerEntry>)
    suspend fun waitUntilAppSyncedToWatch(id: Uuid, timeout: Duration): Boolean
    suspend fun startAppOnWatch(id: Uuid): Boolean
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
private data class SourcedLockerEntry(
    val source: AppstoreSource,
    val entry: LockerEntry,
)

internal fun compareVersionStrings(a: String?, b: String?): Int {
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