@file:OptIn(ExperimentalTime::class)

package coredevices.ring.data.entity.room.indexfeed

import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_TODOS_ID
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/** Todo = membership in the system Todos list, matching `IndexFeedViewModel`. */
private fun CachedItem.isWidgetTodo(): Boolean = parentListIds().contains(LIST_TODOS_ID)

/** Rows for the Android "Notes & To-dos" home-screen widget: newest first, ties by id. */
fun recentNotesAndTodos(items: List<CachedItem>, limit: Int = 5): List<CachedItem> {
    require(limit > 0) { "limit must be > 0, was $limit" }
    return items.asSequence()
        .filter { !it.deleted && !it.locked && !it.done }
        .filter { it.isWidgetTodo() || it.kind == "note" }
        .sortedWith(compareByDescending<CachedItem> { it.createdAt }.thenBy { it.firestoreId })
        .take(limit)
        .toList()
}

fun widgetRowSubtitle(item: CachedItem): String {
    if (!item.isWidgetTodo()) return "Note"
    val due = item.dueAt ?: return "To-do"
    return "Due ${due.toLocalDateTime(TimeZone.currentSystemDefault()).date}"
}

/** What the widget actually renders per row; used to skip no-op refreshes. */
fun widgetRenderFingerprint(rows: List<CachedItem>): List<Triple<String, String, String>> =
    rows.map { Triple(it.firestoreId, it.displayTitle, widgetRowSubtitle(it)) }
