package coredevices.pebble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
import coredevices.analytics.CoreAnalytics
import coredevices.pebble.account.BootConfig
import coredevices.pebble.account.BootConfigProvider
import coredevices.pebble.rememberLibPebble
import coredevices.pebble.services.PebbleWebServices
import coredevices.pebble.ui.PebbleRequestInterceptor.Companion.ARGS_ADD_TO_LOCKER_RESULT
import coredevices.pebble.ui.PebbleRequestInterceptor.Companion.ARGS_LOAD_TO_DEVICE_RESULT
import coredevices.pebble.ui.PebbleRequestInterceptor.Companion.ARGS_QUERY
import coredevices.pebble.ui.PebbleRequestInterceptor.Companion.SEARCH
import coredevices.ui.PebbleWebview
import coredevices.ui.PebbleWebviewNavigator
import coredevices.ui.PebbleWebviewUrlInterceptor
import io.ktor.http.decodeURLPart
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.KnownPebbleDevice
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

val appStoreLogger = Logger.withTag("AppStoreScreen")

@Composable
fun AppStoreScreen(
    nav: NavBarNav,
    type: AppType?,
    topBarParams: TopBarParams,
    deepLinkId: String?,
) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val bootConfigProvider = koinInject<BootConfigProvider>()
        var bootConfig by remember { mutableStateOf<BootConfig?>(null) }
        val libPebble = rememberLibPebble()
        val searchState = rememberSearchState()

        val url = remember(bootConfig) {
            val watch = libPebble.watches.value
                .sortedWith(PebbleDeviceComparator)
                .filterIsInstance<KnownPebbleDevice>()
                .firstOrNull()
            val watchType = watch?.watchType ?: WatchHardwarePlatform.CORE_ASTERIX
            (bootConfig?.let {
                when {
                    deepLinkId != null -> {
                        it.config.webViews.appStoreApplication.replace("\$\$id\$\$", deepLinkId)
                    }
                    type != null -> {
                        when (type) {
                            AppType.Watchface -> it.config.webViews.appStoreWatchFaces
                            AppType.Watchapp -> it.config.webViews.appStoreWatchApps
                        }
                    } else -> null
                }
            } ?: "about:blank").replace("\$\$hardware\$\$", watchType.watchType.codename)
        }

        val scope = rememberCoroutineScope()
        val webServices = koinInject<PebbleWebServices>()
        val watchesFiltered = remember {
            libPebble.watches.map {
                it.sortedWith(PebbleDeviceComparator).filterIsInstance<KnownPebbleDevice>()
                    .firstOrNull()
            }
        }
        val lastConnectedWatch by watchesFiltered.collectAsState(null)
        val analytics: CoreAnalytics = koinInject()
        val interceptor = remember {
            PebbleRequestInterceptor {
                when (methodName) {
                    "loadAppToDeviceAndLocker" -> {
                        val app = dataAs<AppJson>()
                        appStoreLogger.v { "app = $app" }
                        if (app == null) return@PebbleRequestInterceptor
                        scope.launch {
                            val added = withTimeoutOrNull(5.seconds) {
                                analytics.logEvent(
                                    "locker.app.install", mapOf(
                                        "uuid" to app.uuid,
                                        "name" to app.title,
                                    )
                                )
                                webServices.addToLegacyLocker(app.uuid)
                            } ?: false
                            appStoreLogger.v { "Add to locker: added=$added" }
                            topBarParams.showSnackbar("Syncing Locker")
                            val synced = withTimeoutOrNull(10.seconds) {
                                if (added) {
                                    libPebble.requestLockerSync().await()
                                    appStoreLogger.v { "Locker sync finished" }
                                }
                            } != null
                            val watch = lastConnectedWatch as? ConnectedPebbleDevice
                            if (added && synced && watch != null) {
                                val uuid = Uuid.parse(app.uuid)
                                val entry = libPebble.getLockerApp(uuid).firstOrNull()
                                if (entry == null) {
                                    appStoreLogger.e { "Locker entry not found for $app" }
                                    topBarParams.showSnackbar("Error adding to locker")
                                    return@launch
                                }
                                appStoreLogger.v { "Launch app" }
                                val commonEntry = entry.asCommonApp(
                                    watchType = watch.watchType.watchType,
                                    appstoreSource = null,
                                    categories = null,
                                )
                                if (!libPebble.launchApp(
                                        commonEntry,
                                        topBarParams,
                                        watch.identifier
                                    )
                                ) {
                                    return@launch
                                }
                                topBarParams.showSnackbar("Launching ${commonEntry.title}")
                                if (commonEntry.hasSettings()) {
                                    commonEntry.showSettings(nav, libPebble, topBarParams)
                                }
                            }
                            appStoreLogger.v { "Add to locker: synced=$synced" }
                            sendResult(
                                mapOf(
                                    ARGS_ADD_TO_LOCKER_RESULT to Json.encodeToJsonElement(added),
                                    ARGS_LOAD_TO_DEVICE_RESULT to Json.encodeToJsonElement(synced),
                                )
                            )
                        }
                    }

                    "setNavBarTitle" -> {
                        val title = dataAs<TitleJson>()
                        if (title == null) return@PebbleRequestInterceptor
                        topBarParams.title(title.title)
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            bootConfig = bootConfigProvider.getBootConfig()
            topBarParams.searchAvailable(searchState)
            topBarParams.actions { }
            topBarParams.title("")
            launch {
                topBarParams.overrideGoBack.collect {
                    if (interceptor.navigator?.goBack() != true) {
                        nav.goBack()
                    }
                }
            }
        }

        LaunchedEffect(searchState.typing) {
            if (!searchState.typing && searchState.query.isNotEmpty()) {
                appStoreLogger.d { "Search: ${searchState.query}" }
                val args = buildJsonObject {
                    put(ARGS_QUERY, JsonPrimitive(searchState.query))
                }
                interceptor.invokeMethod(method = SEARCH, args = args)
            }
        }
        PebbleWebview(
            url = url,
            interceptor = interceptor,
            modifier = Modifier.fillMaxSize(),
        )
    }
}


@Serializable
data class AppJson(
    val id: String,
    val title: String,
    val uuid: String,
    val type: String,
)

@Serializable
data class TitleJson(
    val title: String,
)

class PebbleRequestInterceptor(
    private val methodHandler: MethodHandler.() -> Unit,
) : PebbleWebviewUrlInterceptor {
    override var navigator: PebbleWebviewNavigator? = null

    override fun onIntercept(url: String, navigator: PebbleWebviewNavigator): Boolean {
        val match = REGEX.find(url)
        if (match == null) {
            return true
        }
        val method = match.groupValues[1]
        val argsString = match.groupValues[2]
        appStoreLogger.v { "method = $method args = $argsString" }
        val jsonString = argsString.decodeURLPart()
        val json = Json.parseToJsonElement(jsonString)
        val handler = MethodHandler(method, json, navigator)
        handler.methodHandler()
        return true
    }

    fun invokeMethod(method: String, args: Map<String, JsonElement>) {
        val nav = navigator
        if (nav == null) {
            appStoreLogger.e { "navigator is null?" }
            return
        }
        val call = buildJsonObject {
            put(CALLBACK_ID, JsonPrimitive(0))
            put(METHOD_NAME, JsonPrimitive(method))
            args.forEach {
                put(it.key, it.value)
            }
        }
        val url = "javascript:PebbleBridge.handleRequest($call);"
        nav.loadUrl(url)
    }

    class MethodHandler(
        val methodName: String,
        val json: JsonElement,
        private val navigator: PebbleWebviewNavigator
    ) {
        val data = json.jsonObject[KEY_DATA]?.jsonObject
        val dataString = data?.toString()
        private val callbackId = json.jsonObject["callbackId"]?.jsonPrimitive?.intOrNull

        inline fun <reified T> dataAs(): T? = dataString?.let {
            try {
                JSON.decodeFromString<T>(it)
            } catch (e: SerializationException) {
                appStoreLogger.w(e) { "Error deserializing: $it" }
                null
            }
        }

        fun sendResult(args: Map<String, JsonElement>) {
            if (callbackId == null) return
            val finalData = data?.buildUpon {
                args.forEach { (key, value) ->
                    put(key, value)
                }
                put(ARGS_EXECUTION_RESULT, Json.encodeToJsonElement(true))
            }
            val finalJson = json.buildUpon {
                finalData?.let {
                    put(KEY_DATA, finalData)
                }
            }
            val finalJsonString = finalJson.toString()
            navigator.loadUrl("javascript:PebbleBridge.handleResponse($finalJsonString);")
        }
    }

    companion object {
        private val REGEX = Regex("pebble-method-call-js-frame://\\??method=(.*)&args=(.*)")
        const val ARGS_ADD_TO_LOCKER_RESULT: String = "added_to_locker"
        const val ARGS_LOAD_TO_DEVICE_RESULT: String = "loaded_on_device"
        const val ARGS_EXECUTION_RESULT: String = "execution_result"
        const val KEY_DATA = "data"
        const val CALLBACK_ID = "callbackId"
        const val METHOD_NAME = "methodName"
        const val ARGS_QUERY: String = "query"
        const val SEARCH: String = "search"
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

fun JsonElement.buildUpon(block: JsonObjectBuilder.() -> Unit): JsonElement {
    return buildJsonObject {
        this@buildUpon.jsonObject.forEach { (key, value) ->
            put(key, value)
        }
        block()
    }
}