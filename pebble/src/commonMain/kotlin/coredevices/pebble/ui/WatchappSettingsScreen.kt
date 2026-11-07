package coredevices.pebble.ui

import CoreNav
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import co.touchlab.kermit.Logger
import com.multiplatform.webview.request.RequestInterceptor
import com.multiplatform.webview.request.WebRequest
import com.multiplatform.webview.request.WebRequestInterceptResult
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.PlatformWebViewParams
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewFactoryParam
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.back
import coredevices.pebble.PebbleDeepLinkHandler
import coredevices.pebble.RealPebbleDeepLinkHandler
import coredevices.pebble.config.ConfigPageBridge
import coredevices.pebble.config.ConfigPageClosedListener
import coredevices.pebble.config.ConfigPageSessions
import coredevices.pebble.config.ConfigScriptIds
import coredevices.pebble.config.PluginConfigFileAccess
import coredevices.pebble.config.VisibleConfigPage
import coredevices.pebble.config.performWebSignIn
import coredevices.pebble.config.webSignInSupported
import coredevices.util.isDebugBuild
import coredevices.util.rememberUiContext
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.permissions.PermissionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private const val URL_DATA_PREFIX = "data:text/html;charset=utf-8,"

// The settings URL can be a large data: URI (hundreds of KB of HTML) which would cause
// TransactionTooLargeException if passed as a navigation route argument. Store it here
// in memory instead, keyed by watchIdentifier.
internal object WatchappSettingsUrlCache {
    private val urls = mutableMapOf<String, String>()
    fun put(watchIdentifier: String, url: String) { urls[watchIdentifier] = url }
    fun get(watchIdentifier: String): String? = urls[watchIdentifier]
    fun remove(watchIdentifier: String) { urls.remove(watchIdentifier) }
}
internal expect fun webViewFactory(
    params: WebViewFactoryParam,
    uuid: Uuid,
    /** Null leaves the page with no `Pebble` object, which is what a plain webview should be. */
    bridge: ConfigPageBridge?,
    /** Blocking; whatever the page loads or connects to is checked against the app's permissions. */
    mayLoad: (String) -> Boolean,
    network: ConfigPageNetwork,
): NativeWebView

/** Limits what a config page may connect to, on top of [webViewFactory]'s per-request checks. */
internal expect class ConfigPageNetwork {
    /** False if the limits couldn't be put in place, so the page mustn't open. */
    val enforced: Boolean
}

/**
 * WebKit content-blocker url-filters allowing [domain] and its subdomains for [scheme] (e.g.
 * `^https?://`). The host must end at a port or path, so `https://domain:x@evil.com/` doesn't match.
 */
internal fun configPageUrlFilter(scheme: String, domain: String) =
    "$scheme([^/:@]+\\.)?${domain.replace(".", "\\.")}(:[0-9]+)?/"

/** [domains] each cover their subdomains; null leaves the page unrestricted. */
internal expect suspend fun configPageNetwork(uuid: Uuid, domains: List<String>?): ConfigPageNetwork

internal expect suspend fun restoreLocalStorage(webView: NativeWebView)
internal expect fun persistLocalStorage(webView: NativeWebView)

@Composable
internal expect fun rememberPlatformWebViewParams(mayLoad: (String) -> Boolean): PlatformWebViewParams?

private val logger = Logger.withTag("WatchappSettingsScreen")

