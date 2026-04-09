package coredevices.ring.agent.builtin_servlets.notes

enum class NoteProvider(val id: Int, val title: String) {
    Builtin(1, "Builtin Notes"),
    Notion(2, "Notion");

    companion object {
        fun fromId(id: Int): NoteProvider? = entries.find { it.id == id }
    }
}