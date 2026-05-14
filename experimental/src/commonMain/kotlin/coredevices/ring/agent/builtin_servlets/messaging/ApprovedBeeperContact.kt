package coredevices.ring.agent.builtin_servlets.messaging

import kotlinx.serialization.Serializable

@Serializable
data class ApprovedBeeperContact(
    val roomId: String,
    val name: String,
    val nickname: String? = null
) {
    /**
     * Check if the given query matches this contact's name or nickname.
     * Supports substring matching, token matching, and quoted-nickname matching.
     * e.g. "Liz" matches "Elizabeth 'Liz' Migicovsky" or nickname "Liz"
     */
    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return false

        val nameLower = name.lowercase()
        val nickLower = nickname?.lowercase()

        // Exact full match
        if (nameLower == q || nickLower == q) return true

        // Substring match on full name or nickname
        if (nameLower.contains(q) || nickLower?.contains(q) == true) return true

        // Token match: query matches start of any word in the name
        // "Mig" matches "Elizabeth Migicovsky"
        val nameTokens = nameLower.split(" ", "'", "'", "'", "\"", "(", ")")
            .filter { it.isNotBlank() }
        if (nameTokens.any { it.startsWith(q) }) return true

        // Check quoted nicknames embedded in the display name
        // "Elizabeth 'Liz' Migicovsky" -> extract "liz"
        val quotedPattern = Regex("['\u2018\u2019'\"]([^'\"\\u2018\\u2019]+)['\u2018\u2019'\"]")
        quotedPattern.findAll(nameLower).forEach { match ->
            val quoted = match.groupValues[1].trim()
            if (quoted == q || quoted.contains(q)) return true
        }

        return false
    }

    /**
     * Score how well a query matches this contact. Higher = better match.
     * Returns 0 if no match.
     */
    fun matchScore(query: String): Int {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 0

        val nameLower = name.lowercase()
        val nickLower = nickname?.lowercase()

        // Exact full name match
        if (nameLower == q) return 100
        // Exact nickname match
        if (nickLower == q) return 95
        // Nickname starts with query
        if (nickLower?.startsWith(q) == true) return 90
        // First name exact match
        val firstName = nameLower.split(" ").firstOrNull()
        if (firstName == q) return 85
        // First name starts with query
        if (firstName?.startsWith(q) == true) return 80
        // Quoted nickname match
        val quotedPattern = Regex("['\u2018\u2019'\"]([^'\"\\u2018\\u2019]+)['\u2018\u2019'\"]")
        quotedPattern.findAll(nameLower).forEach { match ->
            val quoted = match.groupValues[1].trim()
            if (quoted == q) return 75
            if (quoted.startsWith(q)) return 70
        }
        // Any name token starts with query
        val nameTokens = nameLower.split(" ", "'", "'", "'", "\"", "(", ")")
            .filter { it.isNotBlank() }
        if (nameTokens.any { it.startsWith(q) }) return 60
        // Substring match on nickname
        if (nickLower?.contains(q) == true) return 50
        // Substring match on full name
        if (nameLower.contains(q)) return 40

        return 0
    }
}
