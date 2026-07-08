@file:OptIn(ExperimentalTime::class)

package coredevices.ring.service.indexfeed

import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.ListDocument
import coredevices.ring.database.firestore.dao.FirestoreListsDao
import coredevices.ring.database.room.repository.ListRepository
import dev.gitlive.firebase.firestore.DocumentSnapshot
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Creates the three system seed lists for a user the first time they run the
 * new index-feed-enabled app version: Notes-to-self, Reminders, Shopping.
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

        suspend fun maybeAdd(id: String, doc: () -> ListDocument): DocumentSnapshot? {
            val remote = runCatching { firestoreDao.getListSnapshot(id) }.getOrNull()
            if (remote == null) {
                // Network failure — be conservative and skip. We'll try again
                // on the next auth event.
                logger.w { "ensure: snapshot for $id failed, deferring" }
                return null
            }
            if (!remote.exists) toWrite += id to doc()
            return remote
        }

        maybeAdd(LIST_NOTES_SELF_ID) {
            ListDocument(
                createdAt = now, updatedAt = now,
                title = "Notes to self", icon = "📓",
                listKind = "note", seed = SEED_NOTES_SELF,
            )
        }
        val todosSnapshot = maybeAdd(LIST_TODOS_ID) {
            ListDocument(
                createdAt = now, updatedAt = now,
                title = TODOS_RENAMED_TITLE, icon = "⏰",
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

        // One-time rename for users seeded before the "Todos" → "Reminders"
        // rename. Gated on the stored title still being the old default, so a
        // user who renamed the list themselves is left untouched. The system
        // list is unencrypted (seed != null), so its title is readable here.
        val renamed = maybeRenameDefaultTodos(todosSnapshot, now)

        if (toWrite.isEmpty()) return renamed
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

    /** Returns true when a rename write happened this call. */
    private suspend fun maybeRenameDefaultTodos(snapshot: DocumentSnapshot?, now: Instant): Boolean {
        if (snapshot == null || !snapshot.exists) return false
        val existing = runCatching { snapshot.data<ListDocument>() }.getOrNull() ?: return false
        if (!isDefaultTodosListTitle(existing.seed, existing.title)) return false

        // The Firestore snapshot can lag a local rename that hasn't synced yet.
        // If the local mirror is no longer the default, the user renamed it —
        // don't clobber that with "Reminders".
        val local = repo.getById(LIST_TODOS_ID)
        if (local != null && !isDefaultTodosListTitle(local.seed, local.title)) return false

        val renamed = existing.copy(title = TODOS_RENAMED_TITLE, updatedAt = now)
        logger.i { "ensure: renaming default '$DEFAULT_TODOS_TITLE' list -> '$TODOS_RENAMED_TITLE'" }
        firestoreDao.setList(LIST_TODOS_ID, renamed)
        repo.setList(LIST_TODOS_ID, renamed)
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

        // The Todos list was originally seeded with this title; it now seeds as
        // TODOS_RENAMED_TITLE and existing users are migrated (see ensure()).
        const val DEFAULT_TODOS_TITLE = "Todos"
        const val TODOS_RENAMED_TITLE = "Reminders"

        /**
         * True when the stored Todos list still has its original default title
         * and is the system-seeded list — i.e. safe to rename to "Reminders".
         * Returns false once renamed, for user-renamed lists, and for any
         * non-system list, so the migration converges and never clobbers a
         * user's own title.
         */
        fun isDefaultTodosListTitle(seed: String?, title: String): Boolean =
            seed == SEED_TODOS && title == DEFAULT_TODOS_TITLE
    }
}
