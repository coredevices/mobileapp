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
): JsRunner(appInfo, lockerEntry, jsPath, device, urlOpenRequests) {
    private var jsContext: JSContext? = null
    private val logger = Logger.withTag("JSCRunner-${appInfo.longName}")
    private var interfacesRef: StableRef<List<RegisterableJsInterface>>? = null
    private val interfaceMapRefs = mutableListOf<StableRef<Map<String, *>>>()
    private val functionRefs = mutableListOf<StableRef<*>>()
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

        // Create stable references for the eval/evalRaw functions to prevent GC from moving them
        val evalFn: (String) -> JSValue? = ::eval
        val evalRawFn: (String) -> JSValue? = ::evalRaw
        val evalRef = StableRef.create(evalFn)
        val evalRawRef = StableRef.create(evalRawFn)
        functionRefs.add(evalRef)
        functionRefs.add(evalRawRef)

        val interfacesScope = scope + threadContext
        val instances = listOf(
            XMLHTTPRequestManager(interfacesScope, evalFn, remoteTimelineEmulator, appInfo),
            JSTimeout(interfacesScope, evalRawFn),
            JSCPKJSInterface(this, device, libPebble, jsTokenUtil, remoteTimelineEmulator),
            JSCPrivatePKJSInterface(jsPath, this, device, interfacesScope, _outgoingAppMessages, logMessages, jsTokenUtil, remoteTimelineEmulator),
            JSCJSLocalStorageInterface(jsContext, appInfo.uuid, appContext, evalRawFn),
            JSCGeolocationInterface(interfacesScope, this)
        )
        interfacesRef = StableRef.create(instances)
        instances.forEach {
            // Create a JavaScript object and set properties individually to avoid passing
            // Kotlin Map objects which can be moved by GC
            val jsObject = jsContext.evaluateScript("({})")!!

            // Create stable references for all functions and set them on the JS object
            it.interf.forEach { (key, value) ->
                if (value != null) {
                    functionRefs.add(StableRef.create(value))
                    jsObject[key] = value
                }
            }

            // Store a reference to the Kotlin interf map to keep it alive
            interfaceMapRefs.add(StableRef.create(it.interf))

            jsContext[it.name] = jsObject
            it.onRegister(jsContext)
        }
    }

    private fun evaluateInternalScript(filenameNoExt: String) {
        val bundle = NSBundle.mainBundle
        val path = bundle.pathForResource(filenameNoExt, "js")
            ?: error("Startup script not found in bundle")
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

            interfacesRef?.let {
                it.get().forEach { iface -> iface.close() }
                it.dispose()
            }
            interfacesRef = null
            // Dispose all interface map references
            interfaceMapRefs.forEach { it.dispose() }
            interfaceMapRefs.clear()
            // Dispose all function references
            functionRefs.forEach { it.dispose() }
            functionRefs.clear()
            // Dispose navigator reference
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