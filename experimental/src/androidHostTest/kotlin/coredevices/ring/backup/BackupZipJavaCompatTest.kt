package coredevices.ring.backup

import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Backups written by earlier Android versions used java.util.zip, and users unzip backups with
 * standard tools, so both directions must interoperate.
 */
@Ignore // CI JDK is not compatible with our kmpio
class BackupZipJavaCompatTest {
    private val dir = Path(SystemTemporaryDirectory, "backup-zip-compat-${Random.nextLong()}")
    private val zipPath = Path(dir, "test.zip")
    private val payload = Random(7).nextBytes(150 * 1024)

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
    fun readsZipWrittenByJavaUtilZip() = runTest {
        ZipOutputStream(File(zipPath.toString()).outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write("{}".encodeToByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("recordings/x/audio.raw"))
            zos.write(payload)
            zos.closeEntry()
        }

        val reader = BackupZipReader(zipPath)
        reader.open()
        try {
            assertEquals(setOf("manifest.json", "recordings/x/audio.raw"), reader.entryNames)
            assertContentEquals("{}".encodeToByteArray(), reader.readBytes("manifest.json"))
            assertContentEquals(payload, reader.readBytes("recordings/x/audio.raw"))
        } finally {
            reader.close()
        }
    }

    @Test
    fun javaUtilZipReadsOurOutput() = runTest {
        val writer = BackupZipWriter(zipPath)
        writer.open()
        writer.addEntry("manifest.json", "{}".encodeToByteArray())
        writer.addEntry("recordings/x/audio.raw", Buffer().apply { write(payload) })
        writer.close()

        ZipFile(File(zipPath.toString())).use { zip ->
            assertEquals(listOf("manifest.json", "recordings/x/audio.raw"), zip.entries().toList().map { it.name })
            assertContentEquals(payload, zip.getInputStream(zip.getEntry("recordings/x/audio.raw")).use { it.readBytes() })
        }
    }
}
