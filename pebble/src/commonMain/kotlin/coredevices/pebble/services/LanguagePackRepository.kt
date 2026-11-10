package coredevices.pebble.services

import androidx.compose.ui.text.intl.Locale
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.pebble.firmware.isCoreDevice
import coredevices.util.sha256
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.endpointmanager.InstalledLanguagePack
import io.rebble.libpebblecommon.locker.getLockerPBWCacheDirectory
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json

class LanguagePackRepository(
    private val json: Json,
    private val httpClient: HttpClient,
    private val settings: Settings,
    private val appContext: AppContext,
) {
    private val logger = Logger.withTag("LanguagePackRepository")

    private val manifestJson = Json(json) { ignoreUnknownKeys = true }
    private val _manifest = MutableStateFlow<LanguageManifest?>(null)
    /** Null until [init] has loaded a persisted manifest, or one has been fetched. */
    internal val manifest: StateFlow<LanguageManifest?> = _manifest.asStateFlow()
    private val publishedPacks get() = manifest.value?.languages.orEmpty()
    private val legacyPacks get() = manifest.value?.legacyLanguages.orEmpty().map { it.asLanguagePack() }

    /** Loads the persisted manifest, fetching one if none has been persisted yet. */
    suspend fun init() {
        val persisted = withContext(Dispatchers.IO) {
            settings.getStringOrNull(MANIFEST_CACHE_KEY)?.let {
                runCatching { decodeLanguageManifest(manifestJson, it) }.getOrNull()
            }
        }
        _manifest.compareAndSet(null, persisted)
        if (manifest.value == null) syncManifest()
    }

    /** Returns false if the manifest couldn't be fetched; the persisted one is kept. */
    suspend fun syncManifest(): Boolean = withContext(Dispatchers.IO) {
        try {
            val text = withTimeoutOrNull(10.seconds) {
                downloadBytes(LANGUAGE_MANIFEST_URL, 1_048_576).decodeToString()
            }
            if (text == null) {
                logger.w { "Timed out fetching language manifest" }
                return@withContext false
            }
            val fetched = decodeLanguageManifest(manifestJson, text)
            settings.putString(MANIFEST_CACHE_KEY, text)
            _manifest.value = fetched
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Failed to fetch language manifest" }
            false
        }
    }

    internal fun languagePacksForWatch(manifest: LanguageManifest, watch: ConnectedPebbleDevice) =
        selectLanguagePacks(manifest.legacyLanguages.map { it.asLanguagePack() }, manifest.languages,
            watch.watchInfo.platform, Locale.current.toLanguageTag())

    suspend fun prepareDownload(pack: LanguagePack): Path = withContext(Dispatchers.IO) {
        withTimeout(30.seconds) {
            val bytes = downloadBytes(pack.file, pack.size)
            pack.verify(bytes)
            val directory = getLockerPBWCacheDirectory(appContext)
            SystemFileSystem.createDirectories(directory)
            val path = Path(directory, "language.pbl")
            SystemFileSystem.sink(path).buffered().use { it.write(bytes) }
            path
        }
    }

    private suspend fun downloadBytes(url: String, maxSize: Long): ByteArray =
        httpClient.prepareGet(url).execute { response ->
            check(response.status.isSuccess()) { "Language download failed" }
            val bytes = response.bodyAsChannel().readRemaining(maxSize + 1).readByteArray()
            require(bytes.size.toLong() <= maxSize) { "Language download exceeds expected size" }
            bytes
        }

    /**
     * Friendly display name for a language pack read back from the watch, which only reports the
     * raw ISO locale + version.
     */
    fun displayNameForInstalled(installed: InstalledLanguagePack): String {
        // The watch reports no source identifier, so never infer update status from its version.
        val locale = canonicalLocale(installed.isoLocal)
        val legacy = legacyPacks.filter { canonicalLocale(it.isoLocal) == locale }
        val pack = publishedPacks.firstOrNull { canonicalLocale(it.locale) == locale }?.asLanguagePack()
            ?: legacy.firstOrNull { it.version == installed.version }
            ?: legacy.firstOrNull()
        return pack?.let { "${it.localName} (${it.name}) v${installed.version}" }
            ?: "${installed.isoLocal} (v${installed.version})"
    }
}

internal fun WatchHardwarePlatform.languagePackPlatform(): WatchHardwarePlatform = when {
    isCoreDevice() -> WatchHardwarePlatform.PEBBLE_SILK
    else -> this
}

data class LanguagePack(
    val isoLocal: String,
    val file: String,
    val hardware: String?,
    val localName: String,
    val name: String,
    val version: Int,
    val id: String,
    val sha256: String,
    val size: Long,
    val published: PublishedLanguagePack? = null,
) {
    internal fun verify(bytes: ByteArray) {
        require(bytes.size.toLong() == size && sha256(bytes).toHexString() == sha256) {
            "Language pack verification failed. Please try again."
        }
    }
}

fun LanguagePack.description(): String = when {
    isEnglishFontPack(isoLocal) -> "English with additional fonts"
    published != null -> published.completionLabel()
    else -> "From the Rebble archive"
}

enum class LanguagePackGroup(val title: String) {
    Translations("Translations"),
    FontPacks("Font packs"),
    Community("Older community packs"),
}

/** Built-in English makes the archived en_US pack redundant. */
internal fun groupLanguagePacks(packs: List<LanguagePack>): List<Pair<LanguagePackGroup, List<LanguagePack>>> {
    val grouped = packs.filter { canonicalLocale(it.isoLocal) != "en_us" }.groupBy {
        when {
            isEnglishFontPack(it.isoLocal) -> LanguagePackGroup.FontPacks
            it.published != null -> LanguagePackGroup.Translations
            else -> LanguagePackGroup.Community
        }
    }
    return LanguagePackGroup.entries.mapNotNull { group -> grouped[group]?.let { group to it } }
}

fun LanguagePack.canContribute() = published != null && !isEnglishFontPack(isoLocal)

fun LanguagePack.displayName() = published?.let {
    "$localName ($name) · ${it.updatedAt.take(10)}"
} ?: "$localName ($name) v$version"
