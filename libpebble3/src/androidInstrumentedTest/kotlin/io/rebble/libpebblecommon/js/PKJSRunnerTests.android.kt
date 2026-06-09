package io.rebble.libpebblecommon.js

import android.util.Base64
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.TokenProvider
import io.rebble.libpebblecommon.database.dao.FakeLockerEntryDao
import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

fun createJsRunner(
    libPebble: LibPebble,
    scope: CoroutineScope,
    appInfo: PbwAppInfo,
    lockerEntry: LockerEntry,
    jsPath: Path,
    device: CompanionAppDevice,
    urlOpenRequests: Channel<String>,
    logMessages: Channel<String>
): JsRunner {
    val context = InstrumentationRegistry.getInstrumentation().context
    val watchConfigFlow = testWatchConfigFlow()
    return WebViewJsRunner(
        appContext = AppContext(context),
        libPebble = libPebble,
        jsTokenUtil = JsTokenUtil(
            object : TokenProvider {
                override suspend fun getDevToken(): String? {
                    return null
                }
            },
            lockerEntryDao = FakeLockerEntryDao(),
            watchConfigFlow = watchConfigFlow,
        ),
        device = device,
        appInfo = appInfo,
        lockerEntry = lockerEntry,
        jsPath = jsPath,
        urlOpenRequests = urlOpenRequests,
        logMessages = logMessages,
        remoteTimelineEmulator = testRemoteTimelineEmulator(),
        httpInterceptorManager = testHttpInterceptorManager(),
        notificationConfigFlow = testNotificationConfigFlow(),
        scope = scope
    )
}

@MediumTest
class PKJSRunnerTestsAndroid: PKJSRunnerTests(::createJsRunner) {
    @Test
    override fun testJSExecution() {
        super.testJSExecution()
    }

    @Test
    override fun testJSReady() {
        super.testJSReady()
    }

    @Test
    override fun testLocalStoragePersistence() {
        super.testLocalStoragePersistence()
    }

    @Test
    override fun testLocalStorageSandbox() {
        super.testLocalStorageSandbox()
    }

    @Test
    override fun testLocalStorageEarlyExecution() {
        super.testLocalStorageEarlyExecution()
    }

    @Test
    fun testWebSocketOmitsOriginAndTransfersMessages() {
        val requestHeaders = ArrayBlockingQueue<List<String>>(1)
        val clientMessages = ArrayBlockingQueue<String>(1)
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val serverThread = thread(start = true, name = "pkjs-websocket-test-server") {
            server.use {
                val socket = it.accept()
                socket.use { accepted ->
                    accepted.soTimeout = 5_000
                    val input = accepted.getInputStream()
                    val output = accepted.getOutputStream()
                    val reader = input.bufferedReader()
                    val lines = generateSequence { reader.readLine() }
                        .takeWhile { line -> line.isNotEmpty() }
                        .toList()
                    requestHeaders.put(lines)

                    val key = lines.first { line ->
                        line.startsWith("Sec-WebSocket-Key:", ignoreCase = true)
                    }.substringAfter(':').trim()
                    val accept = websocketAccept(key)
                    output.writer().apply {
                        writeWebSocketHandshake(accept, TEST_PROTOCOL)
                        flush()
                    }
                    val frame = input.readWebSocketFrame()
                    if (frame.opcode == OPCODE_TEXT) {
                        clientMessages.put(frame.payload.decodeToString())
                    }
                    output.writeWebSocketTextFrame("server hello")
                    output.flush()
                }
            }
        }

        val runner = makeRunner(
            """
                const ws = new WebSocket("ws://127.0.0.1:${server.localPort}/gateway", "$TEST_PROTOCOL");
                ws.onopen = function() {
                    window.wsProtocol = ws.protocol;
                    ws.send("client hello");
                };
                ws.onmessage = function(event) {
                    window.wsMessage = event.data;
                    ws.close(1000, "done");
                };
            """.trimIndent(),
            kotlin.uuid.Uuid.random()
        )

        try {
            runBlocking {
                runner.start()
                val headers = requestHeaders.poll(5, TimeUnit.SECONDS)
                    ?: error("Timed out waiting for WebSocket handshake")
                assertFalse(
                    headers.any { it.startsWith("Origin:", ignoreCase = true) },
                    "Android PKJS native WebSocket must not send an Origin header"
                )
                assertTrue(
                    headers.any { it.equals("Sec-WebSocket-Protocol: $TEST_PROTOCOL", ignoreCase = true) },
                    "Android PKJS native WebSocket should preserve requested subprotocols"
                )
                assertEquals(
                    "client hello",
                    clientMessages.poll(5, TimeUnit.SECONDS),
                    "Android PKJS native WebSocket should deliver text frames from JS to native"
                )
                waitForJsCondition(
                    runner,
                    "window.wsProtocol === '$TEST_PROTOCOL' && window.wsMessage === 'server hello'"
                )
            }
        } finally {
            runBlocking {
                runner.stop()
            }
            server.close()
            serverThread.join(1_000)
        }
    }