@Composable
fun WatchappSettingsScreen(
    coreNav: CoreNav,
    /** What is being configured: a watchapp, a plugin, or a pbw that ships both. */
    uuid: String,
    title: String,
) {
    val libPebble = koinInject<LibPebble>()
    // Opened from outside the locker (a plugin picked as an Index destination), nothing is cached.
    val resolvedUrl by produceState(initialValue = WatchappSettingsUrlCache.get(uuid), uuid) {
        if (value == null) {
            value = runCatching { Uuid.parse(uuid) }.getOrNull()
                ?.let { libPebble.appConfigPageUrl(it) } ?: ""
        }
    }
    val url = resolvedUrl?.let(::normalizeWatchappSettingsUrl) ?: return
    val closedListener: ConfigPageClosedListener = koinInject()
    DisposableEffect(uuid) {
        onDispose {
            WatchappSettingsUrlCache.remove(uuid)
            runCatching { Uuid.parse(uuid) }.getOrNull()?.let(closedListener::onConfigPageClosed)
        }
    }
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val sessions: ConfigPageSessions = koinInject()
        val session by remember(uuid, title) { sessions.sessionsFor(uuid, title) }
            .collectAsState(null)
        // Nothing left to configure — the watchapp stopped and there is no plugin behind it.
        LaunchedEffect(session) {
            if (session?.isOrphaned == true) {
                logger.d { "No scripts left for $uuid; closing settings" }
                withContext(Dispatchers.Main) { coreNav.goBack() }
            }
        }
        val webViewUuid = remember(uuid) { runCatching { Uuid.parse(uuid) }.getOrNull() }
        val mayLoad = remember(webViewUuid) {
            { url: String -> webViewUuid != null && runBlocking { libPebble.mayLoadInConfigPage(webViewUuid, url) } }
        }
        val platformParams = rememberPlatformWebViewParams(mayLoad)
        // Fixed when the page opens: a domain approved later applies from the next visit.
        val network by produceState<ConfigPageNetwork?>(null, webViewUuid) {
            value = webViewUuid?.let { configPageNetwork(it, libPebble.configPageDomains(it)) }
        }
        val state = rememberWebViewState(url) {
            androidWebSettings.domStorageEnabled = true
            iOSWebSettings.isInspectable = true
        }
        val deepLinks: PebbleDeepLinkHandler = koinInject()
        val scope = rememberCoroutineScope()
        // Leaves the page for the linked app's page in the locker. Called on the main thread, which
        // persistLocalStorage blocks on.
        val openStoreAppLink = { url: String ->
            val isStoreLink = RealPebbleDeepLinkHandler.storeAppLink(url) != null
            if (isStoreLink) {
                scope.launch(Dispatchers.Default) {
                    runCatching { state.nativeWebView }.getOrNull()?.let { persistLocalStorage(it) }
                    deepLinks.openStoreAppLink(url)
                }
            }
            isStoreLink
        }
        val interceptor = remember {
            SettingsRequestInterceptor(
                openStoreAppLink = openStoreAppLink,
                onSuccess = { data ->
                    runCatching { state.nativeWebView }.getOrNull()?.let { persistLocalStorage(it) }
                    session?.onPageClosed(data)
                    withContext(Dispatchers.Main) {
                        coreNav.goBack()
                    }
                },
                onError = {
                    runCatching { state.nativeWebView }.getOrNull()?.let { persistLocalStorage(it) }
                    withContext(Dispatchers.Main) {
                        coreNav.goBack()
                    }
                }
            )
        }
        val navigator = rememberWebViewNavigator(requestInterceptor = interceptor)
        // One bridge for the life of the screen: it is installed when the WebView is built,
        // before any page has loaded, and picks up scripts as they resolve.
        val bridge = remember { if (sessions.enabled) ConfigPageBridge(scope) else null }
        val uiContext = rememberUiContext()
        val appContext: AppContext = koinInject()
        val debugBuild = remember { appContext.isDebugBuild() }
        val webSignInApp by remember(webViewUuid) {
            webViewUuid?.let { libPebble.getLockerApp(it) } ?: flowOf(null)
        }.collectAsState(null)
        LaunchedEffect(bridge, webSignInApp, uiContext, debugBuild) {
            val ctx = uiContext
            // Only offer web sign-in where the platform can isolate its cookie jar.
            bridge?.webSignIn = if (ctx != null && webSignInSupported() &&
                webSignInApp?.mayUseWebSignIn(debugBuild) == true) {
                { request -> performWebSignIn(ctx, request) }
            } else {
                null
            }
        }
        var linkRequest by remember { mutableStateOf<LinkRequest?>(null) }
        LaunchedEffect(bridge) {
            bridge?.openUrl = { url ->
                if (openStoreAppLink(url)) {
                    true
                } else if (linkRequest != null) {
                    // One at a time, so a page can't swap the url while the user is reading it.
                    false
                } else {
                    val answer = CompletableDeferred<Boolean>()
                    linkRequest = LinkRequest(url, Url(url).host, answer)
                    answer.await()
                }
            }
        }
        linkRequest?.let { request ->
            val uriHandler = LocalUriHandler.current
            val close = { opened: Boolean ->
                request.answer.complete(opened)
                linkRequest = null
            }
            AlertDialog(
                onDismissRequest = { close(false) },
                title = { Text("Open in browser?") },
                text = {
                    Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                        Text("$title wants to open a page on")
                        Text(request.host, style = MaterialTheme.typography.titleMedium)
                        Text(request.url, style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { close(runCatching { uriHandler.openUri(request.url) }.isSuccess) }) {
                        Text("Open")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { close(false) }) { Text("Cancel") }
                },
            )
        }
        LaunchedEffect(bridge, session, uiContext, webViewUuid) {
            val ctx = uiContext
            val pluginUuid = webViewUuid
            // Only a plugin can use a granted file, so a page with none behind it gets no picker.
            bridge?.fileAccess = if (ctx != null && pluginUuid != null &&
                session?.scripts?.containsKey(ConfigScriptIds.PLUGIN) == true) {
                PluginConfigFileAccess(libPebble, pluginUuid, ctx)
            } else {
                null
            }
        }
        DisposableEffect(session) {
            val open = session
            bridge?.scripts = open?.scripts.orEmpty()
            // Starting a plugin takes a moment; the page waits for its 'ready' event.
            val starting = scope.launch { open?.open { target -> bridge?.ready(target) } }
            onDispose {
                starting.cancel()
                open?.scripts?.keys?.forEach { bridge?.forget(it) }
                bridge?.scripts = emptyMap()
                scope.launch { open?.close() }
            }
        }
        LaunchedEffect(session) {
            session?.messages?.collect { (target, json) -> bridge?.push(target, json) }
        }
        val pluginsEnabled = libPebble.config.collectAsState().value.watchConfig.enablePlugins
        val visibleConfigPage: VisibleConfigPage = koinInject()
        if (webViewUuid != null) {
            LifecycleResumeEffect(webViewUuid) {
                visibleConfigPage.shown(webViewUuid)
                onPauseOrDispose { visibleConfigPage.hidden(webViewUuid) }
            }
        }
        var showPermissions by remember { mutableStateOf(false) }
        val snackbarHostState = remember { SnackbarHostState() }
        val definedPermissions by rememberUpdatedState(rememberPluginPermissions())
        // Only what the app asks for while the page is open; anything already waiting has the badge.
        // From the statuses, which don't wait for a plugin's config session to start running.
        LaunchedEffect(webViewUuid) {
            val appUuid = webViewUuid ?: return@LaunchedEffect
            var known: Set<String>? = null
            libPebble.appPermissionStatuses(appUuid)
                .filterNotNull()
                .map { all -> all.filter { it.status == PermissionStatus.Waiting }.map { it.permission } }
                .collect { keys ->
                    val new = known?.let { keys - it }.orEmpty()
                    known = keys.toSet()
                    // In the background the notifier tells the user instead.
                    if (new.isEmpty() || visibleConfigPage.app.value != appUuid) return@collect
                    // Launched: showing waits until it's dismissed.
                    launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "$title wants to ${requestSummary(new, definedPermissions)}",
                            actionLabel = "Review",
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) showPermissions = true
                    }
                }
        }
        LaunchedEffect(state.loadingState) {
            if (state.loadingState is LoadingState.Loading) {
                logger.d { "Page load finished, applying shims" }
                val nativeWebView = runCatching { state.nativeWebView }.getOrNull() ?: return@LaunchedEffect
                restoreLocalStorage(nativeWebView)
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("App Settings")
                            Text("Configuring $title", style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = coreNav::goBack) {
                            Icon(
                                Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(
                                    Res.string.back
                                )
                            )
                        }
                    },
                    actions = {
                        if (webViewUuid != null && pluginsEnabled) {
                            Box {
                                TopBarIconButtonWithToolTip(
                                    onClick = { showPermissions = true },
                                    icon = Icons.Outlined.Shield,
                                    description = "Permissions",
                                    badge = { PermissionRequestBadge(webViewUuid) },
                                )
                                MenuBelowAnchor(
                                    expanded = showPermissions,
                                    onDismissRequest = { showPermissions = false },
                                ) {
                                    AppPermissionsSection(webViewUuid)
                                }
                            }
                        }
                    },
                )
            }
        ) { paddingValues ->
            val pageNetwork = network
            if (webViewUuid != null && pageNetwork != null) {
                if (!pageNetwork.enforced) {
                    Text(
                        "These settings can't be opened safely on this device.",
                        modifier = Modifier.padding(paddingValues).padding(16.dp),
                    )
                } else {
                    WebView(
                        state = state,
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        navigator = navigator,
                        platformWebViewParams = platformParams,
                        factory = { webViewFactory(it, webViewUuid, bridge, mayLoad, pageNetwork) }
                    )
                }
            }
        }
    }
}

