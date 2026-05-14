@file:OptIn(kotlin.time.ExperimentalTime::class)

package coredevices.ring.service.indexfeed

import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.ItemDocument
import coredevices.indexai.data.entity.ListDocument
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.CachedList
import coredevices.ring.database.Preferences
import coredevices.ring.database.firestore.dao.FirestoreItemsDao
import coredevices.ring.database.firestore.dao.FirestoreListsDao
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.ListRepository
import coredevices.ring.service.RecordingBackgroundScope
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/**
 * Bidirectional auto-sync between Room and Firestore for items + lists.
 *
 * Mirrors the existing recording auto-upload observer in
 * [coredevices.ring.service.recordings.RecordingProcessingQueue] so the
 * three index-feed collections (recordings, items, lists) all use the
 * same conceptual pattern: watch the local Room flow, push diffs out;
 * watch the Firestore snapshot, pull changes in.
 *
 * Eagerly resolved at app start (Koin singleton). The init block attaches
 * four observers — push items, push lists, pull items, pull lists — and
 * they run for the rest of the process lifetime.
 *
 * Conflict resolution: each [ItemDocument] / [ListDocument] carries an
 * `updatedAt: Instant`. The pull side only writes Room when remote is
 * strictly newer. The push side only writes Firestore when local has
 * advanced since we last pushed/pulled it. The in-memory [lastApplied]
 * map is the dedup key that breaks the local↔cloud echo cycle:
 *
 *   1. Local change → push observer fires → write Firestore.
 *      Record [lastApplied][id] = item.updatedAt.
 *   2. Firestore snapshot fires (our own write echoes back).
 *      Pull listener checks remote.updatedAt > local.updatedAt → equal,
 *      no Room write, no further work.
 *   3. Cloud change (from another device) → snapshot fires.
 *      Pull listener writes Room (remote is newer).
 *      Record [lastApplied][id] = remote.updatedAt.
 *   4. Room flow emits (because of step 3).
 *      Push observer sees `lastApplied[id] == item.updatedAt` → skip.
 *
 * [syncNow] is the manual entry point (Settings → Backup → Sync now).
 * It runs the same pull + push paths once with a forced `getAll()` from
 * Firestore, useful as a fresh-device first-sync or an after-offline
 * catch-up.
 */
