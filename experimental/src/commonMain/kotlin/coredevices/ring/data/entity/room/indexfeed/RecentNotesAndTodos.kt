@file:OptIn(ExperimentalTime::class)

package coredevices.ring.data.entity.room.indexfeed

import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_TODOS_ID
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Todo = membership in the system Todos list, matching `IndexFeedViewModel`. */
fun CachedItem.isWidgetTodo(): Boolean = parentListIds().contains(LIST_TODOS_ID)

private fun CachedItem.isWidgetVisible(): Boolean =
    !deleted && !locked && !done && (isWidgetTodo() || kind == "note")

/** Rows for the Android "Notes & To-dos" home-screen widget: newest first, ties by id. */
fun recentNotesAndTodos(items: List<CachedItem>, limit: Int = 5): List<CachedItem> {
    require(limit > 0) { "limit must be > 0, was $limit" }
    return items.asSequence()
        .filter { it.isWidgetVisible() }
        .sortedWith(compareByDescending<CachedItem> { it.createdAt }.thenBy { it.firestoreId })
        .take(limit)
        .toList()
}

data class WidgetCounts(val todos: Int, val notes: Int)

/** To-dos vs notes among the items the widget would consider (before the row cap). */
fun widgetCounts(items: List<CachedItem>): WidgetCounts {
    val visible = items.filter { it.isWidgetVisible() }
    val todos = visible.count { it.isWidgetTodo() }
    return WidgetCounts(todos = todos, notes = visible.size - todos)
}

private fun LocalDate.dayMonth(): String =
    "$day ${month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}"

/** "now" / "5 min" / "3 h" / "Yesterday" / "1 Sep". */
fun relativeTime(at: Instant, now: Instant, tz: TimeZone = TimeZone.currentSystemDefault()): String {
    val elapsed = now - at
    if (elapsed < 1.minutes) return "now"
    if (elapsed < 1.hours) return "${elapsed.inWholeMinutes} min"
    val today = now.toLocalDateTime(tz).date
    val date = at.toLocalDateTime(tz).date
    return when {
        date == today -> "${elapsed.inWholeHours} h"
        date.toEpochDays() == today.toEpochDays() - 1 -> "Yesterday"
        else -> date.dayMonth()
    }
}

/** To-dos: "Due today" / "Due tomorrow" / "Due 15 Sep" / "Overdue" / "To-do".
 *  Notes: the parent list's title, or "Note" when unfiled / unknown. */
fun widgetRowLabel(
    item: CachedItem,
    listTitles: Map<String, String>,
    now: Instant,
    tz: TimeZone = TimeZone.currentSystemDefault(),
): String {
    if (item.isWidgetTodo()) {
        val due = item.dueAt ?: return "To-do"
        val today = now.toLocalDateTime(tz).date
        val dueDate = due.toLocalDateTime(tz).date
        return when {
            dueDate.toEpochDays() < today.toEpochDays() -> "Overdue"
            dueDate == today -> "Due today"
            dueDate.toEpochDays() == today.toEpochDays() + 1 -> "Due tomorrow"
            else -> "Due ${dueDate.dayMonth()}"
        }
    }
    return item.parentListIds().firstNotNullOfOrNull { listTitles[it] } ?: "Note"
}

/** What the widget actually renders per row (title + label; relative time excluded so
 *  the fingerprint doesn't churn every minute); used to skip no-op refreshes. */
fun widgetRenderFingerprint(
    rows: List<CachedItem>,
    listTitles: Map<String, String> = emptyMap(),
    now: Instant = Clock.System.now(),
    tz: TimeZone = TimeZone.currentSystemDefault(),
): List<Triple<String, String, String>> =
    rows.map { Triple(it.firestoreId, it.displayTitle, widgetRowLabel(it, listTitles, now, tz)) }
