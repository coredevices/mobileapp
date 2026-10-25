package coredevices.ring.agent.integrations.providers

/** Letters and digits only, lowercased, so "Tick Tick" and "TickTick" compare equal. */
internal fun normalizeProviderName(name: String): String = name.filter { it.isLetterOrDigit() }.lowercase()

/** True when [name] occurs in this text as a run of whole words, ignoring case and spacing. */
internal fun String.mentionsProviderName(name: String): Boolean {
    val target = normalizeProviderName(name)
    if (target.isEmpty()) return false
    val words = words()
    return words.indices.any { start ->
        var run = ""
        for (word in words.subList(start, words.size)) {
            run += word
            if (run.length >= target.length) return@any run == target
        }
        false
    }
}

private fun String.words(): List<String> = buildList {
    val word = StringBuilder()
    for (c in this@words) {
        if (c.isLetterOrDigit()) {
            word.append(c)
        } else if (word.isNotEmpty()) {
            add(word.toString().lowercase())
            word.clear()
        }
    }
    if (word.isNotEmpty()) add(word.toString().lowercase())
}
