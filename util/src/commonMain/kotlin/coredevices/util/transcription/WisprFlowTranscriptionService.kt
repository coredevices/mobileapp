package coredevices.util.transcription

import co.touchlab.kermit.Logger
import coredevices.api.AppInfo
import coredevices.api.WisprAppendMessage
import coredevices.api.WisprAuthMessage
import coredevices.api.WisprAudioPackets
import coredevices.api.WisprCommitMessage
import coredevices.api.WisprContext
import coredevices.api.WisprConversationContext
import coredevices.api.WisprConversationMessage
import coredevices.api.WisprFlowAuth
import coredevices.api.WisprJson
import coredevices.api.WisprResponse
import coredevices.util.AudioEncoding
import coredevices.util.CommonBuildKonfig
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class WisprFlowTranscriptionService(
    private val wisprFlowAuth: WisprFlowAuth,
) : TranscriptionService {
    companion object {
        private val logger = Logger.withTag("WisprFlowTranscriptionService")
        private const val WISPR_WS_URL = "wss://platform-api.wisprflow.ai/api/v1/dash/client_ws"
        private const val TARGET_SAMPLE_RATE = 16000
        private const val PACKET_DURATION_MS = 500
        private const val SAMPLES_PER_PACKET = TARGET_SAMPLE_RATE * PACKET_DURATION_MS / 1000 // 800 samples
        private const val BYTES_PER_PACKET = SAMPLES_PER_PACKET * 2 // 16-bit = 2 bytes per sample
    }

    private val client = HttpClient {
        install(WebSockets)
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    private data class PreconnectedSession(val clientKey: String, val session: WebSocketSession)

    private var pendingConnection: Deferred<PreconnectedSession>? = null

    override val onInitialized: Channel<Boolean> = Channel()

    private suspend fun resolveAccessToken(): String? {
        return withTimeout(10.seconds) {
            wisprFlowAuth.getAccessToken()
        }
    }

    override suspend fun isAvailable(): Boolean {
        val available = CommonBuildKonfig.WISPR_AUTH_URL != null
        if (available && pendingConnection == null) {
            pendingConnection = scope.async {
                val clientKey = checkNotNull(resolveAccessToken()) { "WISPR access token unavailable" }
                val (resolvedKey, session) = connectWebSocket(clientKey)
                PreconnectedSession(resolvedKey, session)
            }
        }
        return available
    }

    private suspend fun connectWebSocket(clientKey: String): Pair<String, WebSocketSession> {
        return try {
            val session = withTimeout(10.seconds) {
                client.webSocketSession(WISPR_WS_URL) {
                    url.parameters.append("client_key", "Bearer $clientKey")
                }
            }
            clientKey to session
        } catch (e: Exception) {
            if (e.isForbidden()) {
                logger.w { "WebSocket connection returned 403, refreshing token and retrying" }
                val newToken = checkNotNull(wisprFlowAuth.getAccessToken(forceRefresh = true)) {
                    "WISPR access token unavailable after refresh"
                }
                val session = withTimeout(10.seconds) {
                    client.webSocketSession(WISPR_WS_URL) {
                        url.parameters.append("client_key", "Bearer $newToken")
                    }
                }
                newToken to session
            } else {
                throw e
            }
        }
    }

    private fun Exception.isForbidden(): Boolean {
        // Ktor throws various exception types for failed WebSocket upgrades.
        // Check the message and cause chain for 403 status indicators.
        val msg = message ?: ""
        val causeMsg = cause?.message ?: ""
        return when {
            "403" in msg || "Forbidden" in msg -> true
            "403" in causeMsg || "Forbidden" in causeMsg -> true
            else -> false
        }
    }

    @OptIn(ExperimentalEncodingApi::class, FlowPreview::class)
    override suspend fun transcribe(
        audioStreamFrames: Flow<ByteArray>?,
        sampleRate: Int,
        language: STTLanguage,
        conversationContext: STTConversationContext?,
        dictionaryContext: List<String>?,
        contentContext: String?,
        encoding: AudioEncoding,
        timeout: Duration,
    ): Flow<TranscriptionSessionStatus> = flow {
        if (audioStreamFrames == null) {
            return@flow
        }

        val clientKey: String
        val session: WebSocketSession

        val preconnected = pendingConnection?.let {
            pendingConnection = null
            try {
                it.await()
            } catch (e: Exception) {
                logger.w(e) { "Pre-emptive connection failed, retrying" }
                null
            }
        }
        if (preconnected != null) {
            clientKey = preconnected.clientKey
            session = preconnected.session
        } else {
            val initialKey = checkNotNull(resolveAccessToken()) { "WISPR access token unavailable" }
            val (resolvedKey, resolvedSession) = connectWebSocket(initialKey)
            clientKey = resolvedKey
            session = resolvedSession
        }


        try {
            session.sendAuth(clientKey, language, conversationContext, dictionaryContext, contentContext)
            waitForAuth(session)

            emit(TranscriptionSessionStatus.Open)

            val finalTextDeferred = CompletableDeferred<String>()
            val partials = Channel<String>(Channel.UNLIMITED)

            // Listen for incoming frames continuously
            scope.launch {
                try {
                    for (frame in session.incoming) {
                        if (frame !is Frame.Text) continue
                        val response = WisprJson.decodeFromString<WisprResponse>(frame.readText())

                        when (response.status) {
                            "text" -> {
                                val text = response.body?.text
                                if (!text.isNullOrBlank()) {
                                    if (response.final) {
                                        finalTextDeferred.complete(text)
                                        return@launch
                                    } else {
                                        partials.send(text)
                                    }
                                }
                            }
                            "error" -> {
                                val error = response.error ?: response.message?.toString() ?: "Unknown error"
                                logger.e { "WisprFlow error: $error" }
                                finalTextDeferred.completeExceptionally(
                                    TranscriptionException.TranscriptionServiceError(error, modelUsed = "wisprflow")
                                )
                                return@launch
                            }
                            "info" -> {
                                logger.d { "WisprFlow event: ${response.message?.get("event")}" }
                            }
                        }
                    }
                    // WebSocket closed without final text
                    if (!finalTextDeferred.isCompleted) {
                        finalTextDeferred.completeExceptionally(
                            TranscriptionException.NoSpeechDetected("no_final_transcript", modelUsed = "wisprflow")
                        )
                    }
                } catch (e: Exception) {
                    if (!finalTextDeferred.isCompleted) {
                        finalTextDeferred.completeExceptionally(e)
                    }
                } finally {
                    partials.close()
                }
            }

            // Stream audio packets
            var packetPosition = 0
            val packetDuration = PACKET_DURATION_MS / 1000.0
            var residualBuffer = ByteArray(0)

            audioStreamFrames.collect { chunk ->
                val audioData = if (sampleRate != TARGET_SAMPLE_RATE) {
                    resamplePcm16(chunk, sampleRate, TARGET_SAMPLE_RATE)
                } else {
                    chunk
                }

                // Combine with any leftover from previous chunk
                val combined = residualBuffer + audioData
                var offset = 0

                while (offset + BYTES_PER_PACKET <= combined.size) {
                    val packet = combined.copyOfRange(offset, offset + BYTES_PER_PACKET)
                    val base64Audio = Base64.encode(packet)
                    val volume = calculateVolume(packet)

                    session.sendAppend(packetPosition, base64Audio, volume, packetDuration)
                    packetPosition++
                    offset += BYTES_PER_PACKET
                }

                residualBuffer = if (offset < combined.size) {
                    combined.copyOfRange(offset, combined.size)
                } else {
                    ByteArray(0)
                }

                // Drain any partials that arrived while streaming
                while (true) {
                    val partial = partials.tryReceive().getOrNull() ?: break
                    emit(TranscriptionSessionStatus.Partial(partial))
                }
            }

            // Send any remaining audio as a zero-padded final packet
            if (residualBuffer.isNotEmpty()) {
                val padded = ByteArray(BYTES_PER_PACKET)
                residualBuffer.copyInto(padded)
                val base64Audio = Base64.encode(padded)
                val volume = calculateVolume(padded)
                session.sendAppend(packetPosition, base64Audio, volume, packetDuration)
                packetPosition++
            }

            // Send commit
            val commitMsg = WisprCommitMessage(totalPackets = packetPosition)
            session.send(Frame.Text(WisprJson.encodeToString(commitMsg)))
            logger.d { "Sent commit with $packetPosition total packets" }

            // Drain remaining partials
            for (partial in partials) {
                emit(TranscriptionSessionStatus.Partial(partial))
            }

            // Await final transcription
            val finalText = finalTextDeferred.await()
            emit(TranscriptionSessionStatus.Transcription(finalText, "wisprflow"))
        } catch (e: TranscriptionException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "WisprFlow transcription failed: ${e.message}" }
            throw TranscriptionException.TranscriptionServiceError(
                "WisprFlow error: ${e.message}",
                cause = e,
                modelUsed = "wisprflow"
            )
        }
    }.timeout(10.seconds)

    private suspend fun WebSocketSession.sendAuth(
        clientKey: String,
        language: STTLanguage,
        conversationContext: STTConversationContext?,
        dictionaryContext: List<String>?,
        contentContext: String?
    ) {
        val nameSplit = Firebase.auth.currentUser?.displayName?.split(" ", limit = 2)
        val authMsg = WisprAuthMessage(
            accessToken = clientKey,
            context = WisprContext(
                app = AppInfo(name = "Core Devices", type = "other"),
                dictionaryContext = dictionaryContext,
                userFirstName = nameSplit?.firstOrNull(),
                userLastName = nameSplit?.lastOrNull(),
                contentText = contentContext,
                conversation = conversationContext
                    ?.takeIf { it.messages.isNotEmpty() || it.participants.isNotEmpty() }
                    ?.let {
                        WisprConversationContext(
                            id = it.id,
                            participants = it.participants,
                            messages = it.messages.map { msg ->
                                WisprConversationMessage(
                                    role = when (msg.role) {
                                        STTConvoRole.User -> "user"
                                        STTConvoRole.Human -> "human"
                                        STTConvoRole.Assistant -> "assistant"
                                    },
                                    content = msg.content
                                )
                            }
                        )
                    }
            ),
            language = when (language) {
                is STTLanguage.Automatic -> listOf("en")
                is STTLanguage.Specific -> language.languageCodes.toList()
            }
        )
        send(Frame.Text(WisprJson.encodeToString(authMsg)))
        logger.d { "Sent auth message" }
    }

    private suspend fun WebSocketSession.sendAppend(
        position: Int,
        base64Audio: String,
        volume: Double,
        packetDuration: Double
    ) {
        val msg = WisprAppendMessage(
            position = position,
            audioPackets = WisprAudioPackets(
                packets = listOf(base64Audio),
                volumes = listOf(volume),
                packetDuration = packetDuration,
            )
        )
        send(Frame.Text(WisprJson.encodeToString(msg)))
    }

    private suspend fun waitForAuth(session: WebSocketSession) {
        for (frame in session.incoming) {
            if (frame is Frame.Text) {
                val response = WisprJson.decodeFromString<WisprResponse>(frame.readText())
                if (response.status == "auth") {
                    logger.d { "WisprFlow authenticated" }
                    return
                }
                if (response.status == "error") {
                    val error = response.error ?: "Auth failed"
                    throw TranscriptionException.TranscriptionServiceError(error, modelUsed = "wisprflow")
                }
            }
        }
        throw TranscriptionException.TranscriptionServiceError("WebSocket closed before auth", modelUsed = "wisprflow")
    }

    private fun calculateVolume(pcm16Data: ByteArray): Double {
        var sum = 0.0
        for (i in 0 until pcm16Data.size - 1 step 2) {
            val sample = (pcm16Data[i].toInt() and 0xFF) or (pcm16Data[i + 1].toInt() shl 8)
            val normalized = sample.toShort().toDouble() / 32768.0
            sum += normalized * normalized
        }
        val sampleCount = pcm16Data.size / 2
        return if (sampleCount > 0) kotlin.math.sqrt(sum / sampleCount) else 0.0
    }

    private fun resamplePcm16(input: ByteArray, inputRate: Int, outputRate: Int): ByteArray {
        if (inputRate == outputRate) return input

        val inputSamples = input.size / 2
        val outputSamples = (inputSamples.toLong() * outputRate / inputRate).toInt()
        val output = ByteArray(outputSamples * 2)

        for (i in 0 until outputSamples) {
            val srcPos = i.toDouble() * (inputSamples - 1) / (outputSamples - 1).coerceAtLeast(1)
            val srcIndex = srcPos.toInt().coerceIn(0, inputSamples - 2)
            val frac = srcPos - srcIndex

            val s0 = readPcm16Sample(input, srcIndex)
            val s1 = readPcm16Sample(input, srcIndex + 1)
            val interpolated = (s0 + (s1 - s0) * frac).toInt().coerceIn(-32768, 32767).toShort()

            output[i * 2] = (interpolated.toInt() and 0xFF).toByte()
            output[i * 2 + 1] = (interpolated.toInt() shr 8).toByte()
        }

        return output
    }

    private fun readPcm16Sample(data: ByteArray, sampleIndex: Int): Double {
        val byteIndex = sampleIndex * 2
        val value = (data[byteIndex].toInt() and 0xFF) or (data[byteIndex + 1].toInt() shl 8)
        return value.toShort().toDouble()
    }
}
