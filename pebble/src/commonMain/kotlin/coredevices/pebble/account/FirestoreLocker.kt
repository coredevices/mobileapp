package coredevices.pebble.account

import co.touchlab.kermit.Logger
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
import io.rebble.libpebblecommon.database.entity.LockerAppPermission
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.permissions.PermissionDecisionRecord
import io.rebble.libpebblecommon.web.LockerEntry
import io.rebble.libpebblecommon.web.LockerModel
import io.rebble.libpebblecommon.web.LockerModelWrapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private val logger = Logger.withTag("FirestoreLocker")

class FirestoreLockerDao(private val firestoreProvider: () -> FirebaseFirestore) {
    private val firestore get() = firestoreProvider()
    suspend fun addLockerEntryForUser(
        uid: String,
        entry: FirestoreLockerEntry
    ) {
        try {
            // Merged, leaving out unset fields: the web store may have written the permissions.
            entryDoc(uid, entry).set(entry, merge = true) { encodeDefaults = false }
        } catch (e: FirebaseFirestoreException) {
            throw FirestoreDaoException.fromFirebaseException(e)
        }
    }

    /**
     * Merged key by key, so other devices' decisions for other keys are kept. Fails if the entry is
     * gone: it mustn't be recreated with only permissions.
     */
    suspend fun mergePermissionsForUser(
        uid: String,
        entry: FirestoreLockerEntry,
        permissions: Map<String, PermissionDecisionRecord>,
    ) {
        try {
            entryDoc(uid, entry).updateFields {
                permissions.forEach { (key, record) -> FieldPath("permissions", key) to record }
            }
        } catch (e: FirebaseFirestoreException) {
            throw FirestoreDaoException.fromFirebaseException(e)
        }
    }

    private fun entryDoc(uid: String, entry: FirestoreLockerEntry) = firestore.collection("lockers")
        .document(uid)
        .collection("entries")
        .document(entry.documentId ?: "${entry.appstoreId}-${entry.uuid}")

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

    // Only emit server-confirmed snapshots. Firestore's snapshot listener will fire an
    // initial cached emission after auth re-init (often empty if the cache was invalidated)
    // before the server response arrives — acting on that would mass-delete the user's
    // pebble-store apps. includeMetadataChanges=true is required so we still get an event
    // when the server confirms identical-to-cache content (otherwise the listener would
    // never re-emit and we'd be stuck on the cached emission we filtered out).
    fun observeLockerEntriesForUser(uid: String): Flow<QuerySnapshot> = firestore.collection("lockers")
        .document(uid)
        .collection("entries")
        .snapshots(includeMetadataChanges = true)
        .filter {
            logger.v { "observeLockerEntriesForUser uid=${uid.take(8)}: ${it.documents.size} isFromCache=${it.metadata.isFromCache}" }
            !it.metadata.isFromCache
        }
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
    private val _locker = MutableStateFlow<List<FirestoreLockerEntry>?>(null)
    override val locker: StateFlow<List<FirestoreLockerEntry>?> = _locker.asStateFlow()
    private var fullSyncInProgress = false

    override fun init() {
        GlobalScope.launch {
            Firebase.auth.authStateChanged.collectLatest { user ->
                val userInfo = user?.let { "uid=${it.uid.take(8)} isAnonymous=${it.isAnonymous}" } ?: "null"
                logger.v { "User changed: $userInfo" }
                if (user == null) {
                    return@collectLatest
                }
                dao.observeLockerEntriesForUser(user.uid)
                    .catch { e -> logger.w(e) { "catching error in observe" } }
                    .collect {
                        if (fullSyncInProgress) {
                            logger.v { "Skipping firestore change during full sync (${it.documentChanges.size} changes)" }
                            return@collect
                        }
                        logger.d { "observeLockerEntriesForUser: changes=${it.documentChanges.size}" }
                        val lockerData = it.lockerEntries()
                        val previous = _locker.value
                        // Before handling: a locker sync it requests reads this snapshot.
                        _locker.value = lockerData
                        // Keep listening: in GlobalScope, a throw here would crash the app.
                        try {
                            handleFirestoreChanges(lockerData, previous)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logger.w(e) { "Error handling firestore locker changes" }
                        }
                    }
            }
        }
    }

