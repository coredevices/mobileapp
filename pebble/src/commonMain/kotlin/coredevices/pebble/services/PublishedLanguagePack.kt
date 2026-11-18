package coredevices.pebble.services

import coredevices.pebble.firmware.isCoreDevice
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
    val legacyLanguages: List<LegacyLanguagePack> = emptyList(),
)

@Serializable
internal data class LegacyLanguagePack(
    val locale: String,
    val name: String,
    val nativeName: String,
    val version: Int,
    val hardware: String? = null,
    val url: String,
    val sha256: String,
    val size: Long,
) {
    fun asLanguagePack() = LanguagePack(
        isoLocal = locale,
        file = url,
        hardware = hardware,
        localName = nativeName,
        name = name,
        version = version,
        id = "legacy:$locale:$hardware:$sha256",
        sha256 = sha256,
        size = size,
    )
}

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
        hardware = null,
        localName = nativeName,
        name = name,
        version = version,
        id = "pebbleos:$locale:$sha256",
        sha256 = sha256,
        size = size,
        published = this,
    )

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
        requireValidPack(pack.locale, pack.name, pack.nativeName, pack.version, pack.size, pack.sha256)
        require(pack.totalStrings >= 0 && pack.translatedStrings in 0..pack.totalStrings)
        require(pack.url == "https://github.com/coredevices/pebbleos-translations/releases/download/${manifest.release}/${pack.locale}.pbl")
        require(pack.translationUrl.startsWith("https://translate.repebble.com/"))
        Instant.parse(pack.updatedAt)
    }
    require(manifest.legacyLanguages.map { it.asLanguagePack().id }.distinct().size == manifest.legacyLanguages.size)
    for (pack in manifest.legacyLanguages) {
        requireValidPack(pack.locale, pack.name, pack.nativeName, pack.version, pack.size, pack.sha256)
        require(pack.url.startsWith("https://"))
    }
    return manifest
}

private fun requireValidPack(locale: String, name: String, nativeName: String, version: Int, size: Long, sha256: String) {
    require(Regex("[A-Za-z][A-Za-z0-9_@.-]*").matches(locale))
    require(name.isNotBlank() && nativeName.isNotBlank())
    require(version in 1..65535 && size in 1..16_777_216)
    require(Regex("[a-f0-9]{64}").matches(sha256))
}

internal fun canonicalLocale(locale: String): String = when (val normalized = locale.replace('-', '_').lowercase()) {
    "ca" -> "ca_es"
    "bg" -> "bg_bg"
    "zh_tw" -> "zh_hant"
    "ar_sa" -> "ar_aa"
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
    val current = published.map { it.asLanguagePack() }
    // Replacement is by locale, not version: legacy versions use a different sequence.
    // Old firmware may use strings missing from current packs, so legacy wins there.
    val (preferred, fallback) = if (platform.isCoreDevice()) current to compatibleLegacy else compatibleLegacy to current
    val preferredLocales = preferred.map { canonicalLocale(it.isoLocal) }.toSet()
    return (preferred + fallback.filter { canonicalLocale(it.isoLocal) !in preferredLocales })
        .sortedWith(compareByDescending<LanguagePack> { canonicalLocale(it.isoLocal) == canonicalLocale(preferredLocale) }
            .thenByDescending { it.isoLocal.take(2).equals(preferredLocale.take(2), ignoreCase = true) }
            .thenBy { it.name })
}

internal fun isEnglishFontPack(locale: String): Boolean =
    canonicalLocale(locale).startsWith("en_") && canonicalLocale(locale) != "en_us"
