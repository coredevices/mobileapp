package coredevices.ring.agent.builtin_servlets.messaging

/** How well a query matches a name, weakest first. */
internal enum class NameMatch { None, JoinedPrefix, WordPrefix, WholeWords, Exact }

internal data class BeeperChatCandidate(
    val roomId: String,
    val name: String,
    val match: NameMatch,
    val approved: Boolean = false,
    val lastActivity: Long = 0,
    val group: Boolean = false,
)

private fun nameWords(s: String): List<String> =
    s.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
        .split(' ').filter { it.isNotEmpty() && it != "and" }

internal fun nameMatch(query: String, name: String): NameMatch {
    val q = nameWords(query)
    val n = nameWords(name)
    if (q.isEmpty() || n.isEmpty()) return NameMatch.None
    if (q == n) return NameMatch.Exact
    if (q.all { it in n }) return NameMatch.WholeWords
    if (prefixesMatchDistinctWords(q, n)) return NameMatch.WordPrefix
    // "lizm" -> "Liz.M", "mcdonald" -> "Mc Donald": must still start at a word boundary
    val joinedQuery = q.joinToString("")
    if (n.indices.any { n.drop(it).joinToString("").startsWith(joinedQuery) }) return NameMatch.JoinedPrefix
    return NameMatch.None
}

private fun prefixesMatchDistinctWords(prefixes: List<String>, words: List<String>): Boolean {
    if (prefixes.isEmpty()) return true
    val first = prefixes.first()
    return words.indices.any { i ->
        words[i].startsWith(first) &&
            prefixesMatchDistinctWords(prefixes.drop(1), words.filterIndexed { j, _ -> j != i })
    }
}

internal fun nameMatch(query: String, names: List<String?>): NameMatch =
    names.filterNotNull().maxOfOrNull { nameMatch(query, it) } ?: NameMatch.None

/** Candidates at the strongest match level found; direct chats only, unless just group chats match that well. */
internal fun List<BeeperChatCandidate>.bestMatches(): List<BeeperChatCandidate> {
    val best = maxOfOrNull { it.match }?.takeIf { it != NameMatch.None } ?: return emptyList()
    val atBest = filter { it.match == best }
    return atBest.filterNot { it.group }.ifEmpty { atBest }
}

/**
 * Most recently active chat wins. It stays approved (sent without confirming) only if it is the
 * one approved chat at this level and every query word is a whole word of its name.
 */
internal fun List<BeeperChatCandidate>.pick(): BeeperChatCandidate? {
    val chats = distinctBy { it.roomId }
    val pick = chats.maxByOrNull { it.lastActivity } ?: return null
    return pick.copy(approved = pick.approved && chats.size == 1 && pick.match >= NameMatch.WholeWords)
}
