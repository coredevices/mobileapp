package coredevices.mcp.client

/**
 * Keyword scoring over tool names and descriptions. Tolerates word boundaries in `snake_case`
 * and `camelCase`, prefixes/plurals, and a typo or two on longer words.
 */
internal object ToolSearch {
    fun score(keywords: List<String>, vararg texts: String?): Int {
        val tokens = texts.filterNotNull().flatMap(::tokenize).toSet()
        if (tokens.isEmpty()) return 0
        return keywords.flatMap(::tokenize).sumOf { keyword -> tokens.maxOf { tokenScore(keyword, it) } }
    }

    fun tokenize(text: String): List<String> =
        text.replace(CAMEL_BOUNDARY, "$1 $2").lowercase().split(NON_ALPHANUMERIC).filter { it.isNotEmpty() }

    private fun tokenScore(keyword: String, token: String): Int {
        val shorter = minOf(keyword.length, token.length)
        return when {
            token == keyword -> EXACT
            shorter >= MIN_PARTIAL_LENGTH && (token.startsWith(keyword) || keyword.startsWith(token)) -> PREFIX
            shorter >= MIN_PARTIAL_LENGTH && (token.contains(keyword) || keyword.contains(token)) -> PARTIAL
            editDistance(keyword, token) <= allowedEdits(shorter) -> FUZZY
            else -> 0
        }
    }

    private fun allowedEdits(length: Int) = when {
        length >= 8 -> 2
        length >= 5 -> 1
        else -> 0
    }

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
            }
            previous = current
        }
        return previous[b.length]
    }

    private const val EXACT = 4
    private const val PREFIX = 3
    private const val PARTIAL = 2
    private const val FUZZY = 1
    private const val MIN_PARTIAL_LENGTH = 3
    private val CAMEL_BOUNDARY = Regex("([a-z0-9])([A-Z])")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
}
