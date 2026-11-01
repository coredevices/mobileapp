package coredevices.ring.backup

import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@Ignore
class BackupZipTest {
    private val dir = Path(SystemTemporaryDirectory, "backup-zip-test-${Random.nextLong()}")
    private val zipPath = Path(dir, "test.zip")

    @BeforeTest
    fun setUp() {
        SystemFileSystem.createDirectories(dir)
    }

    @AfterTest
    fun tearDown() {
        SystemFileSystem.list(dir).forEach { SystemFileSystem.delete(it, false) }
        SystemFileSystem.delete(dir, false)
    }

    @Test
    fun roundTripsByteAndStreamedEntries() = runTest {
        val small = "hello".encodeToByteArray()
        // Larger than one write chunk so the streamed path is exercised across chunk boundaries
        val large = Random(42).nextBytes(300 * 1024)
        val largePath = Path(dir, "large.bin")
        SystemFileSystem.sink(largePath).buffered().use { it.write(large) }

        val writer = BackupZipWriter(zipPath)
        writer.open()
        writer.addEntry("manifest.json", small)
        SystemFileSystem.source(largePath).buffered().use { writer.addEntry("recordings/abc/audio.raw", it) }
        writer.addEntry("recordings/abc/empty-ish.raw", Buffer().apply { write(byteArrayOf(1, 2, 3)) })
        writer.close()

        val reader = BackupZipReader(zipPath)
        reader.open()
        try {
            assertEquals(
                setOf("manifest.json", "recordings/abc/audio.raw", "recordings/abc/empty-ish.raw"),
                reader.entryNames,
            )
            assertContentEquals(small, reader.readBytes("manifest.json"))
            assertContentEquals(byteArrayOf(1, 2, 3), reader.readBytes("recordings/abc/empty-ish.raw"))
            val sink = Buffer()
            reader.readEntry("recordings/abc/audio.raw", sink)
            assertContentEquals(large, sink.readByteArray())
        } finally {
            reader.close()
        }
    }
}
