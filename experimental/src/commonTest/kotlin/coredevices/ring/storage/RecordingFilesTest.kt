package coredevices.ring.storage

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordingFilesTest {
    private val root = Path(SystemTemporaryDirectory, "recording-files-test-${Random.nextLong()}")
    private val cacheDir = Path(root, "cache")
    private val dataDir = Path(root, "data")
    private val files = RecordingFiles(cacheDir, dataDir)

    @BeforeTest
    fun setUp() {
        SystemFileSystem.createDirectories(cacheDir)
        SystemFileSystem.createDirectories(dataDir)
    }

    @AfterTest
    fun tearDown() {
        for (dir in listOf(cacheDir, dataDir)) {
            SystemFileSystem.list(dir).forEach { SystemFileSystem.delete(it, false) }
            SystemFileSystem.delete(dir, false)
        }
        SystemFileSystem.delete(root, false)
    }

    private fun write(path: Path, bytes: ByteArray) {
        SystemFileSystem.sink(path).buffered().use { it.write(bytes) }
    }

    private fun read(path: Path): ByteArray =
        SystemFileSystem.source(path).buffered().use { it.readByteArray() }

    @Test
    fun localPathIsNullWithoutAnyCopy() {
        assertNull(files.localPath("rec"))
    }

    @Test
    fun localPathFallsBackToPersistent() {
        write(files.persistentPath("rec"), byteArrayOf(1))
        assertEquals(files.persistentPath("rec"), files.localPath("rec"))
    }

    @Test
    fun localPathPrefersCache() {
        write(files.persistentPath("rec"), byteArrayOf(1))
        write(files.cachePath("rec"), byteArrayOf(2))
        assertEquals(files.cachePath("rec"), files.localPath("rec"))
    }

    @Test
    fun moveToCacheRelocatesPersistentCopy() {
        write(files.persistentPath("rec"), byteArrayOf(1, 2, 3))
        files.moveToCache("rec")
        assertFalse(SystemFileSystem.exists(files.persistentPath("rec")))
        assertContentEquals(byteArrayOf(1, 2, 3), read(files.cachePath("rec")))
    }

    @Test
    fun moveToCacheWithoutPersistentCopyKeepsCache() {
        write(files.cachePath("rec"), byteArrayOf(9))
        files.moveToCache("rec")
        assertContentEquals(byteArrayOf(9), read(files.cachePath("rec")))
    }

    @Test
    fun deletePersistentLeavesCacheAndToleratesMissing() {
        write(files.cachePath("rec"), byteArrayOf(9))
        write(files.persistentPath("rec"), byteArrayOf(1))
        files.deletePersistent("rec")
        files.deletePersistent("rec")
        assertFalse(SystemFileSystem.exists(files.persistentPath("rec")))
        assertTrue(SystemFileSystem.exists(files.cachePath("rec")))
    }
}