    /**
     * Handle individual changes from firebase pushes (only for pebble store)
     */
    private suspend fun handleFirestoreChanges(locker: List<FirestoreLockerEntry>, previous: List<FirestoreLockerEntry>?) {
        val existingLocker = libPebbleLockerProxy.getAllLockerUuids().first()
        // A decision made elsewhere for an app already here: a locker sync brings it in. Our own
        // uploads change entries too, so only one newer than this device's counts.
        if (previous != null) {
            val before = previous.syncedPermissions()
            val changed = locker.syncedPermissions().filter { (uuid, decisions) -> uuid in existingLocker && before[uuid] != decisions }
            if (changed.isNotEmpty() && hasNewerDecisions(changed, libPebbleLockerProxy.permissionDecisions())) {
                // It adds any new apps too.
                libPebbleLockerProxy.requestLockerSync()
                return
            }
        }
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
        libPebbleLockerProxy.addAppsToLocker(appsToAdd, added.syncedPermissions())
        // A plugin-only app never reaches the watch.
        if (appsToAdd.size == 1 && AppType.fromString(appsToAdd.first().type) != AppType.Plugin) {
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
        // Short grace period for the server-confirmed snapshot during app boot. Returns
        // immediately if locker.value is already populated.
        val fsLocker = withTimeoutOrNull(2.seconds) {
            locker.first { it != null }
        }
        if (fsLocker == null) {
            logger.w { "fetchLocker: server-confirmed locker snapshot not yet received" }
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
            val lockerForSource = fsLocker.filter { it.appstoreSource == source.url }
            val appsForSource = appstore.fetchAppStoreApps(lockerForSource, useCache = !forceRefresh)
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
            appsForSource.fromDesignatedSource(source.url, designatedSourceByUuid)
        }
        val fetchedUuids = apps.map { Uuid.parse(it.uuid) }.toSet()
        val uuidsFromFailedSources = fsLocker
            .filter { it.appstoreSource in failedSources }
            .map { it.uuid }
            .toSet()
        return LockerModelWrapper(
            locker = LockerModel(
                applications = apps
            ),
            failedToFetchUuids = (fsLocker.map { it.uuid }.toSet() - fetchedUuids) + uuidsFromFailedSources,
            permissions = fsLocker.syncedPermissions(),
        )
        } finally {
            // Re-read locker from Firestore since we skipped listener updates during sync.
            try {
                val snapshot = withTimeoutOrNull(30.seconds) {
                    dao.observeLockerEntriesForUser(user.uid).first()
                }
                if (snapshot != null) {
                    _locker.value = snapshot.lockerEntries()
                } else {
                    logger.w { "Timed out waiting for server-confirmed snapshot after sync" }
                }
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
    suspend fun addAppsToLocker(apps: List<LockerEntry>, permissions: Map<Uuid, Map<String, PermissionDecisionRecord>>)
    suspend fun waitUntilAppSyncedToWatch(id: Uuid, timeout: Duration): Boolean
    suspend fun startAppOnWatch(id: Uuid): Boolean
    fun requestLockerSync()
    suspend fun permissionDecisions(): List<LockerAppPermission>
}

/**
 * An app only ever updates from the store it was installed from: another store publishing the same
 * UUID must not take over its permissions and data. Apps not yet in the locker are [sourceUrl]'s.
 */
internal fun List<LockerEntry>.fromDesignatedSource(sourceUrl: String, designatedSourceByUuid: Map<String, String>) =
    filter { (designatedSourceByUuid[it.uuid] ?: sourceUrl) == sourceUrl }

/** Whether [remote] holds a decision this device hasn't got, or has an older one of. */
internal fun hasNewerDecisions(
    remote: Map<Uuid, Map<String, PermissionDecisionRecord>>,
    local: List<LockerAppPermission>,
): Boolean {
    val held = local.associateBy { it.appUuid to it.permission }
    return remote.any { (uuid, decisions) ->
        decisions.any { (key, record) -> (held[uuid to key]?.decidedAtMs ?: -1) < record.decidedAtMs }
    }
}

/** The decisions to bring onto this device, by app. */
private fun List<FirestoreLockerEntry>.syncedPermissions(): Map<Uuid, Map<String, PermissionDecisionRecord>> =
    mapNotNull { entry -> entry.permissions?.filterKeys(::isSynced)?.takeIf { it.isNotEmpty() }?.let { entry.uuid to it } }
        .toMap()

@Serializable
data class FirestoreLockerEntry(
    val uuid: Uuid,
    val appstoreId: String,
    val appstoreSource: String,
    val timelineToken: String?,
    /** The user's decisions, by permission key, from any device or the web store. */
    val permissions: Map<String, PermissionDecisionRecord>? = null,
    /** Set when read: another client may have stored the entry under a different ID. */
    @Transient val documentId: String? = null,
)

/** One undecodable entry (e.g. written by another client) mustn't take the rest of the locker down with it. */
private fun QuerySnapshot.lockerEntries(): List<FirestoreLockerEntry> = documents.mapNotNull { doc ->
    try {
        doc.data<FirestoreLockerEntry>().copy(documentId = doc.id)
    } catch (e: Exception) {
        logger.w(e) { "Skipping invalid locker entry ${doc.id}" }
        null
    }
}

/**
 * Compare two version strings numerically by major.minor segments.
 * null versions sort lower than non-null.
 */
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