class IndexFeedSyncService(
    private val itemRepo: ItemRepository,
    private val listRepo: ListRepository,
    private val firestoreItemsDao: FirestoreItemsDao,
    private val firestoreListsDao: FirestoreListsDao,
    private val preferences: Preferences,
    private val scope: RecordingBackgroundScope,
) {
    private val log = Logger.withTag("IndexFeedSync")

    /** (id → updatedAt) of the last value we either pushed to Firestore
     *  or pulled from it. Used by [pushItems]/[pushLists] to skip echoes
     *  of our own writes coming back via the snapshot listener (and vice
     *  versa). Per-process; cleared on app restart. */
    private val itemLastApplied = mutableMapOf<String, Instant>()
    private val listLastApplied = mutableMapOf<String, Instant>()
    private val mutex = Mutex()

    /** Emits the current Firebase user (or null) and re-emits on sign-in /
     *  sign-out. We gate every Firestore call on a non-null user — the
     *  underlying DAOs throw `IllegalStateException` if accessed while
     *  unauthenticated, which would crash app start (the syncher is
     *  eagerly constructed in [coredevices.ExperimentalDevices.appInit]
     *  so its observers are attached before login completes). */
    private val authState = flow {
        emit(Firebase.auth.currentUser)
        Firebase.auth.authStateChanged.collect { emit(it) }
    }

    init {
        // Local → Cloud (items). Push observer skips when no user; the
        // next sign-in re-emits a Room snapshot via the auth flow gate.
        // Uses the sync-only flow that INCLUDES soft-deleted rows — the
        // `deleted = true` flag is itself the tombstone we propagate.
        @OptIn(ExperimentalCoroutinesApi::class)
        authState.flatMapLatest { user ->
            if (user == null) flow<List<CachedItem>> {} else itemRepo.getAllForSyncFlow().drop(1)
        }
            .debounce(300)           // collapse bursts of edits
            .onEach { items ->
                if (preferences.backupEnabled.value) pushItems(items)
            }
            .flowOn(Dispatchers.IO)
            .catch { log.e(it) { "items push observer error" } }
            .launchIn(scope)

        // Local → Cloud (lists). Same tombstone-propagation rule.
        @OptIn(ExperimentalCoroutinesApi::class)
        authState.flatMapLatest { user ->
            if (user == null) flow<List<CachedList>> {} else listRepo.getAllForSyncFlow().drop(1)
        }
            .debounce(300)
            .onEach { lists ->
                if (preferences.backupEnabled.value) pushLists(lists)
            }
            .flowOn(Dispatchers.IO)
            .catch { log.e(it) { "lists push observer error" } }
            .launchIn(scope)

        // Cloud → Local (items). flatMapLatest cancels the inner snapshot
        // listener on sign-out and re-subscribes on sign-in.
        @OptIn(ExperimentalCoroutinesApi::class)
        authState.flatMapLatest { user ->
            if (user == null) flow<QuerySnapshot> {} else firestoreItemsDao.changesFlow()
        }
            .onEach { snap ->
                if (preferences.backupEnabled.value) pullItems(snap)
            }
            .flowOn(Dispatchers.IO)
            .catch { log.e(it) { "items pull listener error" } }
            .launchIn(scope)

        // Cloud → Local (lists)
        @OptIn(ExperimentalCoroutinesApi::class)
        authState.flatMapLatest { user ->
            if (user == null) flow<QuerySnapshot> {} else firestoreListsDao.changesFlow()
        }
            .onEach { snap ->
                if (preferences.backupEnabled.value) pullLists(snap)
            }
            .flowOn(Dispatchers.IO)
            .catch { log.e(it) { "lists pull listener error" } }
            .launchIn(scope)
    }

    /**
     * Manual full reconciliation (Settings → Backup → Sync now). Pulls
     * every doc from Firestore once, then pushes every local doc once.
     * The continuous observers above keep things consistent in real
     * time; this is a fresh-device or after-offline catch-up.
     */
    suspend fun syncNow() {
        if (!preferences.backupEnabled.value) return
        if (Firebase.auth.currentUser == null) {
            log.w { "syncNow: skipped (not authenticated)" }
            return
        }
        try { pullItems(firestoreItemsDao.getAll()) } catch (e: Exception) { log.w(e) { "syncNow: pull items failed" } }
        try { pullLists(firestoreListsDao.getAll()) } catch (e: Exception) { log.w(e) { "syncNow: pull lists failed" } }
        try { pushItems(itemRepo.getAllForSyncFlow().first()) } catch (e: Exception) { log.w(e) { "syncNow: push items failed" } }
        try { pushLists(listRepo.getAllForSyncFlow().first()) } catch (e: Exception) { log.w(e) { "syncNow: push lists failed" } }
    }

    // ── push (Room → Firestore) ─────────────────────────────────────────

    private suspend fun pushItems(items: List<CachedItem>) {
        val toPush = mutex.withLock {
            items.filter { itemLastApplied[it.firestoreId] != it.updatedAt }
        }
        if (toPush.isEmpty()) return
        try {
            firestoreItemsDao.writeBatch(toPush.map { it.firestoreId to it.toDocument() })
            mutex.withLock { toPush.forEach { itemLastApplied[it.firestoreId] = it.updatedAt } }
            log.i { "pushed ${toPush.size} items" }
        } catch (e: Exception) {
            log.w(e) { "pushItems failed (${toPush.size} items kept locally)" }
        }
    }

    private suspend fun pushLists(lists: List<CachedList>) {
        val toPush = mutex.withLock {
            lists.filter { listLastApplied[it.firestoreId] != it.updatedAt }
        }
        if (toPush.isEmpty()) return
        try {
            firestoreListsDao.writeBatch(toPush.map { it.firestoreId to it.toDocument() })
            mutex.withLock { toPush.forEach { listLastApplied[it.firestoreId] = it.updatedAt } }
            log.i { "pushed ${toPush.size} lists" }
        } catch (e: Exception) {
            log.w(e) { "pushLists failed (${toPush.size} lists kept locally)" }
        }
    }

    // ── pull (Firestore → Room) ─────────────────────────────────────────
    //
    // Pull processes BOTH `snap.documents` (current state — used by
    // syncNow's one-shot getAll() and snapshot-listener emissions alike,
    // for the upsert path) and `snap.documentChanges` filtered to
    // REMOVED, so hard deletes from another client / Firebase Console
    // propagate to local Room. Snapshot listeners surface REMOVED
    // events; one-shot get() snapshots do not, so the REMOVED loop is
    // a no-op on syncNow's branch.

    private suspend fun pullItems(snap: QuerySnapshot) {
        var applied = 0
        for (doc in snap.documents) {
            val remote = try { doc.data<ItemDocument>() } catch (e: Exception) {
                log.w(e) { "skip item ${doc.id}: deser failed" }; continue
            }
            val local = itemRepo.getById(doc.id)
            if (local == null || remote.updatedAt > local.updatedAt) {
                itemRepo.upsertLocal(doc.id, remote)
                mutex.withLock { itemLastApplied[doc.id] = remote.updatedAt }
                applied++
            }
        }
        var removed = 0
        for (change in snap.documentChanges) {
            if (change.type != dev.gitlive.firebase.firestore.ChangeType.REMOVED) continue
            val id = change.document.id
            if (itemRepo.getById(id) != null) {
                itemRepo.deleteLocal(id)
                mutex.withLock { itemLastApplied.remove(id) }
                removed++
            }
        }
        if (applied > 0 || removed > 0) {
            log.i { "items pull: applied=$applied removed=$removed" }
        }
    }

    private suspend fun pullLists(snap: QuerySnapshot) {
        var applied = 0
        for (doc in snap.documents) {
            val remote = try { doc.data<ListDocument>() } catch (e: Exception) {
                log.w(e) { "skip list ${doc.id}: deser failed" }; continue
            }
            val local = listRepo.getById(doc.id)
            if (local == null || remote.updatedAt > local.updatedAt) {
                listRepo.upsertLocal(doc.id, remote)
                mutex.withLock { listLastApplied[doc.id] = remote.updatedAt }
                applied++
            }
        }
        var removed = 0
        for (change in snap.documentChanges) {
            if (change.type != dev.gitlive.firebase.firestore.ChangeType.REMOVED) continue
            val id = change.document.id
            if (listRepo.getById(id) != null) {
                listRepo.deleteLocal(id)
                mutex.withLock { listLastApplied.remove(id) }
                removed++
            }
        }
        if (applied > 0 || removed > 0) {
            log.i { "lists pull: applied=$applied removed=$removed" }
        }
    }
}
