package coredevices.ring.agent.integrations.obsidian

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObsidianNoteFormatterTest {

    // 2026-06-18 14:05
    private val y = 2026; private val mo = 6; private val d = 18; private val h = 14; private val mi = 5

    @Test
    fun timestampedFileNameIsZeroPaddedInSubfolder() {
        val cfg = config(ObsidianMode.TIMESTAMPED_FILES, subfolder = "Index Inbox")
        val write = ObsidianNoteFormatter.plan(cfg, "hello", y, mo, d, h, mi)
        assertTrue(write is ObsidianWrite.NewFile)
        write as ObsidianWrite.NewFile
        assertEquals("Index Inbox/2026-06-18 1405.md", write.fileName)
    }

    @Test
    fun timestampedFileHasFrontmatterThenBody() {
        val cfg = config(ObsidianMode.TIMESTAMPED_FILES, subfolder = "Index Inbox")
        val write = ObsidianNoteFormatter.plan(cfg, "buy milk", y, mo, d, h, mi) as ObsidianWrite.NewFile
        assertEquals(
            "---\ncreated: 2026-06-18T14:05\ntags: [index]\n---\n\nbuy milk\n",
            write.content,
        )
    }

    @Test
    fun emptySubfolderWritesToVaultRoot() {
        val cfg = config(ObsidianMode.TIMESTAMPED_FILES, subfolder = "  ")
        val write = ObsidianNoteFormatter.plan(cfg, "x", y, mo, d, h, mi) as ObsidianWrite.NewFile
        assertEquals("2026-06-18 1405.md", write.fileName)
    }

    @Test
    fun mainNoteAppendsToFixedFile() {
        val cfg = config(ObsidianMode.MAIN_NOTE)
        val write = ObsidianNoteFormatter.plan(cfg, "note text", y, mo, d, h, mi)
        assertTrue(write is ObsidianWrite.Append)
        write as ObsidianWrite.Append
        assertEquals("Pebble Index.md", write.fileName)
        assertEquals("## 2026-06-18 14:05\n\nnote text\n", write.block)
    }

    @Test
    fun namedNoteAppendsToTargetAddingMdExtension() {
        val cfg = config(ObsidianMode.NAMED_NOTE, targetNote = "Daily")
        val write = ObsidianNoteFormatter.plan(cfg, "t", y, mo, d, h, mi) as ObsidianWrite.Append
        assertEquals("Daily.md", write.fileName)
    }

    @Test
    fun namedNoteKeepsExistingMdExtension() {
        val cfg = config(ObsidianMode.NAMED_NOTE, targetNote = "Daily.md")
        val write = ObsidianNoteFormatter.plan(cfg, "t", y, mo, d, h, mi) as ObsidianWrite.Append
        assertEquals("Daily.md", write.fileName)
    }

    @Test
    fun subfolderPathTraversalIsStripped() {
        // ".." segments must never let a note escape the chosen vault.
        assertEquals("Inbox", ObsidianNoteFormatter.sanitizeSubfolder("../Inbox"))
        assertEquals("a/b", ObsidianNoteFormatter.sanitizeSubfolder("a/../b"))
        assertEquals("x", ObsidianNoteFormatter.sanitizeSubfolder("/x/"))
        assertEquals("", ObsidianNoteFormatter.sanitizeSubfolder(".."))
        assertEquals("Index Inbox", ObsidianNoteFormatter.sanitizeSubfolder("Index Inbox"))
    }

    @Test
    fun timestampedModeSanitizesSubfolderInPath() {
        val cfg = config(ObsidianMode.TIMESTAMPED_FILES, subfolder = "../escape")
        val write = ObsidianNoteFormatter.plan(cfg, "x", y, mo, d, h, mi) as ObsidianWrite.NewFile
        assertEquals("escape/2026-06-18 1405.md", write.fileName)
    }

    @Test
    fun mergeAppendInsertsBlankLineBetweenExistingAndBlock() {
        val merged = ObsidianNoteFormatter.mergeAppend("# Title\nold", "## new\n\nbody\n")
        assertEquals("# Title\nold\n\n## new\n\nbody\n", merged)
    }

    @Test
    fun mergeAppendIntoNullOrEmptyJustReturnsBlock() {
        assertEquals("## new\n\nbody\n", ObsidianNoteFormatter.mergeAppend(null, "## new\n\nbody\n"))
        assertEquals("## new\n\nbody\n", ObsidianNoteFormatter.mergeAppend("", "## new\n\nbody\n"))
    }

    @Test
    fun dedupeNameAppendsCounterUntilFree() {
        val taken = setOf("Index Inbox/2026-06-18 1405.md", "Index Inbox/2026-06-18 1405 (2).md")
        val free = ObsidianNoteFormatter.dedupeName("Index Inbox/2026-06-18 1405.md") { it in taken }
        assertEquals("Index Inbox/2026-06-18 1405 (3).md", free)
    }

    @Test
    fun dedupeNameReturnsOriginalWhenFree() {
        val free = ObsidianNoteFormatter.dedupeName("a/b.md") { false }
        assertEquals("a/b.md", free)
    }

    private fun config(
        mode: ObsidianMode,
        targetNote: String = "",
        subfolder: String = "Index Inbox",
    ) = ObsidianConfig(mode = mode, targetNote = targetNote, subfolder = subfolder)
}
