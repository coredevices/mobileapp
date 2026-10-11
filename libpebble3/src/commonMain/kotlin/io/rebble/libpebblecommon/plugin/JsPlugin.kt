package io.rebble.libpebblecommon.plugin

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.js.HttpInterceptorManager
import io.rebble.libpebblecommon.js.JsEngine
import io.rebble.libpebblecommon.js.JsEngineInterface
import io.rebble.libpebblecommon.js.JsEngineLocalStorage
import io.rebble.libpebblecommon.js.WebSocketManager
import io.rebble.libpebblecommon.js.XMLHTTPRequestManager
import io.rebble.libpebblecommon.js.BASE64_JS
import io.rebble.libpebblecommon.js.EngineGeolocation
import io.rebble.libpebblecommon.js.FETCH_JS
import io.rebble.libpebblecommon.js.FETCH_REGISTRY
import io.rebble.libpebblecommon.js.GEOLOCATION_JS
import io.rebble.libpebblecommon.js.WEBSOCKET_JS
import io.rebble.libpebblecommon.permissions.NetworkAccess
import io.rebble.libpebblecommon.permissions.RunningApps
import io.rebble.libpebblecommon.storage.pluginSecureStorage
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * A [Plugin] backed by a JS script. Sources are polled at the declared refresh interval; actions
 * are one-shot. Sessions are cold-started per request and torn down immediately.
 */
