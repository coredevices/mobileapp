package coredevices.util.models

import coredevices.util.deleteRecursive
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromoteSingleRootDirTest {
    private val dir = Path(SystemTemporaryDirectory, "promote-test-${Random.nextLong()}").also {
        SystemFileSystem.createDirectories(it)
    }

    @AfterTest
    fun cleanup() = deleteRecursive(dir)

    private fun touch(path: Path) {
        path.parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.sink(path).buffered().use { it.write(byteArrayOf(1)) }
    }

    @Test
    fun hoistsSingleRootDir() {
        touch(Path(dir, "root/config.txt"))
        touch(Path(dir, "root/components/manifest.json"))

        promoteSingleRootDir(dir)

        assertTrue(SystemFileSystem.exists(Path(dir, "config.txt")))
        assertTrue(SystemFileSystem.exists(Path(dir, "components/manifest.json")))
        assertFalse(SystemFileSystem.exists(Path(dir, "root")))
    }

    @Test
    fun leavesFlatLayoutUntouched() {
        touch(Path(dir, "config.txt"))
        touch(Path(dir, "weights.bin"))

        promoteSingleRootDir(dir)

        assertTrue(SystemFileSystem.exists(Path(dir, "config.txt")))
        assertTrue(SystemFileSystem.exists(Path(dir, "weights.bin")))
        assertEquals(2, SystemFileSystem.list(dir).size)
    }

    @Test
    fun leavesSingleFileUntouched() {
        touch(Path(dir, "config.txt"))

        promoteSingleRootDir(dir)

        assertTrue(SystemFileSystem.exists(Path(dir, "config.txt")))
    }

    @Test
    fun leavesEmptyDirUntouched() {
        promoteSingleRootDir(dir)

        assertEquals(0, SystemFileSystem.list(dir).size)
    }
}
