package coredevices.ring.agent.integrations.obsidian

/** Resolved Obsidian config used to decide how/where to write a note. */
data class ObsidianConfig(
    val mode: ObsidianMode,
    val targetNote: String,
    val subfolder: String,
)

/** A concrete filesystem action for one note. [fileName] may contain one subfolder segment. */
sealed interface ObsidianWrite {
    /** Create (or overwrite) a brand-new file with [content]. */
    data class NewFile(val fileName: String, val content: String) : ObsidianWrite
    /** Append [block] to an existing file (creating it if absent). */
    data class Append(val fileName: String, val block: String) : ObsidianWrite
}

/**
 * Pure formatting + mode dispatch. Timestamp is passed as parts so this has no
 * datetime/platform dependency and is trivially unit-testable.
 */
object ObsidianNoteFormatter {

    const val MAIN_NOTE_NAME = "Pebble Index.md"

    fun plan(config: ObsidianConfig, content: String, year: Int, month: Int, day: Int, hour: Int, minute: Int): ObsidianWrite {
        val date = "$year-${pad2(month)}-${pad2(day)}"
        val compactTime = "${pad2(hour)}${pad2(minute)}"
        val isoMinute = "${date}T${pad2(hour)}:${pad2(minute)}"
        return when (config.mode) {
            ObsidianMode.TIMESTAMPED_FILES -> {
                val name = "$date $compactTime.md"
                val sub = sanitizeSubfolder(config.subfolder)
                val fileName = if (sub.isEmpty()) name else "$sub/$name"
                ObsidianWrite.NewFile(fileName, timestampedContent(isoMinute, content))
            }
            ObsidianMode.MAIN_NOTE ->
                ObsidianWrite.Append(MAIN_NOTE_NAME, appendBlock(date, hour, minute, content))
            ObsidianMode.NAMED_NOTE ->
                ObsidianWrite.Append(withMdExtension(config.targetNote), appendBlock(date, hour, minute, content))
        }
    }

    private fun timestampedContent(isoMinute: String, content: String): String =
        "---\ncreated: $isoMinute\ntags: [index]\n---\n\n${content.trimEnd()}\n"

    private fun appendBlock(date: String, hour: Int, minute: Int, content: String): String =
        "## $date ${pad2(hour)}:${pad2(minute)}\n\n${content.trimEnd()}\n"

    /** Concatenate [block] after [existing], guaranteeing exactly one blank line between them. */
    fun mergeAppend(existing: String?, block: String): String {
        val base = existing?.trimEnd()
        return if (base.isNullOrEmpty()) block else "$base\n\n$block"
    }

    /** Append " (2)", " (3)", ... before the .md extension until [isTaken] returns false. */
    fun dedupeName(fileName: String, isTaken: (String) -> Boolean): String {
        if (!isTaken(fileName)) return fileName
        val dot = fileName.lastIndexOf('.')
        val stem = if (dot >= 0) fileName.substring(0, dot) else fileName
        val ext = if (dot >= 0) fileName.substring(dot) else ""
        var n = 2
        while (true) {
            val candidate = "$stem ($n)$ext"
            if (!isTaken(candidate)) return candidate
            n++
        }
    }

    /**
     * Normalises a user-entered subfolder to a safe relative path: trims each segment and drops
     * blank, "." and ".." segments so a note can never be written outside the chosen vault.
     * Returns "" for the vault root.
     */
    fun sanitizeSubfolder(raw: String): String =
        raw.split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .joinToString("/")

    private fun withMdExtension(name: String): String =
        if (name.endsWith(".md", ignoreCase = true)) name else "$name.md"

    private fun pad2(value: Int): String = value.toString().padStart(2, '0')
}
