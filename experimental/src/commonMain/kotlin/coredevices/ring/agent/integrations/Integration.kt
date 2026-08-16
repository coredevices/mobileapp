package coredevices.ring.agent.integrations

import coredevices.mcp.SessionContext
import coredevices.util.integrations.Integration
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Manages reminders for one provider. Like [NoteIntegration], implementations are singletons that
 * manage reminders on a platform/service — not objects representing a single reminder. Obtain the
 * active provider's instance from `ReminderIntegrationFactory`.
 */
interface ReminderIntegration : Integration {
    /**
     * Schedules a reminder and returns its id (throws on failure). [listId] targets a list found
     * via [searchForList]; [notifyBefore] requests an extra early heads-up notification before
     * [deadline]. Integrations that can't honour either simply ignore them. Builtin integrations
     * create the feed item themselves, stamped from [source]; external ones ignore it.
     */
    suspend fun createReminder(
        title: String,
        deadline: Instant?,
        listId: String? = null,
        notifyBefore: Duration? = null,
        source: ItemSource? = null,
    ): String

    suspend fun searchForList(listName: String): List<ReminderListEntry>

    suspend fun getAllLists(): List<ReminderListEntry> = emptyList()
}

data class ReminderListEntry(
    val id: String,
    val title: String
)

private fun cleanUpTitle(title: String): String {
    //Remove 'list' from start or end of title, and trim whitespace
    return title.replace(Regex("(?i)^list\\s+|\\s+list$"), "").trim()
}

/**
 * Filters a list of [ReminderListEntry]s by a search string, returning only those whose
 * title matches the search string, ordered by descending relevance. A title also matches when it
 * appears as a whole phrase inside a longer query ("my grocery list" -> "Grocery list").
 */
internal fun List<ReminderListEntry>.fuzzyFilter(query: String): List<ReminderListEntry> {
    val q = cleanUpTitle(query).lowercase()
    return this.mapNotNull { entry ->
        val t = cleanUpTitle(entry.title).lowercase()
        val idx = t.indexOf(q)
        val score = when {
            t == q -> 0                                        // exact
            idx == 0 -> 1                                      // prefix
            idx > 0 && t[idx - 1] == ' ' -> 2          // word-start
            q.containsPhrase(t) -> 3                          // title contained in a longer query
            else -> return@mapNotNull null                    // no match, or mid-word (discard)
        }
        // A contained title consumes more of the query the longer it is, so it ranks the other way.
        Triple(entry, score, if (score == 3) -t.length else t.length)
    }.sortedWith(compareBy({ it.second }, { it.third }))
    .map { it.first }
}

/** True when [phrase] occurs in this string bounded by spaces or its ends. */
private fun String.containsPhrase(phrase: String): Boolean {
    if (phrase.isEmpty()) return false
    var from = 0
    while (true) {
        val i = indexOf(phrase, from)
        if (i < 0) return false
        val end = i + phrase.length
        if ((i == 0 || this[i - 1] == ' ') && (end == length || this[end] == ' ')) return true
        from = i + 1
    }
}

interface NoteIntegration : Integration {
    suspend fun createNote(content: String, source: ItemSource? = null): String?
}

/**
 * Identifies the recording a tool-created object came from, so builtin integrations can stamp
 * the feed items they create (feed grouping and reminder deep links rely on it). External
 * integrations ignore it.
 */
data class ItemSource(
    val recordingFirestoreId: String?,
    val createdAt: Instant?,
    val toolCallId: String?,
)

fun SessionContext.itemSource(): ItemSource =
    ItemSource(recordingFirestoreId, timeBase, toolCallId)