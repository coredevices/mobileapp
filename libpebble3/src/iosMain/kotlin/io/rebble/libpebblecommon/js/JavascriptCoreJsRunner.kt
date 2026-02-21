package io.rebble.libpebblecommon.js

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.js.JSCGeolocationInterface
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.js.JSCJSLocalStorageInterface
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.js.reproduceProductionCrash
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import kotlinx.cinterop.StableRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.json.Json
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.JavaScriptCore.JSContext
import platform.JavaScriptCore.JSGarbageCollect
import platform.JavaScriptCore.JSGlobalContextRef
import platform.JavaScriptCore.JSValue
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi

class JavascriptCoreJsRunner(
    private val appContext: AppContext,
    private val libPebble: LibPebble,
    private val jsTokenUtil: JsTokenUtil,
    device: CompanionAppDevice,
    private val scope: CoroutineScope,
    appInfo: PbwAppInfo,
    lockerEntry: LockerEntry,
    jsPath: Path,
    urlOpenRequests: Channel<String>,
    private val logMessages: Channel<String>,
    private val remoteTimelineEmulator: RemoteTimelineEmulator,
    private val httpInterceptorManager: HttpInterceptorManager,
): JsRunner(appInfo, lockerEntry, jsPath, device, urlOpenRequests) {
    private var jsContext: JSContext? = null
    private val logger = Logger.withTag("JSCRunner-${appInfo.longName}")
    private val interfaces = mutableMapOf<String, RegisterableJsInterface>()
    private var dispatcherRef: StableRef<*>? = null
    private var navigatorRef: StableRef<JSValue>? = null
    @OptIn(DelicateCoroutinesApi::class)
    private val threadContext = newSingleThreadContext("JSRunner-${appInfo.uuid}")

    override fun debugForceGC() {
        runBlocking(threadContext) {
            JSGarbageCollect(jsContext!!.JSGlobalContextRef())
        }
    }

    private fun initInterfaces(jsContext: JSContext) {
        fun eval(js: String) = this.jsContext?.evalCatching(js)
        fun evalRaw(js: String): JSValue? = this.jsContext?.evaluateScript(js)

        val evalFn: (String) -> JSValue? = ::eval
        val evalRawFn: (String) -> JSValue? = ::evalRaw

        val interfacesScope = scope + threadContext
        val instances = listOf(
            XMLHTTPRequestManager(interfacesScope, evalFn, httpInterceptorManager, appInfo),
            JSTimeout(interfacesScope, evalRawFn),
            WebSocketManager(interfacesScope, evalFn),
            JSCPKJSInterface(this, device, libPebble, jsTokenUtil),
            JSCPrivatePKJSInterface(jsPath, this, device, interfacesScope, _outgoingAppMessages, logMessages, jsTokenUtil, remoteTimelineEmulator, httpInterceptorManager),
            JSCJSLocalStorageInterface(jsContext, appInfo.uuid, appContext, evalRawFn),
            JSCGeolocationInterface(interfacesScope, this)
        )

        // Store instances for dispatch (Kotlin-side only, not exposed to JSC)
        instances.forEach { interfaces[it.name] = it }

        // Register a single native dispatcher — the only Kotlin object in JSC's graph.
        // Previously, ~35 Kotlin function references were individually set as JSValue
        // properties, each becoming a KotlinBase wrapper. JSC's GC would call [KotlinBase hash]
        // on these from its Heap Helper Thread, racing with K/N's GC and causing EXC_BAD_ACCESS.
        val dispatcher: (String, String, Any?) -> Any? = { objectName, methodName, args ->
            val iface = interfaces[objectName]
            val argList = (args as? List<*>) ?: emptyList<Any?>()
            iface?.dispatch(methodName, argList)
        }
        dispatcherRef = StableRef.create(dispatcher)
        jsContext["__nativeDispatch"] = dispatcher

        // Generate pure JS proxy objects — these contain no KotlinBase references.
        // Each method delegates to __nativeDispatch which routes to the Kotlin dispatch table.
        instances.forEach { iface ->
            val methods = iface.interf.keys.joinToString(",") { "'$it'" }
            jsContext.evaluateScript("""
                var ${iface.name} = {};
                [$methods].forEach(function(m) {
                    ${iface.name}[m] = function() {
                        return __nativeDispatch('${iface.name}', m, Array.from(arguments));
                    };
                });
            """.trimIndent())
            iface.onRegister(jsContext)
        }
    }

    private fun evaluateInternalScript(filenameNoExt: String) {
        val bundle = NSBundle.mainBundle
        val path = bundle.pathForResource(filenameNoExt, "js")
            ?: error("Startup script not found in bundle: $filenameNoExt")
        val js = SystemFileSystem.source(Path(path)).buffered().use {
            it.readString()
        }
        runBlocking(threadContext) {
            jsContext?.evalCatching(js, NSURL.fileURLWithPath(path))
        }
    }

    private fun exceptionHandler(context: JSContext?, exception: JSValue?) {
        val decoded: Any? = when {
            exception == null -> null
            exception.isObject() -> exception.toDictionary()?.let { JSError.fromDictionary(it) }
            else -> exception.toString()
        }
        logger.d { "JS Exception: ${exception?.toObject()}" }
        logger.e { "JS Exception: $decoded" }
    }

    private fun setupJsContext() {
        runBlocking(threadContext) {
            val jsContext = JSContext()
            this@JavascriptCoreJsRunner.jsContext = jsContext
            initInterfaces(jsContext)
            jsContext.exceptionHandler = ::exceptionHandler
            jsContext.setName("PKJS: ${appInfo.longName}")
            val selector = NSSelectorFromString("setInspectable:")
            if (jsContext.respondsToSelector(selector)) {
                jsContext.setInspectable(libPebble.config.value.watchConfig.pkjsInspectable)
            } else {
                logger.w { "JSContext.setInspectable not available on this iOS version" }
            }
        }
    }

    @OptIn(NativeRuntimeApi::class)
    private fun tearDownJsContext() {
        _readyState.value = false
        runBlocking(threadContext) {
            // Cancel the scope and wait for all jobs to complete before closing threadContext
            scope.cancel()
            scope.coroutineContext[kotlinx.coroutines.Job]?.join()

            interfaces.values.forEach { it.close() }
            interfaces.clear()
            dispatcherRef?.dispose()
            dispatcherRef = null
            navigatorRef?.dispose()
            navigatorRef = null
            jsContext = null
        }
        GC.collect()
        threadContext.close()
    }

    private fun evaluateStandardLib() {
        evaluateInternalScript("XMLHTTPRequest")
        evaluateInternalScript("JSTimeout")
        evaluateInternalScript("WebSocket")
    }

    private fun setupNavigator() {
        // Create a JavaScript object for navigator to avoid passing Kotlin Map objects
        val navigatorObj = jsContext?.evaluateScript("({})")!!
        val geolocationObj = jsContext?.evaluateScript("({})")!!

        navigatorObj["userAgent"] = "PKJS"
        navigatorObj["geolocation"] = geolocationObj
        navigatorObj["language"] = NSLocale.currentLocale.localeIdentifier

        // Keep a reference to prevent the JSValue from being collected
        navigatorRef = StableRef.create(navigatorObj)
        jsContext?.set("navigator", navigatorObj)
    }

    override suspend fun start() {
        setupJsContext()
        setupNavigator()
        logger.d { "JS Context set up" }
        evaluateStandardLib()
        logger.d { "Standard lib scripts evaluated" }
        evaluateInternalScript("startup")
        logger.d { "Startup script evaluated" }
        loadAppJs(jsPath.toString())
        // Uncomment to reproduce JSCore crash
//        reproduceProductionCrash(scope, this)
    }

    override suspend fun stop() {
        logger.d { "Stopping JS Context" }
        tearDownJsContext()
        logger.d { "JS Context torn down" }
    }

    override suspend fun loadAppJs(jsUrl: String) {
        SystemFileSystem.source(Path(jsUrl)).buffered().use {
            val js = it.readString()
            withContext(threadContext) {
                jsContext?.evalCatching(js, NSURL.fileURLWithPath(jsUrl))
            }
        }
        signalReady()
    }

    override suspend fun signalInterceptResponse(
        callbackId: String,
        result: InterceptResponse
    ) {
        TODO("Not supported")
    }

    override suspend fun signalNewAppMessageData(data: String?): Boolean {
        withContext(threadContext) {
            jsContext?.evalCatching("globalThis.signalNewAppMessageData(${Json.encodeToString(data)})")
        }
        return true
    }

    override suspend fun signalTimelineToken(callId: String, token: String) {
        val tokenJson = Json.encodeToString(mapOf("userToken" to token, "callId" to callId))
        withContext(threadContext) {
            jsContext?.evalCatching("globalThis.signalTimelineTokenSuccess($tokenJson)")
        }
    }

    override suspend fun signalTimelineTokenFail(callId: String) {
        val tokenJson = Json.encodeToString(mapOf("userToken" to null, "callId" to callId))
        withContext(threadContext) {
            jsContext?.evalCatching("globalThis.signalTimelineTokenFailure($tokenJson)")
        }
    }

    override suspend fun signalReady() {
        withContext(threadContext) {
            jsContext?.evalCatching("globalThis.signalReady()")
        }
    }

    override suspend fun signalShowConfiguration() {
        withContext(threadContext) {
            jsContext?.evalCatching("globalThis.signalShowConfiguration()")
        }
    }

    override suspend fun signalWebviewClosed(data: String?) {
        withContext(threadContext) {
            jsContext?.evalCatching("globalThis.signalWebviewClosedEvent(${Json.encodeToString(data)})")
        }
    }

    override suspend fun eval(js: String) {
        withContext(threadContext) {
            jsContext?.evalCatching(js)
        }
    }

    override suspend fun evalWithResult(js: String): Any? {
        return withContext(threadContext) {
            jsContext?.evalCatching(js)?.toObject()
        }
    }
}