package coredevices.ring.service.indexfeed

import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.GeofenceLocationDocument
import coredevices.ring.data.entity.room.SavedGeofenceLocation
import coredevices.ring.database.Preferences
import coredevices.ring.database.firestore.dao.FirestoreGeofenceLocationsDao
import coredevices.ring.database.room.repository.GeofenceLocationRepository
import coredevices.ring.encryption.DocumentEncryptor
import coredevices.ring.encryption.EncryptionManager
import coredevices.ring.encryption.KeyStorageStatus
import coredevices.ring.service.RecordingBackgroundScope
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.ChangeType
import dev.gitlive.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Bidirectional sync of saved places between Room and Firestore, following
 * [IndexFeedSyncService]: newest `updatedAt` wins, deletes travel as tombstones, and
 * [lastApplied] stops our own writes echoing back.
 *
 * An encrypted place this device can't decrypt is not stored (a place without coordinates is
 * no use); it is pulled again once a key is available.
 */
class GeofenceLocationSyncService(
    private val repository: GeofenceLocationRepository,
    private val firestoreDao: FirestoreGeofenceLocationsDao,
    private val preferences: Preferences,
    private val documentEncryptor: DocumentEncryptor,
    private val encryptionManager: EncryptionManager,
    private val scope: RecordingBackgroundScope,
) {
    private val lastApplied = mutableMapOf<Uuid, Instant>()
    private val mutex = Mutex()

    private val authState = flow {
        emit(Firebase.auth.currentUser)
        Firebase.auth.authStateChanged.collect { emit(it) }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start() {
        authState.flatMapLatest { user ->
            if (user == null) emptyFlow() else repository.getAllForSyncFlow().drop(1)
        }
            .debounce(300)
            .onEach { if (preferences.backupEnabled.value) push(it) }
            .flowOn(Dispatchers.IO)
            .catch { log.e(it) { "places push observer error" } }
            .launchIn(scope)

        authState.flatMapLatest { user ->
            if (user == null) emptyFlow() else firestoreDao.changesFlow()
        }
            .onEach { if (preferences.backupEnabled.value) pull(it) }
            .flowOn(Dispatchers.IO)
            .catch { log.e(it) { "places pull listener error" } }
            .launchIn(scope)

        // The pull listener only fires on a remote change, so places skipped for want of a key
        // need fetching again once one turns up or is replaced.
        combine(
            encryptionManager.keyStorageStatus.map { it == KeyStorageStatus.KeyLocallyAvailable },
            encryptionManager.keyStoreRevision,
        ) { available, revision -> available to revision }
            .filter { (available, _) -> available }
            .distinctUntilChanged()
            .onEach { repullWithKey() }
            .flowOn(Dispatchers.IO)
            .catch { log.e(it) { "places key-available observer error" } }
            .launchIn(scope)
    }

    private suspend fun repullWithKey() {
        if (!preferences.backupEnabled.value || Firebase.auth.currentUser == null) return
        try {
            pull(firestoreDao.getAll())
        } catch (e: Exception) {
            log.w(e) { "places re-pull failed" }
        }
    }

    private suspend fun push(places: List<SavedGeofenceLocation>) {
        val toPush = mutex.withLock { places.filter { lastApplied[it.id] != it.updatedAt } }
        if (toPush.isEmpty()) return
        val key = encryptionKeyOrNull()
        try {
            firestoreDao.writeBatch(toPush.map {
                val doc = it.toDocument()
                it.id.toString() to (if (key != null) documentEncryptor.encryptGeofenceLocation(doc, key) else doc)
            })
            mutex.withLock { toPush.forEach { lastApplied[it.id] = it.updatedAt } }
            log.i { "pushed ${toPush.size} places" }
        } catch (e: Exception) {
            log.w(e) { "push failed (${toPush.size} places kept locally)" }
        }
    }

    private suspend fun encryptionKeyOrNull(): String? {
        if (!preferences.useEncryption.value) return null
        return documentEncryptor.getKey().also {
            if (it == null) log.w { "Encryption enabled but no key available — pushing places unencrypted" }
        }
    }

    private suspend fun pull(snap: QuerySnapshot) {
        var applied = 0
        val key = documentEncryptor.getKey()
        for (doc in snap.documents) {
            val id = Uuid.parseOrNull(doc.id)
            val remote = try {
                doc.data<GeofenceLocationDocument>()
            } catch (e: Exception) {
                null
            }
            if (id == null || remote == null) {
                log.w { "skip place ${doc.id}: unreadable" }
                continue
            }
            val local = repository.getForSync(id)
            if (local == null || remote.updatedAt > local.updatedAt) {
                val resolved = decryptOrNull(id, remote, key) ?: continue
                repository.applyRemote(SavedGeofenceLocation.fromDocument(id, resolved))
                applied++
            }
            mutex.withLock { lastApplied[id] = remote.updatedAt }
        }
        var removed = 0
        for (change in snap.documentChanges) {
            if (change.type != ChangeType.REMOVED) continue
            val id = Uuid.parseOrNull(change.document.id) ?: continue
            if (repository.getForSync(id) != null) {
                repository.removeLocal(id)
                mutex.withLock { lastApplied.remove(id) }
                removed++
            }
        }
        if (applied > 0 || removed > 0) log.i { "places pull: applied=$applied removed=$removed" }
    }

    private fun decryptOrNull(id: Uuid, remote: GeofenceLocationDocument, key: String?): GeofenceLocationDocument? {
        if (remote.encrypted == null) return remote
        if (key == null) {
            log.w { "Encrypted place $id but no key — skipping" }
            return null
        }
        return try {
            documentEncryptor.decryptGeofenceLocation(remote, key)
        } catch (e: Exception) {
            log.w(e) { "Place $id decrypt failed — skipping" }
            null
        }
    }

    companion object {
        private val log = Logger.withTag("GeofenceLocationSync")
    }
}
