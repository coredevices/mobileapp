package coredevices.util.models

import coredevices.util.CommonBuildKonfig

/**
 * A speech-to-text model the app can download and select between.
 *
 * [version] is the Cactus packaging/format tag on the model's Hugging Face repo
 * (e.g. "v1.10") and must be one the bundled Cactus runtime understands, so all
 * STT models are pinned to the same format version as the default model.
 */
data class SttModelSpec(
    val slug: String,
    val version: String,
    val quantization: String,
    val sizeInMB: Int,
    val displayName: String,
    val description: String,
    val englishOnly: Boolean,
)

/**
 * The set of STT models the app exposes for selection. Replaces the previous
 * reliance on the single [CommonBuildKonfig.CACTUS_STT_MODEL] build constant for
 * everything except the *default* model.
 */
object SttModelCatalog {
    private const val HF_BASE = "https://huggingface.co/Cactus-Compute"

    val models: List<SttModelSpec> = listOf(
        // Default / recommended. Version is driven by the build config so the two
        // can't drift apart.
        SttModelSpec(
            slug = CommonBuildKonfig.CACTUS_STT_MODEL,
            version = CommonBuildKonfig.CACTUS_STT_WEIGHTS_VERSION,
            quantization = "int8",
            sizeInMB = 670,
            displayName = "Parakeet 0.6B",
            description = "Multilingual · 25 languages",
            englishOnly = false,
        ),
        // English-only opt-in. Same architecture and footprint as the default
        // (parakeet-tdt-0.6b), materially better English accuracy on noisy or
        // hard speech. Pinned to the same weights format version as the default.
        SttModelSpec(
            slug = "parakeet-tdt-0.6b-v2",
            version = "v1.10",
            quantization = "int8",
            sizeInMB = 670,
            displayName = "Parakeet 0.6B (English)",
            description = "English only · best English accuracy",
            englishOnly = true,
        ),
    )

    /** The model loaded when the user has not explicitly chosen one. */
    val defaultSlug: String get() = CommonBuildKonfig.CACTUS_STT_MODEL

    fun forSlug(slug: String): SttModelSpec? = models.firstOrNull { it.slug == slug }

    /** Falls back to the default STT weights version for unknown slugs (e.g. legacy models). */
    fun versionFor(slug: String): String =
        forSlug(slug)?.version ?: CommonBuildKonfig.CACTUS_STT_WEIGHTS_VERSION

    fun downloadUrl(slug: String): String {
        val spec = forSlug(slug)
        val version = spec?.version ?: CommonBuildKonfig.CACTUS_STT_WEIGHTS_VERSION
        val quant = spec?.quantization ?: "int8"
        return "$HF_BASE/$slug/resolve/$version/weights/${slug.lowercase()}-$quant.zip"
    }
}