/** Always opens below its anchor, so the anchor stays visible and tappable to close it. */
@Composable
private fun MenuBelowAnchor(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val margin = with(density) { 8.dp.roundToPx() }
    var maxHeight by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val positionProvider = remember(margin) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                maxHeight = windowSize.height - anchorBounds.bottom - margin
                val x = if (layoutDirection == LayoutDirection.Ltr) {
                    anchorBounds.right - popupContentSize.width
                } else {
                    anchorBounds.left
                }
                return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)), anchorBounds.bottom)
            }
        }
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = MenuDefaults.shape,
            color = MenuDefaults.containerColor,
            tonalElevation = MenuDefaults.TonalElevation,
            shadowElevation = MenuDefaults.ShadowElevation,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 400.dp)
                    .heightIn(max = with(density) { maxHeight.coerceAtLeast(0).toDp() })
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                content = content,
            )
        }
    }
}

private class LinkRequest(val url: String, val host: String, val answer: CompletableDeferred<Boolean>)

internal fun normalizeWatchappSettingsUrl(url: String): String {
    val parsed = runCatching { Url(url) }.getOrNull() ?: return url
    val host = parsed.host.lowercase()

    val isLegacyRawGitHost = host == "cdn.rawgit.com" || host == "rawgit.com"
    if (!isLegacyRawGitHost) return url

    return URLBuilder(parsed).apply {
        this.host = "raw.githack.com"
    }.buildString()
}