    @Test
    fun testWebSocketRejectsUnexpectedSubprotocol() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val serverThread = thread(start = true, name = "pkjs-websocket-protocol-test-server") {
            server.use {
                val socket = it.accept()
                socket.use { accepted ->
                    accepted.soTimeout = 5_000
                    val reader = accepted.getInputStream().bufferedReader()
                    val lines = generateSequence { reader.readLine() }
                        .takeWhile { line -> line.isNotEmpty() }
                        .toList()
                    val key = lines.first { line ->
                        line.startsWith("Sec-WebSocket-Key:", ignoreCase = true)
                    }.substringAfter(':').trim()
                    accepted.getOutputStream().writer().apply {
                        writeWebSocketHandshake(websocketAccept(key), "unexpected.v1")
                        flush()
                    }
                }
            }
        }

        val runner = makeRunner(
            """
                const ws = new WebSocket("ws://127.0.0.1:${server.localPort}/gateway", "$TEST_PROTOCOL");
                window.wsOpened = false;
                window.wsErrored = false;
                window.wsClosed = false;
                ws.onopen = function() {
                    window.wsOpened = true;
                };
                ws.onerror = function() {
                    window.wsErrored = true;
                };
                ws.onclose = function(event) {
                    window.wsClosed = true;
                    window.wsCloseCode = event.code;
                    window.wsWasClean = event.wasClean;
                };
            """.trimIndent(),
            kotlin.uuid.Uuid.random()
        )

