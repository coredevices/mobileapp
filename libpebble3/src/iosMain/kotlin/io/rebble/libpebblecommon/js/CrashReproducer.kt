package io.rebble.libpebblecommon.io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.js.JavascriptCoreJsRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.native.runtime.NativeRuntimeApi

/**
 * Test that reproduces the crash conditions.
 *
 * The crash happened when:
 * 1. signalNewAppMessageData was called (line 226)
 * 2. JSC's GC ran in the background
 * 3. GC tried to hash Kotlin function objects
 *
 * To reproduce, we need to:
 * 1. Keep calling signalNewAppMessageData
 * 2. Keep JSC's GC busy
 * 3. Create conditions for JSC to need to hash objects
 */
@OptIn(NativeRuntimeApi::class)
fun reproduceProductionCrash(scope: CoroutineScope, jsRunner: JavascriptCoreJsRunner) {
    println("🔴 Attempting to reproduce production crash...")

    // Thread 1: Continuously send app messages (this is where crash occurred)
    scope.launch {
        repeat(500) { i ->
            jsRunner.signalNewAppMessageData(
                """
                    {
                        "test": $i,
                        "data": "payload_$i"
                    }
                """.trimIndent()
            )
            delay(10)
        }
    }

    // Thread 2: Force GC repeatedly
    scope.launch {
        repeat(500) {
            delay(15)
            jsRunner.debugForceGC()
            kotlin.native.runtime.GC.collect()
        }
    }

    // Thread 3: Call various Pebble interface methods
    scope.launch {
        repeat(500) { i ->
            try {
                jsRunner.eval(
                    """
                        // Call methods that access the Kotlin-backed functions
                        Pebble.getAccountToken();
                        _Pebble.onConsoleLog('test', 'Message $i', '');
                    """.trimIndent()
                )
            } catch (e: Exception) {
                println("⚠️ Exception in eval: ${e.message}")
            }
            delay(20)
        }
    }

    // Thread 4: Create WeakMaps/WeakSets in JS to trigger hashing
    scope.launch {
        repeat(100) { i ->
            try {
                jsRunner.eval(
                    """
                        // WeakMaps and WeakSets in JS will cause JSC to hash objects
                        const wm = new WeakMap();
                        const ws = new WeakSet();

                        // Try to use Kotlin functions as weak keys (this should trigger hash)
                        const funcs = [Pebble.getAccountToken, _Pebble.onConsoleLog];

                        // This is where JSC might try to hash the Kotlin objects
                        for (let func of funcs) {
                            try {
                                wm.set({ref: func}, $i);
                            } catch(e) {
                                console.log('WeakMap error: ' + e);
                            }
                        }
                    """.trimIndent()
                )
            } catch (e: Exception) {
                println("⚠️ Exception in WeakMap test: ${e.message}")
            }
            delay(50)
        }
    }

    println("✅ Test completed without crash (or 💥 it crashed)")
}
