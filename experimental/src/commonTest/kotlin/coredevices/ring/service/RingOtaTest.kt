package coredevices.ring.service

import coredevices.api.EngDashArtifact
import coredevices.api.EngDashLatestResult
import coredevices.api.EngDashOtaApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RingOtaTest {

    private class FakeEngDashOtaApi(
        private val result: EngDashLatestResult? = null,
        private val error: Exception? = null,
    ) : EngDashOtaApi {
        data class Call(
            val deviceSerial: String,
            val hardwareVersion: String,
            val currentVersion: String?,
            val deviceMac: String?,
        )

        val calls = mutableListOf<Call>()

        override suspend fun getLatestFirmware(
            deviceSerial: String,
            hardwareVersion: String,
            currentVersion: String?,
            deviceMac: String?
        ): EngDashLatestResult? {
            calls += Call(deviceSerial, hardwareVersion, currentVersion, deviceMac)
            error?.let { throw it }
            return result
        }
    }

    private class FakeClock(var now: Instant = Instant.fromEpochMilliseconds(0)) : Clock {
        override fun now() = now
    }

    private val firmwareBody = "firmware-blob"
    private val artifactUrl = "https://example.com/fw/index01.bin"
    private val defaultHwVersion = "index01_1-0"

    private val cacheDir = Path(SystemTemporaryDirectory, "ring-ota-test-${Random.nextLong()}")
    private val clock = FakeClock()
    private val bgJob = Job()
    private val bgScope = RecordingBackgroundScope(CoroutineScope(Dispatchers.Default + bgJob))

    @BeforeTest
    fun setUp() {
        SystemFileSystem.createDirectories(cacheDir)
    }

    @AfterTest
    fun tearDown() = runTest {
        bgJob.cancelAndJoin()
        SystemFileSystem.list(cacheDir).forEach { SystemFileSystem.delete(it, false) }
        SystemFileSystem.delete(cacheDir, false)
    }

    private fun latestResult(artifacts: List<EngDashArtifact> = listOf(EngDashArtifact(artifactUrl))) =
        EngDashLatestResult(version = "v1.3.0", artifacts = artifacts)

    private fun httpClient(status: HttpStatusCode = HttpStatusCode.OK) = HttpClient(MockEngine { request ->
        if (status.value in 200..299) {
            assertEquals(artifactUrl, request.url.toString())
            respond(firmwareBody)
        } else {
            respondError(status)
        }
    })

    private fun ringOta(api: EngDashOtaApi, client: HttpClient = httpClient()) =
        RingOta(api, client, bgScope, cacheDir, clock)

    private fun cacheFile(hardwareVersion: String = defaultHwVersion) =
        Path(cacheDir, "ring_cached_fw_${hardwareVersion}.json")

    private fun seedCache(content: String, hardwareVersion: String = defaultHwVersion) {
        SystemFileSystem.sink(cacheFile(hardwareVersion)).buffered().use { it.writeString(content) }
    }

    private fun readCache(hardwareVersion: String = defaultHwVersion): String? {
        val file = cacheFile(hardwareVersion)
        if ((SystemFileSystem.metadataOrNull(file)?.size ?: 0L) == 0L) return null
        return SystemFileSystem.source(file).buffered().readString()
    }

    private suspend fun awaitCacheWrites() = bgJob.children.toList().joinAll()

    @Test
    fun `downloads artifact and passes device parameters through`() = runTest {
        val api = FakeEngDashOtaApi(result = latestResult())

        val firmware = ringOta(api).getLatestFirmware(
            serial = "SN123",
            mac = "AABBCCDDEEFF",
            hardwareVersion = 2 to 1,
            currentFirmwareVersion = "1.2.3",
        )

        assertEquals(firmwareBody, firmware)
        assertEquals(
            listOf(
                FakeEngDashOtaApi.Call(
                    deviceSerial = "SN123",
                    hardwareVersion = "index01_2-1",
                    // No cached copy yet, so the version is withheld to force a full response
                    currentVersion = null,
                    deviceMac = "AABBCCDDEEFF",
                )
            ),
            api.calls,
        )
    }

    @Test
    fun `saves downloaded artifact to cache`() = runTest {
        val api = FakeEngDashOtaApi(result = latestResult())

        ringOta(api).getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3")
        awaitCacheWrites()

        assertEquals(firmwareBody, readCache())
    }

    @Test
    fun `sends prefixed current version when cache exists`() = runTest {
        seedCache("cached-fw")
        val api = FakeEngDashOtaApi(result = latestResult())

        ringOta(api).getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3")

        assertEquals("v1.2.3", api.calls.single().currentVersion)
    }

    @Test
    fun `keeps existing v prefix on current version`() = runTest {
        seedCache("cached-fw")
        val api = FakeEngDashOtaApi(result = latestResult())

        ringOta(api).getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "v1.2.3")

        assertEquals("v1.2.3", api.calls.single().currentVersion)
    }

    @Test
    fun `omits current version when firmware reports 0_0`() = runTest {
        seedCache("cached-fw")
        val api = FakeEngDashOtaApi(result = latestResult())

        ringOta(api).getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "0.0.1")

        assertNull(api.calls.single().currentVersion)
    }

    @Test
    fun `returns null when no update is available and no cache`() = runTest {
        val api = FakeEngDashOtaApi(result = null)

        assertNull(ringOta(api).getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3"))
    }

    @Test
    fun `returns cache when no update is available`() = runTest {
        seedCache("cached-fw")
        val api = FakeEngDashOtaApi(result = null)

        assertEquals("cached-fw", ringOta(api).getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3"))
    }

    @Test
    fun `throws when update check fails and no cache`() = runTest {
        val api = FakeEngDashOtaApi(error = Exception("boom"))
        var downloadAttempted = false
        val client = HttpClient(MockEngine {
            downloadAttempted = true
            respond(firmwareBody)
        })

        assertFailsWith<Exception> {
            ringOta(api, client).getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3")
        }
        assertEquals(false, downloadAttempted)
    }

    @Test
    fun `returns cache when update check fails`() = runTest {
        seedCache("cached-fw")
        val api = FakeEngDashOtaApi(error = Exception("boom"))

        assertEquals("cached-fw", ringOta(api).getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3"))
    }

    @Test
    fun `returns null when response has no artifacts and no cache`() = runTest {
        val api = FakeEngDashOtaApi(result = latestResult(artifacts = emptyList()))

        assertNull(ringOta(api).getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3"))
    }

    @Test
    fun `returns cache when response has no artifacts`() = runTest {
        seedCache("cached-fw")
        val api = FakeEngDashOtaApi(result = latestResult(artifacts = emptyList()))

        assertEquals("cached-fw", ringOta(api).getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3"))
    }

    @Test
    fun `throws when artifact download fails and no cache`() = runTest {
        val api = FakeEngDashOtaApi(result = latestResult())
        val client = httpClient(status = HttpStatusCode.InternalServerError)

        assertFailsWith<IllegalStateException> {
            ringOta(api, client).getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3")
        }
    }

    @Test
    fun `returns cache when artifact download fails`() = runTest {
        seedCache("cached-fw")
        val api = FakeEngDashOtaApi(result = latestResult())
        val client = httpClient(status = HttpStatusCode.InternalServerError)

        assertEquals("cached-fw", ringOta(api, client).getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3"))
    }

    @Test
    fun `serves fresh cache without hitting the api`() = runTest {
        val api = FakeEngDashOtaApi(result = latestResult())
        val ota = ringOta(api)

        ota.getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3")
        awaitCacheWrites()

        clock.now += 29.minutes
        assertEquals(firmwareBody, ota.getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3"))
        assertEquals(1, api.calls.size)
    }

    @Test
    fun `refreshes stale cache`() = runTest {
        val api = FakeEngDashOtaApi(result = latestResult())
        val ota = ringOta(api)

        ota.getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3")
        awaitCacheWrites()

        clock.now += 31.minutes
        assertEquals(firmwareBody, ota.getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3"))
        assertEquals(2, api.calls.size)
    }

    @Test
    fun `rate limits update checks when there is no cache`() = runTest {
        val api = FakeEngDashOtaApi(result = null)
        val ota = ringOta(api)

        ota.getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3")
        clock.now += 30.seconds
        assertNull(ota.getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3"))
        assertEquals(1, api.calls.size)

        clock.now += 1.minutes
        ota.getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3")
        assertEquals(2, api.calls.size)
    }

    @Test
    fun `treats empty cache file as no cache`() = runTest {
        seedCache("")
        val api = FakeEngDashOtaApi(result = null)

        assertNull(ringOta(api).getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3"))
    }

    @Test
    fun `caches are keyed by hardware version`() = runTest {
        seedCache("cached-fw", hardwareVersion = "index01_2-1")
        val api = FakeEngDashOtaApi(result = null)

        assertNull(ringOta(api).getLatestFirmware("SN123", "AABBCCDDEEFF", 1 to 0, "1.2.3"))
    }

    @Test
    fun `resetCache deletes cached firmware`() = runTest {
        seedCache("cached-fw")
        seedCache("cached-fw-2", hardwareVersion = "index01_2-1")

        ringOta(FakeEngDashOtaApi()).resetCache()

        assertNull(readCache())
        assertNull(readCache("index01_2-1"))
    }
}
