package io.rebble.libpebblecommon.js

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.cinterop.StableRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import platform.JavaScriptCore.JSContext

actual class JsEngine actual constructor(
    appContext: AppContext,
    scope: CoroutineScope,
    private val name: String,
    private val interfaces: List<JsEngineInterface>,
) {
    private val logger = Logger.withTag("JsEngine-$name")

    @OptIn(DelicateCoroutinesApi::class)
    private val threadContext = newSingleThreadContext("JsEngine-$name")
    private var jsContext: JSContext? = null
    private var dispatcherRef: StableRef<*>? = null

    actual suspend fun start() = withContext(threadContext) {
        val context = JSContext()
        context.setExceptionHandler { _, exception ->
            logger.e { "JS exception: ${exception?.toString()}" }
        }

        // A single Kotlin lambda in JSC's object graph, as in JavascriptCoreJsRunner: many
        // individually-bridged Kotlin functions race JSC's GC helper thread against K/N's GC.
        val byName = interfaces.associateBy { it.name }
        val dispatch: (String, String, Any?) -> Any? = { ifaceName, method, args ->
            byName[ifaceName]?.dispatch(method, (args as? List<*>) ?: emptyList<Any?>())
        }
        dispatcherRef = StableRef.create(dispatch)
        context[DISPATCH_NAMESPACE] = dispatch
        interfaces.forEach { context.evalCatching(it.proxyJs()) }

        jsContext = context
        Unit
    }

    actual suspend fun eval(js: String) {
        val context = jsContext ?: return
        withContext(threadContext) { context.evalCatching(js) }
    }

    actual suspend fun stop() {
        withContext(threadContext) {
            interfaces.forEach { (it as? AutoCloseable)?.close() }
            jsContext = null
        }
        dispatcherRef?.dispose()
        dispatcherRef = null
        threadContext.close()
    }

    private companion object {
        const val DISPATCH_NAMESPACE = "__jsEngineDispatch"

        fun JsEngineInterface.proxyJs(): String {
            val methodList = methods.joinToString(",") { "'$it'" }
            return """
                var $name = {};
                [$methodList].forEach(function (m) {
                  $name[m] = function () {
                    return $DISPATCH_NAMESPACE('$name', m, Array.from(arguments));
                  };
                });
            """.trimIndent()
        }
    }
}
