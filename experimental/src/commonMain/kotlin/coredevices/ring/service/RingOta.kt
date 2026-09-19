package coredevices.ring.service

import co.touchlab.kermit.Logger
import coredevices.api.EngDashArtifact
import coredevices.api.EngDashOtaApi
import coredevices.api.ensureVersionPrefix
import coredevices.haversine.KMPHaversineSatellite
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.util.sha1
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RingOta(
    private val otaApi: EngDashOtaApi,
    private val httpClient: HttpClient,
    private val scope: RecordingBackgroundScope,
    private val otaCachePath: Path = getCachedFirmwareDirectory(),
    private val clock: Clock = Clock.System
) {
    private val logger = Logger.withTag("RingOta")
    private val CACHED_FW_PREFIX = "ring_cached_fw"
    private var lastRefresh: Instant? = null
    private val cacheMutex = Mutex()

    suspend fun resetCache() = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            SystemFileSystem.list(otaCachePath).forEach { file ->
                if (file.name.startsWith(CACHED_FW_PREFIX)) {
                    SystemFileSystem.delete(file, false)
                }
            }
        }
    }

    private suspend fun getCacheCopy(hardwareVersion: String): String? = withContext(Dispatchers.IO) {
        val cachedFile = Path(otaCachePath, "${CACHED_FW_PREFIX}_${hardwareVersion}.json")
        cacheMutex.withLock {
            if ((SystemFileSystem.metadataOrNull(cachedFile)?.size ?: 0) > 0) {
                return@withContext SystemFileSystem.source(cachedFile).buffered().readString()
            } else {
                return@withContext null
            }
        }
    }

    private suspend fun saveCache(data: String, hardwareVersion: String) = withContext(Dispatchers.IO) {
        val newHash = sha1((data+hardwareVersion).toByteArray())
        val cachedFile = Path(otaCachePath, "${CACHED_FW_PREFIX}_${hardwareVersion}.json")
        cacheMutex.withLock {
            val current = try {
                SystemFileSystem.source(cachedFile).buffered().readString()
            } catch (e: FileNotFoundException) {
                null
            }
            val currentHash = current?.let { sha1((it+hardwareVersion).toByteArray()) }
            // Only write if we need to
            if (currentHash == null || !currentHash.contentEquals(newHash)) {
                SystemFileSystem.sink(cachedFile).buffered().use { sink ->
                    sink.writeString(data)
                }
            }
        }
    }

    private suspend fun downloadArtifact(artifact: EngDashArtifact): String = withContext(Dispatchers.IO) {
        val resp = httpClient.get(Url(artifact.url))
        check(resp.status.isSuccess()) { "Ring fw download failed: ${resp.status}" }
        return@withContext resp.bodyAsText()
    }

    suspend fun getLatestFirmware(device: KMPHaversineSatellite): String? {
        val state = device.state.value ?: run {
            logger.e { "Could not get ring state for update request" }
            return null
        }
        val mac = state.serialNumber.filter { it.isLetterOrDigit() }.uppercase()
        val serial = state.programmedSerialNumber?.takeIf { it.isNotBlank() }
        val hardwareVersion = state.hardwareVersion
        val currentFirmwareVersion = state.firmwareVersion
        return getLatestFirmware(
            serial ?: mac,
            mac,
            hardwareVersion,
            currentFirmwareVersion
        )
    }

    suspend fun getLatestFirmware(
        serial: String,
        mac: String,
        hardwareVersion: Pair<Int, Int>,
        currentFirmwareVersion: String,
    ): String? {
        val hardwareVersionFmt = "index01_${hardwareVersion.first}-${hardwareVersion.second}"

        val cached = getCacheCopy(hardwareVersionFmt)
        // Don't bother checking again at all if we recently refreshed
        val timeSinceRefresh = clock.now() - (lastRefresh ?: Instant.DISTANT_PAST)
        if (cached != null && timeSinceRefresh < 30.minutes) {
            return cached
        } else if (cached == null && timeSinceRefresh < 1.minutes) { // Rate limit ourselves since haversine will request on every advertisement
            return null
        }
        logger.d { "Last refresh was $timeSinceRefresh ago. Attempting cache refresh" }

        lastRefresh = clock.now()
        // Errors / up to date can return cached file to make sure we're always able to restore quickly & offline
        val result = try {
            otaApi.getLatestFirmware(
                deviceSerial = serial,
                deviceMac = mac,
                hardwareVersion = hardwareVersionFmt,
                // Don't send version if cached == null so we can build the cache
                currentVersion = if (!currentFirmwareVersion.startsWith("0.0") && cached != null) {
                    ensureVersionPrefix(currentFirmwareVersion)
                } else null
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Error checking for updates from eng-dash: ${e.message}" }
            return cached ?: throw e
        }
        if (result == null) {
            logger.d { "OTA response was up to date/no-op" }
            return cached
        }
        logger.d {"ota: $result"}
        val artifact = result.artifacts.firstOrNull() ?: run {
            logger.e { "OTA response had no artifacts" }
            return cached
        }
        return try {
            val data = downloadArtifact(artifact)
            scope.launch { // Save/update cache
                saveCache(data, hardwareVersionFmt)
            }
            return data
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) {"Error downloading artifact: ${e.message}"}
            cached ?: throw e
        }
    }
}

expect fun getCachedFirmwareDirectory(): Path