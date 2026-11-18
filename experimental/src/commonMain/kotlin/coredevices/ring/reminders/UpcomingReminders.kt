package coredevices.ring.reminders

import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Open reminders in Todos order; [lingeringIds] keeps just-completed ones visible. */
internal fun upcomingReminders(
    items: List<CachedItem>,
    lingeringIds: Set<String> = emptySet(),
    now: Instant = Clock.System.now(),
    limit: Int = 100,
): List<CachedItem> =
    items
        .filter { it.metadata is ItemMetadata.Reminder && !it.deleted }
        .filter { !it.done || it.firestoreId in lingeringIds }
        .sortedWith(todoComparator(now))
        .take(limit)

/** Due within 24h (soonest first), then overdue (most recent first), then undated, then later. */
internal fun todoComparator(now: Instant = Clock.System.now()): Comparator<CachedItem> {
    val urgentCutoff = now + 24.hours
    fun bucket(dueAt: Instant?) = when {
        dueAt == null -> 2
        dueAt <= now -> 1
        dueAt <= urgentCutoff -> 0
        else -> 3
    }
    return compareBy<CachedItem> { bucket(it.dueAt) }
        .thenBy { task ->
            val dueMs = task.dueAt?.toEpochMilliseconds() ?: Long.MAX_VALUE
            if (bucket(task.dueAt) == 1) -dueMs else dueMs
        }
        .thenBy { it.createdAt.toEpochMilliseconds() }
        .thenBy { it.title.lowercase() }
}