private class SettingsRequestInterceptor(
    private val openStoreAppLink: (String) -> Boolean,
    private val onError: suspend () -> Unit,
    private val onSuccess: suspend (String) -> Unit,
) : RequestInterceptor {
    private val PREFIX = "pebblejs://close" // non-compliant intercept for close, because some apps just use "close"
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onInterceptUrlRequest(
        request: WebRequest,
        navigator: WebViewNavigator,
    ): WebRequestInterceptResult {
        if (request.isForMainFrame && openStoreAppLink(request.url)) {
            return WebRequestInterceptResult.Reject
        }
        if (!request.url.startsWith(PREFIX)) {
            return WebRequestInterceptResult.Allow
        }

        // Matches PREFIX followed by #, /, or /? 
        // Ideally all apps would send pebblejs://close#param1=value1&param2=value2 but it's not the case.
        // The original Pebble Technology Corp App did deviate from the official spec and handled these wrong cases too.
        val closeUrlRegex = Regex("""^pebblejs://close(?:#|/\?|/)(.*)$""")

        val data = closeUrlRegex.find(request.url)?.groupValues?.get(1)?.decodeURLPart()
        if (data?.isNotEmpty() == true) {
            scope.launch {
                delay(10)
                onSuccess(data)
            }
        } else {
            scope.launch {
                delay(10)
                onError()
            }
        }

        return WebRequestInterceptResult.Reject
    }
}