        try {
            runBlocking {
                runner.start()
                waitForJsCondition(
                    runner,
                    "window.wsClosed === true && window.wsCloseCode === 1006 && window.wsWasClean === false"
                )
                assertFalse(
                    jsBoolean(runner.evalWithResult("window.wsOpened === true")),
                    "Unexpected subprotocol must fail before open"
                )
                assertTrue(
                    jsBoolean(runner.evalWithResult("window.wsErrored === true")),
                    "Unexpected subprotocol should dispatch error"
                )
            }
        } finally {
            runBlocking {
                runner.stop()
            }
            server.close()
            serverThread.join(1_000)
        }
    }

    @Test
    fun testWebSocketCloseWhileConnectingDoesNotOpen() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply {
            soTimeout = 5_000
        }
        val serverThread = thread(start = true, name = "pkjs-websocket-close-connecting-test-server") {
            try {
                server.use {
                    val socket = it.accept()
                    socket.use { accepted ->
                        accepted.getInputStream().bufferedReader().readLine()
                        Thread.sleep(500)
                    }
                }
            } catch (_: SocketTimeoutException) {
                // The client may cancel before the server accepts the connection.
            }
        }

        val runner = makeRunner(
            """
                const ws = new WebSocket("ws://127.0.0.1:${server.localPort}/slow");
                window.wsOpened = false;
                window.wsClosed = false;
                ws.onopen = function() {
                    window.wsOpened = true;
                };
                ws.onclose = function(event) {
                    window.wsClosed = true;
                    window.wsCloseCode = event.code;
                    window.wsWasClean = event.wasClean;
                };
                ws.close();
            """.trimIndent(),
            kotlin.uuid.Uuid.random()
        )

        try {
            runBlocking {
                runner.start()
                waitForJsCondition(
                    runner,
                    "window.wsClosed === true && window.wsCloseCode === 1006 && window.wsWasClean === false"
                )
                assertFalse(
                    jsBoolean(runner.evalWithResult("window.wsOpened === true")),
                    "Closing during CONNECTING must not later dispatch open"
                )
            }
        } finally {
            runBlocking {
                runner.stop()
            }
            server.close()
            serverThread.join(1_000)
        }
    }

    private fun websocketAccept(key: String): String {
        val bytes = MessageDigest.getInstance("SHA-1")
            .digest("$key$WEBSOCKET_GUID".encodeToByteArray())
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun java.io.Writer.writeWebSocketHandshake(accept: String, protocol: String?) {
        write(
            "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $accept\r\n"
        )
        if (protocol != null) {
            write("Sec-WebSocket-Protocol: $protocol\r\n")
        }
        write("\r\n")
    }

    private suspend fun waitForJsCondition(runner: JsRunner, expression: String) {
        val deadline = System.currentTimeMillis() + 5_000
        var lastResult: Any? = null
        while (System.currentTimeMillis() < deadline) {
            lastResult = runner.evalWithResult(expression)
            if (jsBoolean(lastResult)) {
                return
            }
            delay(50)
        }
        error("Timed out waiting for JS condition `$expression`, last result was $lastResult")
    }

    private fun jsBoolean(result: Any?): Boolean {
        return when (result) {
            is Boolean -> result
            is String -> result == "true"
            else -> false
        }
    }

    private fun InputStream.readWebSocketFrame(): WebSocketFrame {
        val first = readByteOrThrow()
        val second = readByteOrThrow()
        val opcode = first and OPCODE_MASK
        val masked = (second and MASK_FLAG) != 0
        val length = when (val shortLength = second and PAYLOAD_LENGTH_MASK) {
            PAYLOAD_LENGTH_16 -> (readByteOrThrow() shl 8) or readByteOrThrow()
            PAYLOAD_LENGTH_64 -> error("64-bit WebSocket frame lengths are not needed for this test")
            else -> shortLength
        }
        val mask = if (masked) {
            ByteArray(4).also { readFully(it) }
        } else {
            null
        }
        val payload = ByteArray(length).also { readFully(it) }
        if (mask != null) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor mask[i % mask.size].toInt()).toByte()
            }
        }
        return WebSocketFrame(opcode, payload)
    }

    private fun InputStream.readByteOrThrow(): Int {
        val value = read()
        if (value == -1) {
            error("Unexpected end of WebSocket stream")
        }
        return value
    }

    private fun InputStream.readFully(buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read == -1) {
                error("Unexpected end of WebSocket stream")
            }
            offset += read
        }
    }

    private fun OutputStream.writeWebSocketTextFrame(message: String) {
        writeWebSocketFrame(OPCODE_TEXT, message.encodeToByteArray())
    }

    private fun OutputStream.writeWebSocketFrame(opcode: Int, payload: ByteArray) {
        check(payload.size <= 125) { "Test WebSocket frames must use short payloads" }
        write(0x80 or opcode)
        write(payload.size)
        write(payload)
    }

    private data class WebSocketFrame(
        val opcode: Int,
        val payload: ByteArray,
    )

    private companion object {
        const val TEST_PROTOCOL = "test.v1"
        const val OPCODE_MASK = 0x0f
        const val OPCODE_TEXT = 0x1
        const val MASK_FLAG = 0x80
        const val PAYLOAD_LENGTH_MASK = 0x7f
        const val PAYLOAD_LENGTH_16 = 126
        const val PAYLOAD_LENGTH_64 = 127
        const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    }
}
