package coredevices.pebble.services

import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertFalse

class LanguagePackRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val published = PublishedLanguagePack(
        locale = "de_DE", name = "German", nativeName = "Deutsch", version = 1,
        updatedAt = "2026-09-17T17:28:12+02:00",
        url = "https://github.com/coredevices/pebbleos-translations/releases/download/packs-test/de_DE.pbl",
        sha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        size = 3, translatedStrings = 753, totalStrings = 777,
        translationUrl = "https://translate.repebble.com/projects/pebbleos/watch/",
    )

    private val legacyPack = LegacyLanguagePack(
        locale = "bg_BG", name = "Bulgarian", nativeName = "Български", version = 1,
        hardware = null, url = "https://example.com/Bulgarian-v1.pbl", sha256 = published.sha256, size = 3,
    )

    private fun legacy(locale: String, hardware: String? = WatchHardwarePlatform.PEBBLE_SILK.revision, id: String = locale) = LanguagePack(
        isoLocal = locale, file = "https://example.com/$id.pbl",
        hardware = hardware, localName = locale, name = locale, version = 38, id = id,
        sha256 = published.sha256, size = 3,
    )

    @Test
    fun newSourceReplacesHigherLegacyVersionAndRetainsUnmigratedLanguages() {
        val packs = selectLanguagePacks(
            listOf(legacy("de_DE"), legacy("he_IL"), legacy("ja_JP"), legacy("en_CN")),
            listOf(published), WatchHardwarePlatform.CORE_ASTERIX, "de-DE",
        )
        assertEquals(listOf("de_DE", "en_CN", "he_IL", "ja_JP"), packs.map { it.isoLocal })
        assertEquals(1, packs.first().version)
        assertEquals(published, packs.first().published)
        assertNull(packs.last().published)
    }

    @Test
    fun oldWatchesPreferLegacyAndOnlyAddUnmatchedPublishedLocales() {
        val hardware = WatchHardwarePlatform.PEBBLE_SNOWY_DVT
        val de = legacy("de_DE", hardware.revision)
        val packs = selectLanguagePacks(listOf(de, legacy("zh_TW", null)),
            listOf(published, published.copy(locale = "zh_Hant"), published.copy(locale = "fr_FR")), hardware, "de-DE")
        assertEquals(listOf("de_DE", "fr_FR", "zh_TW"), packs.map { it.isoLocal })
        assertEquals(de, packs.first())
        assertEquals("fr_FR", packs[1].published?.locale)
    }

    @Test
    fun renamedLocalesMatchWhatOldWatchesReport() {
        assertEquals(canonicalLocale("bg_BG"), canonicalLocale("bg"))
        assertEquals(canonicalLocale("zh_Hant"), canonicalLocale("zh_TW"))
        assertEquals(canonicalLocale("ar_AA"), canonicalLocale("ar_SA"))
    }

    @Test
    fun legacyManifestPacksMapToVerifiableCommunityPacks() {
        val pack = legacyPack.asLanguagePack()
        assertNull(pack.published)
        assertEquals("legacy:bg_BG:null:${published.sha256}", pack.id)
        assertEquals(LanguagePackGroup.Community, groupLanguagePacks(listOf(pack)).single().first)
        assertFalse(pack.canContribute())
        pack.verify("abc".encodeToByteArray())
        assertFailsWith<IllegalArgumentException> { pack.verify("abd".encodeToByteArray()) }
    }

    @Test
    fun groupsPacksAndDropsArchivedEnglish() {
        val fonts = published.copy(locale = "en_SA").asLanguagePack()
        val translation = published.asLanguagePack()
        val community = legacy("ar_SA")
        val groups = groupLanguagePacks(listOf(legacy("en_US"), community, fonts, translation, legacy("en_TW")))
        assertEquals(
            listOf(
                LanguagePackGroup.Translations to listOf(translation),
                LanguagePackGroup.FontPacks to listOf(fonts, legacy("en_TW")),
                LanguagePackGroup.Community to listOf(community),
            ),
            groups,
        )
    }

    @Test
    fun catalanAliasDoesNotProduceTwoChoices() {
        val packs = selectLanguagePacks(listOf(legacy("ca")),
            listOf(published.copy(locale = "ca_ES")), WatchHardwarePlatform.CORE_ASTERIX, "ca-ES")
        assertEquals(listOf("ca_ES"), packs.map { it.isoLocal })
    }

    @Test
    fun emptyPublishedListRetainsLegacyCatalog() {
        val old = listOf(legacy("de_DE"), legacy("he_IL"))
        assertEquals(old, selectLanguagePacks(old, emptyList(), WatchHardwarePlatform.CORE_ASTERIX, "de-DE"))
    }

    @Test
    fun legacyHardwareRetainsExactHardwarePreference() {
        val hardware = WatchHardwarePlatform.PEBBLE_SNOWY_DVT
        val exact = legacy("de_DE", hardware.revision, "exact")
        val packs = selectLanguagePacks(listOf(exact, legacy("de_DE", null, "generic")),
            listOf(published), hardware, "de-DE")
        assertEquals(listOf(exact), packs)
    }

    @Test
    fun validatesManifestAndRejectsUnsupportedOrUnsafeCatalogs() {
        val manifest = LanguageManifest(1, "packs-test", listOf(published), listOf(legacyPack, legacyPack.copy(hardware = "silk")))
        assertEquals(manifest, decodeLanguageManifest(json, json.encodeToString(manifest)))
        for (bad in listOf(
            manifest.copy(schemaVersion = 2),
            manifest.copy(languages = listOf(published, published.copy(locale = "de-DE"))),
            manifest.copy(languages = listOf(published.copy(version = 0))),
            manifest.copy(languages = listOf(published.copy(translatedStrings = 778))),
            manifest.copy(languages = listOf(published.copy(url = "https://example.com/pack.pbl"))),
            manifest.copy(languages = listOf(published.copy(translationUrl = "https://translate.repebble.com.evil.org/"))),
            manifest.copy(legacyLanguages = listOf(legacyPack, legacyPack)),
            manifest.copy(legacyLanguages = listOf(legacyPack.copy(url = "http://example.com/pack.pbl"))),
            manifest.copy(legacyLanguages = listOf(legacyPack.copy(sha256 = "abc"))),
        )) {
            assertFailsWith<IllegalArgumentException> { decodeLanguageManifest(json, json.encodeToString(bad)) }
        }
    }

    @Test
    fun verifiesDownloadedSizeAndHashBeforeInstallation() {
        val pack = published.asLanguagePack()
        pack.verify("abc".encodeToByteArray())
        assertFailsWith<IllegalArgumentException> { pack.verify("abcd".encodeToByteArray()) }
        assertFailsWith<IllegalArgumentException> { pack.verify("abd".encodeToByteArray()) }
    }

    @Test
    fun englishFontPacksHaveNoPercentageOrContributionAction() {
        for (locale in listOf("en_IL", "en_SA", "en_TW", "en_CN", "en_MY")) {
            val pack = published.copy(locale = locale).asLanguagePack()
            assertEquals("English with additional fonts", pack.description())
            assertEquals("English with additional fonts", pack.published!!.completionLabel())
            assertFalse(pack.canContribute())
            assertEquals("English with additional fonts", legacy(locale).description())
        }
    }

    @Test
    fun displaysLastChangeDateAndCompletionWithoutLegacyVersionComparison() {
        assertEquals("Deutsch (German) · 2026-09-17", published.asLanguagePack().displayName())
        assertEquals("96% translated", published.completionLabel())
        assertEquals("No source strings", published.copy(totalStrings = 0, translatedStrings = 0).completionLabel())
    }
}
