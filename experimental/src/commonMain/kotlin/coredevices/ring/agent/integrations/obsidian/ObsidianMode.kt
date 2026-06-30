package coredevices.ring.agent.integrations.obsidian

/** Where each note is written inside the chosen vault folder. */
enum class ObsidianMode(val id: Int) {
    /** Append every note to a single "Pebble Index.md" note in the vault root. */
    MAIN_NOTE(0),
    /** Append every note to an existing note the user picked from the vault. */
    NAMED_NOTE(1),
    /** Write one timestamped .md file per note into a user-named subfolder. */
    TIMESTAMPED_FILES(2);

    companion object {
        fun fromId(id: Int): ObsidianMode = entries.firstOrNull { it.id == id } ?: TIMESTAMPED_FILES
    }
}
