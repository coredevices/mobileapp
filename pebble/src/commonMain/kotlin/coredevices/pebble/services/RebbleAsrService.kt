package coredevices.pebble.services

import androidx.compose.ui.text.intl.Locale
import co.touchlab.kermit.Logger
import coredevices.pebble.account.BootConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.rebble.libpebblecommon.voice.TranscriptionResult
import io.rebble.libpebblecommon.voice.TranscriptionWord
import io.rebble.libpebblecommon.voice.VoiceEncoderInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class RebbleAsrService(
    private val httpClient: HttpClient,
) {
    companion object {
        private val logger = Logger.withTag("RebbleAsrService")
        private const val BOUNDARY = "--Nuance_NMSP_vutc5w1XobDdefsYG3wq"
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Pick the [BootConfig.Config.Voice.Language] that best fits the requested ISO 639-1 code.
         * Null/blank code picks the "Auto" endpoint when available; otherwise tries to prefix-match
         * the four-char locale (e.g. "en" -> "en_US" / "en-AU"); falls back to Auto, then the first
         * available entry.
         */
        fun pickLanguage(
            voice: BootConfig.Config.Voice,
            iso639_1Code: String?,
        ): BootConfig.Config.Voice.Language? {
            if (voice.languages.isEmpty()) return null
            val auto = voice.languages.firstOrNull { it.fourCharLocale.equals("Auto", ignoreCase = true) }
            val code = iso639_1Code?.trim()?.lowercase()
            if (code.isNullOrBlank()) return auto ?: voice.languages.first()

            val matches = voice.languages.filter { lang ->
                val locale = lang.fourCharLocale.lowercase()
                locale.startsWith("${code}_") || locale.startsWith("${code}-")
            }
            if (matches.isEmpty()) return auto ?: voice.languages.first()
            if (matches.size == 1) return matches.single()

            val deviceTag = Locale.current.toLanguageTag().lowercase().replace('-', '_')
            val byDeviceRegion = matches.firstOrNull {
                it.fourCharLocale.lowercase().replace('-', '_') == deviceTag
            }
            return byDeviceRegion ?: matches.first()
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    suspend fun transcribe(
        endpointHost: String,
        encoderInfo: VoiceEncoderInfo.Speex,
        fourCharLocale: String,
        audioFrames: Flow<UByteArray>,
    ): TranscriptionResult {
        // The watch prepends a 1-byte frame-quality header to every Speex frame; pyspeex on the
        // rebble-asr server doesn't strip it, so we drop it here before uploading. Without this the
        // server decodes a byte-shifted bitstream and the transcript comes back as gibberish.
        val frames: List<ByteArray> = audioFrames.toList().mapNotNull { frame ->
            val raw = frame.asByteArray()
            if (raw.size <= 1) null else raw.copyOfRange(1, raw.size)
        }
        if (frames.isEmpty()) {
            return TranscriptionResult.Error("No audio frames received")
        }

        val body = buildMultipartBody(
            boundary = BOUNDARY,
            metadataJson = """{"lang":"$fourCharLocale","codec":"speex","sample_rate":${encoderInfo.sampleRate},"frame_size":${encoderInfo.frameSize},"bit_rate":${encoderInfo.bitRate}}""",
            speexFrames = frames,
        )

        val url = "https://$endpointHost/NmspServlet/"

        val response: HttpResponse = try {
            httpClient.post(url) {
                contentType(ContentType.parse("multipart/form-data; boundary=$BOUNDARY"))
                setBody(body)
            }
        } catch (e: Exception) {
            logger.w(e) { "Rebble ASR request failed: ${e.message}" }
            return TranscriptionResult.ConnectionError(e.message ?: "network error")
        }

        return when (response.status) {
            HttpStatusCode.OK -> parseRebbleAsrResponse(response)
            HttpStatusCode.PaymentRequired -> {
                logger.w { "Rebble ASR returned 402 (subscription required)" }
                TranscriptionResult.Error("Rebble subscription required")
            }
            HttpStatusCode.Unauthorized -> {
                logger.w { "Rebble ASR returned 401 (token rejected)" }
                TranscriptionResult.Error("Rebble authentication failed")
            }
            else -> {
                logger.w { "Rebble ASR returned ${response.status}" }
                TranscriptionResult.Error("Rebble ASR failed: ${response.status.value}")
            }
        }
    }

    private fun buildMultipartBody(
        boundary: String,
        metadataJson: String,
        speexFrames: List<ByteArray>,
    ): ByteArray {
        val crlf = "\r\n".encodeToByteArray()
        val partBoundary = "--$boundary\r\n".encodeToByteArray()
        val closingBoundary = "--$boundary--\r\n".encodeToByteArray()
        val metaHeaders = "Content-Disposition: form-data; name=\"MetaData\"\r\nContent-Type: application/JSON; charset=utf-8\r\n\r\n".encodeToByteArray()
        val audioHeaders = "Content-Disposition: form-data; name=\"audio\"\r\nContent-Type: audio/x-speex\r\n\r\n".encodeToByteArray()
        val metaJsonBytes = metadataJson.encodeToByteArray()

        val total = partBoundary.size + metaHeaders.size + metaJsonBytes.size + crlf.size +
                speexFrames.sumOf { partBoundary.size + audioHeaders.size + it.size + crlf.size } +
                closingBoundary.size

        val out = ByteArray(total)
        var pos = 0
        fun append(src: ByteArray) {
            src.copyInto(out, pos)
            pos += src.size
        }

        append(partBoundary)
        append(metaHeaders)
        append(metaJsonBytes)
        append(crlf)

        for (frame in speexFrames) {
            append(partBoundary)
            append(audioHeaders)
            append(frame)
            append(crlf)
        }

        append(closingBoundary)
        return out
    }

    private suspend fun parseRebbleAsrResponse(response: HttpResponse): TranscriptionResult {
        val contentType = response.contentType()
        val boundary = contentType?.parameter("boundary")
        val bodyBytes = response.bodyAsBytes()
        if (boundary == null) {
            logger.w { "Rebble ASR response missing multipart boundary" }
            return TranscriptionResult.Error("Malformed Rebble ASR response")
        }

        val parts = splitMultipart(bodyBytes, boundary)
        for (part in parts) {
            val (headers, content) = splitHeadersBody(part) ?: continue
            val disposition = headers.lineSequence()
                .firstOrNull { it.lowercase().startsWith("content-disposition") }
                ?.lowercase() ?: continue

            if ("queryresult" in disposition) {
                return try {
                    val text = content.decodeToString().trim()
                    val result = json.decodeFromString<RebbleQueryResult>(text)
                    val flat = result.words.flatten()
                    if (flat.isEmpty()) {
                        TranscriptionResult.Success(emptyList())
                    } else {
                        TranscriptionResult.Success(
                            flat.map { w ->
                                TranscriptionWord(
                                    // rebble-asr appends a literal `\*no-space-before` marker to
                                    // the first word (Nuance protocol leftover; tells the watch
                                    // not to insert a leading space). Strip it before emitting.
                                    word = w.word.removeSuffix("\\*no-space-before"),
                                    confidence = w.confidence.toFloatOrNull() ?: 0.9f,
                                )
                            }
                        )
                    }
                } catch (e: Exception) {
                    logger.w(e) { "Failed to parse Rebble ASR QueryResult JSON" }
                    TranscriptionResult.Error("Unparseable Rebble ASR response")
                }
            }
            if ("queryretry" in disposition) {
                logger.d { "Rebble ASR returned QueryRetry (no speech recognized)" }
                return TranscriptionResult.Success(emptyList())
            }
        }
        return TranscriptionResult.Error("Rebble ASR response missing result part")
    }

    private fun splitMultipart(body: ByteArray, boundary: String): List<ByteArray> {
        val delim = "--$boundary".encodeToByteArray()
        val parts = mutableListOf<ByteArray>()
        var searchFrom = 0
        while (true) {
            val start = indexOf(body, delim, searchFrom)
            if (start < 0) break
            var contentStart = start + delim.size
            // Closing boundary -> done
            if (contentStart + 1 < body.size &&
                body[contentStart] == '-'.code.toByte() &&
                body[contentStart + 1] == '-'.code.toByte()
            ) break
            if (contentStart < body.size && body[contentStart] == '\r'.code.toByte()) contentStart++
            if (contentStart < body.size && body[contentStart] == '\n'.code.toByte()) contentStart++

            val next = indexOf(body, delim, contentStart)
            if (next < 0) break
            var contentEnd = next
            if (contentEnd > 0 && body[contentEnd - 1] == '\n'.code.toByte()) contentEnd--
            if (contentEnd > 0 && body[contentEnd - 1] == '\r'.code.toByte()) contentEnd--
            parts.add(body.copyOfRange(contentStart, contentEnd))
            searchFrom = next
        }
        return parts
    }

    private fun splitHeadersBody(part: ByteArray): Pair<String, ByteArray>? {
        val sep = "\r\n\r\n".encodeToByteArray()
        val idx = indexOf(part, sep, 0)
        if (idx < 0) return null
        val headers = part.copyOfRange(0, idx).decodeToString()
        val body = part.copyOfRange(idx + sep.size, part.size)
        return headers to body
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int): Int {
        if (needle.isEmpty() || haystack.size - from < needle.size) return -1
        outer@ for (i in from..(haystack.size - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}

@Serializable
private data class RebbleQueryResult(
    val words: List<List<RebbleWord>> = emptyList(),
)

@Serializable
private data class RebbleWord(
    @SerialName("word") val word: String,
    @SerialName("confidence") val confidence: String,
)