class JsPlugin(
    override val pluginUuid: Uuid,
    override val name: String,
    private val manifest: PluginManifest,
    /** Read lazily at session start, so bundled/sideloaded scripts aren't all loaded up front. */
    private val script: suspend () -> String,
    private val appContext: AppContext,
    private val scope: CoroutineScope,
    private val httpClient: HttpClient,
    private val httpInterceptorManager: HttpInterceptorManager,
    private val networkAccess: NetworkAccess,
    private val runningApps: RunningApps,
    /** Host-side hosted OAuth, when available. Only reachable by plugins that declare a connector. */
    private val oauthApi: PluginOAuthApi? = null,
) : Plugin, ConfigMessageTarget {

    override val description: String = manifest.description
    override val sources: List<SourceDeclaration> = manifest.sources
    override val actions: List<ActionDeclaration> = manifest.actions

    private val logger = Logger.withTag("JsPlugin-$name")
    private val lenientJson = Json { ignoreUnknownKeys = true }
    private val pending = mutableMapOf<Int, CompletableDeferred<String>>()

    /** `"<category>/<item>"` keys an action reported stale, to cut the poll wait short. */
    private val refreshRequests = MutableSharedFlow<String>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val sessionLock = Mutex()
    private var nextRequestId = 0

    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
    ): Flow<SourceEnvelope> = callbackFlow {
        val declaration = sources.firstOrNull { it.serves(category, item) }
        if (declaration == null) {
            close()
            return@callbackFlow
        }
        val key = "$category/$item"
        val job = launch {
            while (true) {
                if (retired) {
                    close(IllegalStateException("$name was updated or removed; subscribe again"))
                    break
                }
                val size = iconPixelSize
                    ?.let { ""","iconPixelSize":{"w":${it.w},"h":${it.h}}""" }
                    .orEmpty()
                val wanted = properties
                    ?.joinToString(",") { quote(it) }
                    ?.let { ""","properties":[$it]""" }
                    .orEmpty()
                val raw = request(
                    """{"kind":"source","category":${quote(category)},"item":${quote(item)}""" +
                        """$wanted$size}"""
                )
                raw?.let { parseEnvelope(it) }?.let { send(it) }
                withTimeoutOrNull(declaration.suggestedRefreshIntervalSec.coerceAtLeast(1).seconds) {
                    refreshRequests.first { it == key }
                }
            }
        }
        awaitClose { job.cancel() }
    }

    override suspend fun invoke(action: String, args: JsonObject): ActionResult {
        val raw = request("""{"kind":"action","action":${quote(action)},"args":$args}""")
            ?: return ActionResult.error(PluginErrors.TIMEOUT, "plugin did not respond")
        return try {
            val result = lenientJson.decodeFromString(ActionResult.serializer(), raw)
            if (result.ok) result.refreshed.forEach { refreshRequests.tryEmit(it) }
            result
        } catch (e: Exception) {
            logger.w(e) { "bad action response: $raw" }
            ActionResult.error(PluginErrors.UNKNOWN, "malformed plugin response")
        }
    }

    private fun parseEnvelope(raw: String): SourceEnvelope? = try {
        val parsed = lenientJson.decodeFromString(JsEnvelope.serializer(), raw)
        if (parsed.error != null) {
            logger.w { "source error: ${parsed.error}" }
            null
        } else {
            SourceEnvelope(
                pluginUuid = pluginUuid.toString(),
                validUntilMs = parsed.validUntilMs,
                instances = parsed.instances,
            )
        }
    } catch (e: Exception) {
        logger.w(e) { "bad source response: $raw" }
        null
    }

    /** Cold-start a session, dispatch one request, tear the session down. */
    private suspend fun request(requestJson: String): String? = runningApps.whileRunning(pluginUuid) {
        sessionLock.withLock {
            if (retired) return@withLock null
            val session = newSession()
            try {
                session.start()
                awaitReply { id -> session.engine.eval("globalThis.__pluginDispatch($id, $requestJson)") }
            } catch (e: Exception) {
                logger.w(e) { "plugin session failed" }
                null
            } finally {
                session.stop()
            }
        }
    }

    private suspend fun awaitReply(timeout: Duration = REQUEST_TIMEOUT, send: suspend (Int) -> Unit): String? {
        val deferred = CompletableDeferred<String>()
        val id = nextRequestId++
        pending[id] = deferred
        return try {
            send(id)
            withTimeoutOrNull(timeout) { deferred.await() }
        } finally {
            pending.remove(id)
        }
    }

    /** Wired only when the plugin declares an `oauth` connector and the host has an OAuth API. */
    private val oauth = oauthApi?.takeIf { manifest.oauth.isNotEmpty() }

    private val secureStorage = pluginSecureStorage(appContext, pluginUuid)

    private fun newSession(): Session {
        logger.d { "newSession(): $pluginUuid $name" }
        // XHR drives the JS side by evaluating statements that must land in order — readyState
        // before the response body before the events — so callbacks queue rather than race.
        val evals = Channel<String>(Channel.UNLIMITED)
        val xhr = XMLHTTPRequestManager(
            scope = scope,
            eval = { js -> evals.trySend(js) },
            httpInterceptorManager = httpInterceptorManager,
            appUuid = pluginUuid,
            client = httpClient,
            networkAccess = networkAccess,
            jsTarget = FETCH_REGISTRY,
        )
        val webSockets = WebSocketManager(
            scope = scope,
            eval = { js -> evals.trySend(js) },
            client = httpClient,
            appUuid = pluginUuid,
            networkAccess = networkAccess,
        )
        // Same settings scope PKJS uses for this uuid, so a pbw's plugin and its watchapp JS
        // share one set of stored values.
        val localStorage = JsEngineLocalStorage(
            scopedSettingsUuid = pluginUuid.toString(),
            appContext = appContext,
            eval = { js -> evals.trySend(js) },
        )
        val oauthBridge = oauth?.let {
            PluginOAuthJsBridge(scope, { js -> evals.trySend(js) }, pluginUuid.toString(), manifest.oauth.keys, it)
        }
        val secureStorageBridge = PluginSecureStorageJsBridge(scope, { js -> evals.trySend(js) }, secureStorage)
        val geolocation = EngineGeolocation(scope, { js -> evals.trySend(js) }, pluginUuid)
        val interfaces = listOfNotNull(Bridge(), xhr, webSockets, localStorage, geolocation, oauthBridge, secureStorageBridge)
        val engine = JsEngine(appContext, scope, name, interfaces)
        return Session(
            engine, xhr, webSockets, localStorage, geolocation, oauthBridge, secureStorageBridge, evals,
            scope.launch { for (js in evals) engine.eval(js) },
        )
    }

    private inner class Session(
        val engine: JsEngine,
        private val xhr: XMLHTTPRequestManager,
        private val webSockets: WebSocketManager,
        private val localStorage: JsEngineLocalStorage,
        private val geolocation: EngineGeolocation,
        private val oauthBridge: PluginOAuthJsBridge?,
        private val secureStorageBridge: PluginSecureStorageJsBridge,
        private val evals: Channel<String>,
        private val pump: Job,
    ) {
        suspend fun start() {
            logger.d { "Starting session for: $pluginUuid $name" }
            engine.start()
            engine.eval(localStorage.installJs)
            engine.eval(BASE64_JS)
            engine.eval(FETCH_JS)
            engine.eval(WEBSOCKET_JS)
            engine.eval(GEOLOCATION_JS)
            engine.eval(PLUGIN_HOST_JS)
            oauthBridge?.let { engine.eval(PluginOAuthJsBridge.INSTALL_JS) }
            engine.eval(PluginSecureStorageJsBridge.INSTALL_JS)
            engine.eval(script())
        }

        suspend fun stop() {
            logger.d { "Stopping session for: $pluginUuid $name" }
            evals.close()
            pump.cancel()
            xhr.close()
            webSockets.close()
            geolocation.close()
            oauthBridge?.close()
            secureStorageBridge.close()
            engine.stop()
        }
    }

    // ------------------------------------------------------------------ config page

    private val _configMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val configMessages: Flow<String> = _configMessages
    private val configLock = Mutex()
    private var configSession: Session? = null

    override suspend fun openConfigSession() = configLock.withLock {
        closeConfigSessionLocked()
        openConfigSessionLocked()
        Unit
    }

    override suspend fun closeConfigSession() = configLock.withLock { closeConfigSessionLocked() }

    @Volatile private var retired = false

    suspend fun retire() = configLock.withLock {
        retired = true
        closeConfigSessionLocked()
    }

    private suspend fun closeConfigSessionLocked() {
        val session = configSession ?: return
        configSession = null
        try {
            session.stop()
        } finally {
            runningApps.stopped(pluginUuid)
        }
    }

    /** Starting takes long enough that a page's first message can beat [openConfigSession]. */
    private suspend fun openConfigSessionLocked(): Session? {
        if (retired) return null
        configSession?.let { return it }
        val session = newSession()
        return try {
            session.start()
            runningApps.started(pluginUuid)
            session.also { configSession = it }
        } catch (e: Exception) {
            logger.w(e) { "config session failed to start" }
            session.stop()
            null
        }
    }

    override suspend fun onConfigMessage(json: String): String? = configLock.withLock {
        val session = openConfigSessionLocked() ?: return@withLock null
        try {
            awaitReply(CONFIG_REQUEST_TIMEOUT) { id -> session.engine.eval("globalThis.__pluginConfigMessage($id, $json)") }
        } catch (e: Exception) {
            logger.w(e) { "config message failed" }
            null
        }
    }

    /** What plugin JS calls back into: settling a request, and console output. */
    private inner class Bridge : JsEngineInterface {
        override val name = "_JsBridge"
        override val methods = listOf("respond", "log", "configMessage", "refreshSources")

        override fun dispatch(method: String, args: List<Any?>): Any? = when (method) {
            "respond" -> {
                pending[(args[0] as Number).toInt()]?.complete(args[1].toString())
                null
            }
            "log" -> {
                logger.i { "[${args[0]}] ${args[1]}" }
                null
            }
            "configMessage" -> {
                _configMessages.tryEmit(args[0].toString())
                null
            }
            "refreshSources" -> {
                lenientJson.decodeFromString(ListSerializer(String.serializer()), args[0].toString())
                    .forEach { refreshRequests.tryEmit(it) }
                null
            }
            else -> error("Unknown method: $method")
        }
    }

    /** JSON-quote so the value can be embedded in an eval'd string literal. */
    private fun quote(value: String) = Json.encodeToString(String.serializer(), value)

    @Serializable
    private data class JsEnvelope(
        val validUntilMs: Long? = null,
        val instances: List<SourceInstance> = emptyList(),
        val error: String? = null,
    )

    private companion object {
        val REQUEST_TIMEOUT = 20.seconds

        // An interactive OAuth sign-in (browser round-trip) outlasts a data-read timeout; the UI
        // closes the config session anyway, so this only bounds a wedged plugin.
        val CONFIG_REQUEST_TIMEOUT = 5.minutes
    }
}
