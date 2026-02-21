package coredevices.pebble

import co.touchlab.kermit.Logger
import com.eygraber.uri.Uri
import coredevices.analytics.CoreAnalytics
import coredevices.database.AppstoreSourceDao
import coredevices.pebble.account.PebbleAccount
import coredevices.pebble.services.Github
import coredevices.pebble.ui.NavBarRoute
import coredevices.pebble.ui.PebbleNavBarRoutes
import coredevices.util.CoreConfigFlow
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.io.files.Path

expect fun readNameFromContentUri(appContext: AppContext, uri: Uri): String?

expect fun writeFile(appContext: AppContext, uri: Uri): Path?

interface PebbleDeepLinkHandler {
    val initialLockerSync: StateFlow<Boolean>
    val snackBarMessages: SharedFlow<String>
    val navigateToPebbleDeepLink: StateFlow<RealPebbleDeepLinkHandler.PebbleDeepLink?>
    fun handle(uri: Uri?): Boolean
}

class RealPebbleDeepLinkHandler(
    private val pebbleAccount: PebbleAccount,
    private val libPebble: LibPebble,
    private val github: Github,
    private val analytics: CoreAnalytics,
    private val context: AppContext,
    private val appstoreSourceDao: AppstoreSourceDao,
    private val coreConfigFlow: CoreConfigFlow,
) : PebbleDeepLinkHandler {
    private val logger = Logger.withTag("PebbleDeepLinkHandler")
    private val _initialLockerSync = MutableStateFlow(false)
    override val initialLockerSync: StateFlow<Boolean> = _initialLockerSync.asStateFlow()
    private val _snackBarMessages = MutableSharedFlow<String>(extraBufferCapacity = 5)
    override val snackBarMessages: SharedFlow<String> = _snackBarMessages.asSharedFlow()
    private val _navigateToPebbleDeepLink = MutableStateFlow<PebbleDeepLink?>(null)
    override val navigateToPebbleDeepLink = _navigateToPebbleDeepLink.asStateFlow()

    data class PebbleDeepLink(
        val route: NavBarRoute,
        var consumed: Boolean = false,
    )

    override fun handle(uri: Uri?): Boolean {
        uri ?: return false
        return when {
            uri.scheme == "pebble" -> {
                when (uri.host) {
                    CUSTOM_BOOT_CONFIG_URL -> handleBootConfig(uri.path)
                    STORE_URL -> handleAppstore("https://appstore-api.rebble.io/api", uri.path)
                    NAVBAR_URL -> handleNavbar(uri.path)
                    else -> false
                }
            }

            uri.scheme == "https" || uri.scheme == "http" -> {
                when {
                    uri.host == GITHUB_OAUTH_CALLBACK_HOST &&
                            uri.pathSegments.firstOrNull() == GITHUB_OAUTH_CALLBACK_PATH -> handleGithubAuth(
                        uri
                    )

                    else -> false
                }
            }

            uri.scheme == "pebblejs" && uri.host?.startsWith("close") == true -> {
                val data = uri.encodedFragment ?: return false
                libPebble.watches.value
                    .filterIsInstance<ConnectedPebbleDevice>()
                    .firstOrNull { it.currentPKJSSession.value != null }
                    ?.currentPKJSSession
                    ?.value
                    ?.triggerOnWebviewClosed(data) ?: run {
                        logger.w { "No PKJS session found, cannot handle webview close" }
                        return false
                    }
                true
            }

            uri.lastPathSegment?.endsWith(".pbl") ?: false -> handleLanguagePack(uri, uri.lastPathSegment!!)
            uri.lastPathSegment?.endsWith(".pbz") ?: false -> handleFirmware(uri)
            uri.lastPathSegment?.endsWith(".pbw") ?: false -> handleApp(uri)
            uri.scheme == "content" -> handleContentFallback(uri)
            else -> false
        }
    }

    private fun handleContentFallback(uri: Uri): Boolean {
        logger.v { "handleContentFallback() $uri" }
        val name = readNameFromContentUri(context, uri)
        if (name == null) {
            logger.w { "handleContentFallback: couldn't get name for $uri" }
            return false
        }
        logger.d { "filename: $name" }
        return when {
            name.endsWith(".pbl") -> handleLanguagePack(uri, name)
            name.endsWith(".pbz") -> handleFirmware(uri)
            name.endsWith(".pbw") -> handleApp(uri)
            else -> false
        }
    }

    private fun handleLanguagePack(uri: Uri, name: String): Boolean {
        logger.v { "handleLanguagePack() $uri" }
        val file = writeFile(context, uri)
        if (file == null) {
            logger.w { "handleLanguagePack: couldn't write file" }
            _snackBarMessages.tryEmit("Failed to load language pack file")
            return false
        }
        val connectedWatch =
            libPebble.watches.value.filterIsInstance<ConnectedPebble.Language>().firstOrNull()
        if (connectedWatch == null) {
            logger.w { "handleLanguagePack: no connected watch" }
            _snackBarMessages.tryEmit("Failed to load language pack: no connected watch")
            return false
        }
        _snackBarMessages.tryEmit("Installing language pack...")
        connectedWatch.installLanguagePack(file, name)
        return true
    }

    private fun handleFirmware(uri: Uri): Boolean {
        logger.v { "handleFirmware() $uri" }
        val file = writeFile(context, uri)
        if (file == null) {
            logger.w { "handleFirmware: couldn't write file" }
            return false
        }
        libPebble.watches.value.filterIsInstance<ConnectedPebble.Firmware>().forEach {
            it.sideloadFirmware(file)
        }
        return true
    }

    private fun handleApp(uri: Uri): Boolean {
        logger.v { "handleApp() $uri" }
        val file = writeFile(context, uri)
        if (file == null) {
            logger.w { "handleApp: couldn't write file" }
            return false
        }
        GlobalScope.launch {
            libPebble.sideloadApp(file)
        }
        return true
    }

    private fun handleBootConfig(path: String?): Boolean {
        logger.d { "handleBootConfig()" }
        val token = parseTokenFrom(path)
        if (path == null || token == null) {
            logger.w("couldn't find token")
            return false
        }
        GlobalScope.launch {
            pebbleAccount.setToken(token = token, bootUrl = path)
            _initialLockerSync.value = true
            libPebble.requestLockerSync().await()
            libPebble.checkForFirmwareUpdates()
            _initialLockerSync.value = false
            analytics.logEvent("rebble.logged-in")
        }
        return true
    }

    private fun handleAppstore(storeUrl: String, path: String?): Boolean {
        if (path == null) {
            return false
        }
        logger.v { "handleAppstore: $path" }
        GlobalScope.launch {
            val appId = path.removePrefix("/").removeSuffix("/")
            if (coreConfigFlow.value.useNativeAppStoreV2) {
                val store = appstoreSourceDao.getAllEnabledSourcesFlow().firstOrNull()?.find {
                    it.url == storeUrl
                }
                if (store == null) {
                    _snackBarMessages.tryEmit("Failed to find app in enabled feeds")
                    return@launch
                }
                val route = PebbleNavBarRoutes.LockerAppRoute(
                    uuid = null,
                    storedId = appId,
                    storeSource = store.id,
                )
                _navigateToPebbleDeepLink.value = PebbleDeepLink(route)
            } else {
                val route = PebbleNavBarRoutes.AppStoreRoute(
                    appType = null,
                    deepLinkId = appId,
                )
                _navigateToPebbleDeepLink.value = PebbleDeepLink(route)
            }
        }
        return true
    }

    private fun handleNavbar(path: String?): Boolean {
        if (path == null) {
            return false
        }
        logger.v { "handleNavbar: $path" }
        return when (path.removePrefix("/").removeSuffix("/")) {
            "index" -> {
                _navigateToPebbleDeepLink.value = PebbleDeepLink(PebbleNavBarRoutes.IndexRoute)
                true
            }

            else -> false
        }
    }

    private fun handleGithubAuth(uri: Uri): Boolean {
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")
        if (state == null) {
            logger.w("handleGithubAuth: state is null")
            return false
        }
        GlobalScope.launch {
            github.handleRedirect(code, state, error)
        }
        return true
    }

    companion object {
        private const val CUSTOM_BOOT_CONFIG_URL: String = "custom-boot-config-url"
        private const val STORE_URL: String = "appstore"
        private const val NAVBAR_URL: String = "navbar"
        private const val GITHUB_OAUTH_CALLBACK_HOST: String = "cloud.repebble.com"
        private const val GITHUB_OAUTH_CALLBACK_PATH: String = "githubAuth"
        private val TOKEN_REGEX = Regex("access_token=(.*)&t=")
        private val logger = Logger.withTag("PebbleDeepLinkHandler")

        internal fun parseTokenFrom(path: String?): String? {
            if (path == null) {
                logger.w("handleBootConfig: path is null")
                return null
            }
            val bootConfigUrl: String = path.replaceFirst("/", "")
            val token = TOKEN_REGEX.find(bootConfigUrl)?.groups?.get(1)?.value
            return token
        }
    }
}