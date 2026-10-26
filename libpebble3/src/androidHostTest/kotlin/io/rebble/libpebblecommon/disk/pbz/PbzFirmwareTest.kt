package io.rebble.libpebblecommon.disk.pbz;

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.readString
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import kotlin.test.assertEquals

class PbzFirmwareTest {
    @Test
    fun thinPbz() {
        val file = loadResourceToFile("normal_asterix_test.pbz")
        val pbz = PbzFirmware(file)
        assertEquals(1, pbz.manifests.size)
        assertEquals("asterix", pbz.manifests[0].getFirmware().buffered().readString())
        assertEquals("asterix-resources", pbz.manifests[0].getResources()!!.buffered().readString())
    }

    @Test
    fun fatPbz() {
        val file = loadResourceToFile("normal_obelix_test.pbz")
        val pbz = PbzFirmware(file)
        assertEquals(2, pbz.manifests.size)
        assertEquals("obelix0", pbz.manifests[0].getFirmware().buffered().readString())
        assertEquals("obelix-resources0", pbz.manifests[0].getResources()!!.buffered().readString())
        assertEquals("obelix1", pbz.manifests[1].getFirmware().buffered().readString())
        assertEquals("obelix-resources1", pbz.manifests[1].getResources()!!.buffered().readString())
    }

    private fun loadResourceToFile(resourceName: String): Path {
        val inputStream = javaClass.classLoader.getResourceAsStream(resourceName)
            ?: throw AssertionError("Resource not found: $resourceName")

        val tempFile = File.createTempFile("test_file", ".pbz")
        tempFile.deleteOnExit()

        // Copy the resource content to the temporary file
        FileOutputStream(tempFile).use { outputStream ->
            inputStream.use { input ->
                input.copyTo(outputStream)
            }
        }

        val path = Path(tempFile.absolutePath)
        return path
    }
}