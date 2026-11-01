package coredevices.pebble.services

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.rebble.libpebblecommon.connection.FakeLibPebble
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class PluginSourceLoaderTest {
    private val installed = Uuid.random()
    private val libPebble = object : LibPebble by FakeLibPebble() {
        override suspend fun getAppFile(uuid: Uuid, name: String): String? =
            "// installed $name".takeIf { uuid == installed && name == "plugin.js" }
    }
    private var requests = 0

    private fun loader(status: HttpStatusCode = HttpStatusCode.OK, pbw: ByteArray = zipWith("plugin.js", "// store")) =
        PluginSourceLoader(
            libPebble,
            HttpClient(MockEngine { _ ->
                requests++
                if (status.value in 200..299) respond(pbw) else respondError(status)
            }),
        )

    @Test
    fun installedAppReadsFromItsPbwWithoutDownloading() = runTest {
        assertEquals("// installed plugin.js", loader().load(installed, "https://x/app.pbw", "plugin.js"))
        assertEquals(0, requests)
    }

    @Test
    fun storeAppDownloadsThePbwAndCleansUp() = runTest {
        assertEquals("// store", loader().load(null, "https://x/app.pbw", "plugin.js"))
        assertEquals(1, requests)
        assertTrue(SystemFileSystem.list(SystemTemporaryDirectory).none { it.name.startsWith("plugin_source_") })
    }

    @Test
    fun missingInstalledFileFallsBackToTheStorePbw() = runTest {
        assertEquals("// store", loader().load(Uuid.random(), "https://x/app.pbw", "plugin.js"))
    }

    @Test
    fun nullWhenTheScriptIsAbsentOrTheDownloadFails() = runTest {
        assertNull(loader().load(null, null, "plugin.js"))
        assertNull(loader(pbw = zipWith("other.js", "x")).load(null, "https://x/app.pbw", "plugin.js"))
        assertNull(loader(status = HttpStatusCode.NotFound).load(null, "https://x/app.pbw", "plugin.js"))
        assertTrue(SystemFileSystem.list(SystemTemporaryDirectory).none { it.name.startsWith("plugin_source_") })
    }
}

/** A zip holding one stored (uncompressed) entry. */
private fun zipWith(name: String, content: String): ByteArray {
    val nameBytes = name.encodeToByteArray()
    val data = content.encodeToByteArray()
    val crc = crc32(data)
    val local = le32(0x04034b50) + le16(20) + le16(0) + le16(0) + le16(0) + le16(0) +
        le32(crc) + le32(data.size) + le32(data.size) + le16(nameBytes.size) + le16(0) + nameBytes + data
    val central = le32(0x02014b50) + le16(20) + le16(20) + le16(0) + le16(0) + le16(0) + le16(0) +
        le32(crc) + le32(data.size) + le32(data.size) + le16(nameBytes.size) + le16(0) + le16(0) +
        le16(0) + le16(0) + le32(0) + le32(0) + nameBytes
    val end = le32(0x06054b50) + le16(0) + le16(0) + le16(1) + le16(1) +
        le32(central.size) + le32(local.size) + le16(0)
    return local + central + end
}

private fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
private fun le32(v: Long) = le32(v.toInt())

private fun crc32(data: ByteArray): Long {
    var crc = 0xFFFFFFFFL
    for (b in data) {
        crc = crc xor (b.toLong() and 0xFF)
        repeat(8) { crc = if (crc and 1L != 0L) (crc ushr 1) xor 0xEDB88320L else crc ushr 1 }
    }
    return crc xor 0xFFFFFFFFL
}
