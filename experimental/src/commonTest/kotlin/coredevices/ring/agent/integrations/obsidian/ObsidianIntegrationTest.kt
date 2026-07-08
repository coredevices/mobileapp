package coredevices.ring.agent.integrations.obsidian

import PlatformUiContext
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ObsidianIntegrationTest {

    private val tz = TimeZone.of("UTC")
    // 2026-06-18 14:05 UTC
    private val now = LocalDateTime(2026, 6, 18, 14, 5).toInstant(tz)
    private val clock = object : Clock { override fun now(): Instant = now }

    private class FakeVault(var access: Boolean = true) : ObsidianVault {
        val files = mutableMapOf<String, String>()
        override suspend fun pickFolder(uiContext: PlatformUiContext): VaultRef? = VaultRef("h", "vault")
        override suspend fun hasAccess(handle: String): Boolean = access
        override suspend fun listMarkdownFiles(handle: String, subfolder: String): List<String> =
            files.keys.mapNotNull { full ->
                val slash = full.lastIndexOf('/')
                val dir = if (slash >= 0) full.substring(0, slash) else ""
                val leaf = if (slash >= 0) full.substring(slash + 1) else full
                if (dir == subfolder && leaf.endsWith(".md")) leaf else null
            }
        override suspend fun readFile(handle: String, name: String): String? = files[name]
        override suspend fun writeFile(handle: String, name: String, content: String): Boolean {
            files[name] = content; return true
        }
        override suspend fun releaseAccess(handle: String) {}
    }

    private fun integration(vault: FakeVault, prefs: ObsidianPreferences) =
        ObsidianIntegration(vault, prefs, clock, tz)

    @Test
    fun createNoteReturnsNullWhenNoVaultSelected() = runTest {
        val prefs = ObsidianPreferences(MapSettings())
        assertNull(integration(FakeVault(), prefs).createNote("hi"))
    }

    @Test
    fun createNoteReturnsNullWhenAccessLost() = runTest {
        val prefs = ObsidianPreferences(MapSettings()).apply { setVault("h", "vault") }
        assertNull(integration(FakeVault(access = false), prefs).createNote("hi"))
    }

    @Test
    fun timestampedModeWritesNewFileWithFrontmatter() = runTest {
        val vault = FakeVault()
        val prefs = ObsidianPreferences(MapSettings()).apply {
            setVault("h", "vault"); setMode(ObsidianMode.TIMESTAMPED_FILES); setSubfolder("Index Inbox")
        }
        val id = integration(vault, prefs).createNote("buy milk")
        assertEquals("Index Inbox/2026-06-18 1405.md", id)
        assertEquals(
            "---\ncreated: 2026-06-18T14:05\ntags: [index]\n---\n\nbuy milk\n",
            vault.files["Index Inbox/2026-06-18 1405.md"],
        )
    }

    @Test
    fun timestampedModeDeDuplicatesOnCollision() = runTest {
        val vault = FakeVault()
        vault.files["Index Inbox/2026-06-18 1405.md"] = "existing"
        val prefs = ObsidianPreferences(MapSettings()).apply {
            setVault("h", "vault"); setMode(ObsidianMode.TIMESTAMPED_FILES); setSubfolder("Index Inbox")
        }
        val id = integration(vault, prefs).createNote("second")
        assertEquals("Index Inbox/2026-06-18 1405 (2).md", id)
    }

    @Test
    fun timestampedModeDeDuplicatesAtRootWhenNoSubfolder() = runTest {
        val vault = FakeVault()
        vault.files["2026-06-18 1405.md"] = "existing"
        val prefs = ObsidianPreferences(MapSettings()).apply {
            setVault("h", "vault"); setMode(ObsidianMode.TIMESTAMPED_FILES); setSubfolder("")
        }
        val id = integration(vault, prefs).createNote("second")
        assertEquals("2026-06-18 1405 (2).md", id)
    }

    @Test
    fun mainNoteModeAppendsToExistingFile() = runTest {
        val vault = FakeVault()
        vault.files["Pebble Index.md"] = "# Pebble Index\nfirst"
        val prefs = ObsidianPreferences(MapSettings()).apply {
            setVault("h", "vault"); setMode(ObsidianMode.MAIN_NOTE)
        }
        val id = integration(vault, prefs).createNote("second")
        assertEquals("Pebble Index.md", id)
        assertEquals(
            "# Pebble Index\nfirst\n\n## 2026-06-18 14:05\n\nsecond\n",
            vault.files["Pebble Index.md"],
        )
    }

    @Test
    fun mainNoteModeCreatesFileWhenAbsent() = runTest {
        val vault = FakeVault()
        val prefs = ObsidianPreferences(MapSettings()).apply {
            setVault("h", "vault"); setMode(ObsidianMode.MAIN_NOTE)
        }
        integration(vault, prefs).createNote("hello")
        assertEquals("## 2026-06-18 14:05\n\nhello\n", vault.files["Pebble Index.md"])
    }

    @Test
    fun namedNoteModeAppendsToTarget() = runTest {
        val vault = FakeVault()
        val prefs = ObsidianPreferences(MapSettings()).apply {
            setVault("h", "vault"); setMode(ObsidianMode.NAMED_NOTE); setTargetNote("Daily.md")
        }
        integration(vault, prefs).createNote("entry")
        assertTrue(vault.files.containsKey("Daily.md"))
    }

    @Test
    fun namedNoteModeWithBlankTargetWritesNothing() = runTest {
        val vault = FakeVault()
        val prefs = ObsidianPreferences(MapSettings()).apply {
            setVault("h", "vault"); setMode(ObsidianMode.NAMED_NOTE); setTargetNote("")
        }
        assertNull(integration(vault, prefs).createNote("entry"))
        assertTrue(vault.files.isEmpty())
    }
}
