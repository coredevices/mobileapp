package coredevices.pebble.account

import androidx.compose.ui.text.intl.Locale
import co.touchlab.kermit.Logger
import com.eygraber.uri.Uri
import com.russhwolf.settings.Settings
import coredevices.pebble.services.PebbleBootConfigService
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@Serializable
data class BootConfig(
    val config: Config,
) {
    @Serializable
    data class Config(
        val locker: Locker,
        @SerialName("webviews")
        val webViews: Webviews,
        val notifications: Notifications,
        val links: Links,
        val cohorts: Cohorts,
        val voice: Voice? = null,
    ) {
        @Serializable
        data class Locker(
            @SerialName("add_endpoint")
            val addEndpoint: String,
            @SerialName("get_endpoint")
            val getEndpoint: String,
            @SerialName("remove_endpoint")
            val removeEndpoint: String,
        )

        @Serializable
        data class Webviews(
            @SerialName("appstore/watchapps")
            val appStoreWatchApps: String,
            @SerialName("appstore/watchfaces")
            val appStoreWatchFaces: String,
            @SerialName("appstore/application")
            val appStoreApplication: String,
        )

        @Serializable
        data class Notifications(
            @SerialName("ios_app_icons")
            val iosAppIcons: String,
        )

        @Serializable
        data class Links(
            @SerialName("authentication/me")
            val authenticationMe: String,
            @SerialName("users/me")
            val usersMe: String,
        )

        @Serializable
        data class Cohorts(
            val endpoint: String
        )

        @Serializable
        data class Voice(
            @SerialName("first_party_uuids")
            val firstPartyUuids: List<String> = emptyList(),
            val languages: List<Language> = emptyList(),
        ) {
            @Serializable
            data class Language(
                val endpoint: String,
                @SerialName("four_char_locale")
                val fourCharLocale: String,
                @SerialName("six_char_locale")
                val sixCharLocale: String,
            )
        }
    }
}

private fun String.replacePlaceholder(placeholder: String, replacement: String): String =
    replace("\$\$$placeholder\$\$", replacement)

fun BootConfig.Config.Notifications.iconUrlFor(bundleId: String, size: Int): String =
    iosAppIcons.replace("http://", "https://")
        .replacePlaceholder("bundle_id", bundleId)
        .replacePlaceholder("size", "$size")

expect fun bootConfigPlatform(): String

interface BootConfigProvider {
    suspend fun setUrl(url: String?)
    fun getUrl(): String?
    suspend fun getBootConfig(): BootConfig?
}

class RealBootConfigProvider(
    private val settings: Settings,
    private val bootConfigService: PebbleBootConfigService,
) : BootConfigProvider {
    private val logger = Logger.withTag("PebbleAccount")

    override suspend fun setUrl(url: String?) {
        logger.d("setUrl $url")
        if (url != null) {
            settings.putString(BOOTCONFIG_URL_KEY, url)
        } else {
            settings.remove(BOOTCONFIG_URL_KEY)
        }
        // Force to fetch it again (just in case that doesn't work right away)
        settings.remove(BOOTCONFIG_KEY)
        fetch()
    }

    override fun getUrl(): String? {
        val baseUrl = settings.getStringOrNull(BOOTCONFIG_URL_KEY)?.removePrefix("/")
        if (baseUrl == null) {
            logger.i("fetch: no URL")
            return null
        }
        val uri = Uri.parse(baseUrl)
        return uri.buildUpon().apply {
            val platform = bootConfigPlatform()
            if (platform == "android") {
                appendPath(platform)
                appendPath("v3") // bootconfig version?
                appendPath("999") // app version code
            }
            appendQueryParameter("locale", Locale.current.toLanguageTag())
            appendQueryParameter("app_version", "v9.9.9") // app version name
        }.build().toString()
    }

    private suspend fun fetch() {
        val url = getUrl() ?: return
        val bootConfig = bootConfigService.getBootConfig(url)
        logger.d("got bootconfig: $bootConfig")
        settings.putString(BOOTCONFIG_KEY, Json.encodeToString(bootConfig))
        settings.putLong(BOOTCONFIG_FETCHED_AT_KEY, Clock.System.now().toEpochMilliseconds())
    }

    private fun isStale(): Boolean {
        val fetchedAt = settings.getLongOrNull(BOOTCONFIG_FETCHED_AT_KEY) ?: return true
        val age = Clock.System.now().toEpochMilliseconds() - fetchedAt
        return age >= REFRESH_INTERVAL.inWholeMilliseconds || age < 0
    }

    private fun applyOverrides(config: BootConfig): BootConfig {
        val oldFacesUrl = Url(config.config.webViews.appStoreWatchFaces)
        val oldAppsUrl = Url(config.config.webViews.appStoreWatchApps)
        var newConfig = config
        if (oldFacesUrl.host == "apps.rebble.io") {
            newConfig = newConfig.copy(
                config = newConfig.config.copy(
                    webViews = newConfig.config.webViews.copy(
                        appStoreWatchFaces = URLBuilder(oldFacesUrl).apply {
                            host = "apps.repebble.com"
                        }.buildString().replaceFirst("?", "?&")
                    )
                )
            )
        }
        if (oldAppsUrl.host == "apps.rebble.io") {
            newConfig = newConfig.copy(
                config = newConfig.config.copy(
                    webViews = newConfig.config.webViews.copy(
                        appStoreWatchApps = URLBuilder(oldAppsUrl).apply {
                            host = "apps.repebble.com"
                        }.buildString().replaceFirst("?", "?&")
                    )
                )
            )
        }
        return newConfig
    }

    private fun loadFromSettings(): BootConfig? {
        return settings.getStringOrNull(BOOTCONFIG_KEY)?.let { json ->
            try {
                applyOverrides(Json.decodeFromString(json))
            } catch (e: SerializationException) {
                logger.w("error decoding bootconfig", e)
                null
            }
        }
    }

    override suspend fun getBootConfig(): BootConfig? {
        if (!settings.hasKey(BOOTCONFIG_KEY)) {
            fetch()
        } else if (isStale()) {
            // Refresh in case tokens/URLs (e.g. the Rebble ASR access tokens baked into
            // voice.languages[].endpoint) have rotated. Best-effort: fall back to the cached
            // version if the network is unavailable.
            try {
                fetch()
            } catch (e: Exception) {
                logger.w(e) { "Failed to refresh stale boot config, using cached version" }
            }
        }
        val config = loadFromSettings()
        if (config != null) {
            return config
        }
        // might be that we added a new key which wasn't previously persisted; refresh from
        // server (maybe figure out how we can persistt he raw response, instead of
        // re-serializing it?)
        fetch()
        return loadFromSettings()
    }

    companion object {
        private val BOOTCONFIG_URL_KEY = "boot_config_url"
        private val BOOTCONFIG_KEY = "boot_config"
        private val BOOTCONFIG_FETCHED_AT_KEY = "boot_config_fetched_at"
        private val REFRESH_INTERVAL = 24.hours
    }
}
