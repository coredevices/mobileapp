package coredevices.util.models

import coredevices.util.CommonBuildKonfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SttModelCatalogTest {

    @Test
    fun catalog_exposes_both_parakeet_models() {
        val slugs = SttModelCatalog.models.map { it.slug }
        assertTrue("parakeet-tdt-0.6b-v3" in slugs, "default v3 model missing")
        assertTrue("parakeet-tdt-0.6b-v2" in slugs, "English v2 opt-in model missing")
    }

    @Test
    fun default_slug_matches_build_constant() {
        assertEquals(CommonBuildKonfig.CACTUS_STT_MODEL, SttModelCatalog.defaultSlug)
        assertNotNull(SttModelCatalog.forSlug(SttModelCatalog.defaultSlug))
    }

    @Test
    fun default_model_version_tracks_build_config() {
        val default = SttModelCatalog.forSlug(SttModelCatalog.defaultSlug)
        assertNotNull(default)
        assertEquals(CommonBuildKonfig.CACTUS_STT_WEIGHTS_VERSION, default.version)
    }

    @Test
    fun english_model_is_english_only_and_pinned_to_default_format_version() {
        val en = SttModelCatalog.forSlug("parakeet-tdt-0.6b-v2")
        assertNotNull(en)
        assertTrue(en.englishOnly)
        assertEquals(CommonBuildKonfig.CACTUS_STT_WEIGHTS_VERSION, en.version)
        assertEquals("int8", en.quantization)
    }

    @Test
    fun download_url_is_built_from_slug_version_and_quantization() {
        assertEquals(
            "https://huggingface.co/Cactus-Compute/parakeet-tdt-0.6b-v2/resolve/v1.10/weights/parakeet-tdt-0.6b-v2-int8.zip",
            SttModelCatalog.downloadUrl("parakeet-tdt-0.6b-v2"),
        )
    }

    @Test
    fun unknown_slug_falls_back_to_default_weights_version() {
        assertEquals(
            CommonBuildKonfig.CACTUS_STT_WEIGHTS_VERSION,
            SttModelCatalog.versionFor("whisper-small"),
        )
    }
}
