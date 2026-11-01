package coredevices.pebble.services

import coredevices.pebble.firmware.isCoreDevice
import coredevices.util.sha256
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

internal const val LANGUAGE_MANIFEST_URL =
    "https://github.com/coredevices/pebbleos-translations/releases/latest/download/manifest.json"
internal const val MANIFEST_CACHE_KEY = "pebble.languageManifest.v1"

@Serializable
internal data class LanguageManifest(
    val schemaVersion: Int,
    val release: String,
    val languages: List<PublishedLanguagePack>,
)

@Serializable
data class PublishedLanguagePack(
    val locale: String,
    val name: String,
    val nativeName: String,
    val version: Int,
    val updatedAt: String,
    val url: String,
    val sha256: String,
    val size: Long,
    val translatedStrings: Int,
    val totalStrings: Int,
    val translationUrl: String,
) {
    internal fun asLanguagePack() = LanguagePack(
        isoLocal = locale,
        file = url,
        firmwareVersion = "",
        hardware = null,
        localName = nativeName,
        name = name,
        version = version,
        id = "pebbleos:$locale:$sha256",
        published = this,
    )

    internal fun verify(bytes: ByteArray) {
        require(bytes.size.toLong() == size && sha256(bytes).toHexString() == sha256) {
            "Language pack verification failed. Please try again."
        }
    }

    fun completionLabel(): String = when {
        isEnglishFontPack(locale) -> "English with additional fonts"
        totalStrings == 0 -> "No source strings"
        else -> "${translatedStrings.toLong() * 100 / totalStrings}% translated"
    }
}

internal fun decodeLanguageManifest(json: Json, text: String): LanguageManifest {
    val manifest = json.decodeFromString<LanguageManifest>(text)
    require(manifest.schemaVersion == 1) { "Unsupported language catalog" }
    require(Regex("packs-[A-Za-z0-9._-]+").matches(manifest.release))
    require(manifest.languages.map { canonicalLocale(it.locale) }.distinct().size == manifest.languages.size)
    for (pack in manifest.languages) {
        require(Regex("[A-Za-z][A-Za-z0-9_@.-]*").matches(pack.locale))
        require(pack.name.isNotBlank() && pack.nativeName.isNotBlank())
        require(pack.version in 1..65535 && pack.size in 1..16_777_216)
        require(Regex("[a-f0-9]{64}").matches(pack.sha256))
        require(pack.totalStrings >= 0 && pack.translatedStrings in 0..pack.totalStrings)
        require(pack.url == "https://github.com/coredevices/pebbleos-translations/releases/download/${manifest.release}/${pack.locale}.pbl")
        require(pack.translationUrl.startsWith("https://translate.repebble.com/"))
        Instant.parse(pack.updatedAt)
    }
    return manifest
}

internal fun canonicalLocale(locale: String): String = when (val normalized = locale.replace('-', '_').lowercase()) {
    "ca" -> "ca_es"
    else -> normalized
}

internal fun selectLanguagePacks(
    legacy: List<LanguagePack>,
    published: List<PublishedLanguagePack>,
    platform: WatchHardwarePlatform,
    preferredLocale: String,
): List<LanguagePack> {
    val revision = platform.revision
    val fallbackRevision = platform.languagePackPlatform().revision
    val exactLocales = legacy.filter { it.hardware == revision }.map { canonicalLocale(it.isoLocal) }.toSet()
    val compatibleLegacy = legacy.filter {
        when (it.hardware) {
            revision -> true
            null, fallbackRevision -> canonicalLocale(it.isoLocal) !in exactLocales
            else -> false
        }
    }
    val current = if (platform.isCoreDevice()) published.map { it.asLanguagePack() } else emptyList()
    val currentLocales = current.map { canonicalLocale(it.isoLocal) }.toSet()
    // Source replacement is by locale, not version: legacy versions use a different sequence.
    return (current + compatibleLegacy.filter { canonicalLocale(it.isoLocal) !in currentLocales })
        .sortedWith(compareByDescending<LanguagePack> { canonicalLocale(it.isoLocal) == canonicalLocale(preferredLocale) }
            .thenByDescending { it.isoLocal.take(2).equals(preferredLocale.take(2), ignoreCase = true) }
            .thenBy { it.name })
}

internal fun isEnglishFontPack(locale: String): Boolean =
    canonicalLocale(locale).startsWith("en_") && canonicalLocale(locale) != "en_us"
