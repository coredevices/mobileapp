@file:OptIn(ExperimentalTime::class)

package coredevices.ring.service.indexfeed

import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.ListDocument
import coredevices.ring.database.firestore.dao.FirestoreListsDao
import coredevices.ring.database.room.repository.ListRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Creates the three system seed lists for a user the first time they run the
 * new index-feed-enabled app version: Notes-to-self, Todos, Shopping.
 *
 * Self-healing: every call snapshots each of the three default lists in
 * Firestore individually and writes only the missing ones. If the user wipes
 * one or all of them via the Firebase Console, the next auth event re-creates
 * exactly those missing. Firestore is the source of truth — we deliberately
 * don't trust Room here, since the mirror can lag behind a remote delete.
 *
 * Stable doc IDs: `list_notes_self`, `list_todos`, `list_shopping`. Ingest can
 * route items to these without a query.
 */
class DefaultListsBootstrap(
    private val firestoreDao: FirestoreListsDao,
    private val repo: ListRepository,
) {
    private val logger = Logger.withTag("ListsBootstrap")

    /**
     * Idempotent. Returns true when a write happened this call, false when the
     * defaults were already present. Caller can ignore the return value.
     */
    suspend fun ensure(): Boolean {
        // Source of truth is Firestore. We check each of the three default
        // lists individually so that if the user deletes one list (or all of
        // them) on the Firebase Console, the next ensure() recreates only the
        // missing ones. Trusting Room here would leave us in sync with a
        // stale local mirror if remote was wiped.
        val now = Clock.System.now()
        val toWrite = mutableListOf<Pair<String, ListDocument>>()

        suspend fun maybeAdd(id: String, doc: () -> ListDocument) {
            val remote = runCatching { firestoreDao.getListSnapshot(id) }.getOrNull()
            if (remote == null) {
                // Network failure — be conservative and skip. We'll try again
                // on the next auth event.
                logger.w { "ensure: snapshot for $id failed, deferring" }
                return
            }
            if (!remote.exists) toWrite += id to doc()
        }

        maybeAdd(LIST_NOTES_SELF_ID) {
            ListDocument(
                createdAt = now, updatedAt = now,
                title = "Notes to self", icon = "📓",
                listKind = "note", seed = SEED_NOTES_SELF,
            )
        }
        maybeAdd(LIST_TODOS_ID) {
            ListDocument(
                createdAt = now, updatedAt = now,
                title = "Todos", icon = "⏰",
                listKind = "note", seed = SEED_TODOS,
            )
        }
        maybeAdd(LIST_SHOPPING_ID) {
            ListDocument(
                createdAt = now, updatedAt = now,
                title = "Shopping list", icon = "🛒",
                listKind = "checklist", seed = SEED_SHOPPING,
            )
        }

        if (toWrite.isEmpty()) return false
        logger.i { "ensure: writing ${toWrite.size} default list(s): ${toWrite.map { it.first }}" }
        // Bootstrap is the only write path that has to hit Firestore directly
        // (we just confirmed the doc is missing remotely, so we can't rely on
        // the manual Sync-now pipeline to create it later — the user might
        // never tap it). All OTHER list/item writes are Room-only and
        // pushed via Sync-now.
        firestoreDao.writeBatch(toWrite)
        repo.writeBatch(toWrite)
        return true
    }

    companion object {
        // Stable Firestore doc IDs. Ingest references LIST_TODOS_ID directly.
        const val LIST_NOTES_SELF_ID = "list_notes_self"
        const val LIST_TODOS_ID = "list_todos"
        const val LIST_SHOPPING_ID = "list_shopping"

        // Seed marker values stored on ListDocument.seed.
        const val SEED_NOTES_SELF = "notes_self"
        const val SEED_TODOS = "todos"
        const val SEED_SHOPPING = "shopping"
    }
}
