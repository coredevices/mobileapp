package coredevices.util

enum class KeywordMatch {
    /** Matches only whole words, for languages that separate words with spaces (e.g. English). */
    WholeWord,

    /** Matches anywhere in the text, for languages without word boundaries or with attached particles (e.g. Japanese). */
    Substring,
}

data class LocalizedKeywords(
    val words: List<String>,
    val match: KeywordMatch,
)

/** A set of keywords across languages; text matches if it contains a keyword from any language. */
class MultilingualKeywords(private val entries: List<LocalizedKeywords>) {
    val words: List<String> = entries.flatMap { it.words }

    private val patterns: List<Regex> = entries.map { entry ->
        val alternatives = entry.words.joinToString("|") { Regex.escape(it) }
        when (entry.match) {
            KeywordMatch.WholeWord -> Regex("""\b(?:$alternatives)\b""", RegexOption.IGNORE_CASE)
            KeywordMatch.Substring -> Regex(alternatives, RegexOption.IGNORE_CASE)
        }
    }

    fun matches(text: String?): Boolean =
        text != null && patterns.any { it.containsMatchIn(text) }

    /** Keywords formatted for an LLM prompt, e.g. `'calendar', 'event'`. */
    fun promptList(): String = words.joinToString(", ") { "'$it'" }
}
