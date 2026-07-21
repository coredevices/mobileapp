package coredevices.pebble.services

import co.touchlab.kermit.Logger
import coredevices.speex.SpeexCodec
import coredevices.speex.SpeexDecodeResult
import coredevices.util.CoreConfigFlow
import coredevices.util.dictation.PebbleDictationSink
import coredevices.util.models.CactusSTTMode
import coredevices.util.transcription.CactusTranscriptionService
import coredevices.util.transcription.TranscriptionException
import io.ktor.utils.io.CancellationException
import io.rebble.libpebblecommon.voice.TranscriptionProvider
import io.rebble.libpebblecommon.voice.TranscriptionResult
import io.rebble.libpebblecommon.voice.TranscriptionWord
import io.rebble.libpebblecommon.voice.VoiceEncoderInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

/**
 * Mode-aware [TranscriptionProvider] dispatching between [HybridTranscription] (cloud/local via
 * Cactus + WisprFlow) and [RebbleAsrTranscription] (Rebble's ASR). Owns fallback orchestration
 * for `RebbleFirst`/`RebbleFallback` modes (which buffer Speex frames so they can be replayed
 * across both backends).
 *
 * When [dictationSink] is set and matches a session's `appUuid`, that session is diverted away
 * from ASR entirely: its Speex audio is decoded (reusing [decodeSpeex]) and handed to the sink
 * instead of being transcribed, and this class reports [TranscriptionResult.Success] with no
 * words so the watch gets its normal end-of-session ack with no transcript.
 */
class STTRouter(
    private val cactus: HybridTranscription,
    private val rebble: RebbleAsrTranscription,
    private val cactusService: CactusTranscriptionService,
    private val coreConfigFlow: CoreConfigFlow,
    private val dictationSink: PebbleDictationSink? = null,
) : TranscriptionProvider {
    companion object {
        private val logger = Logger.withTag("STTRouter")
        private val rebbleModes = setOf(
            CactusSTTMode.RebbleOnly,
            CactusSTTMode.RebbleFirst,
            CactusSTTMode.RebbleFallback,
        )
    }

    override suspend fun canServeSession(): Boolean {
        val mode = coreConfigFlow.value.sttConfig.mode
        return when (mode) {
            CactusSTTMode.RebbleOnly -> rebble.isAvailable()
            CactusSTTMode.RebbleFirst, CactusSTTMode.RebbleFallback ->
                rebble.isAvailable() || cactus.canServeSession()
            else -> cactus.canServeSession()
        }
    }

    override suspend fun canServeSession(appUuid: Uuid): Boolean {
        if (dictationSink?.canIngest(appUuid) == true) return true
        return canServeSession()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override suspend fun transcribe(
        encoderInfo: VoiceEncoderInfo,
        audioFrames: Flow<UByteArray>,
        isNotificationReply: Boolean,
        appUuid: Uuid,
    ): TranscriptionResult {
        if (dictationSink?.canIngest(appUuid) == true) {
            require(encoderInfo is VoiceEncoderInfo.Speex) {
                "Pebble dictation intercept only supports Speex encoding, got ${encoderInfo::class.simpleName}"
            }
            val pcm = decodeSpeex(encoderInfo, audioFrames.toList())
            dictationSink.ingest(appUuid, pcm, encoderInfo.sampleRate.toInt())
            return TranscriptionResult.Success(emptyList())
        }
        return transcribe(encoderInfo, audioFrames, isNotificationReply)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override suspend fun transcribe(
        encoderInfo: VoiceEncoderInfo,
        audioFrames: Flow<UByteArray>,
        isNotificationReply: Boolean
    ): TranscriptionResult {
        val mode = coreConfigFlow.value.sttConfig.mode
        if (mode !in rebbleModes) {
            return cactus.transcribe(encoderInfo, audioFrames, isNotificationReply)
        }

        require(encoderInfo is VoiceEncoderInfo.Speex) {
            "Rebble routing only supports Speex encoding, got ${encoderInfo::class.simpleName}"
        }

        val frames: List<UByteArray> = audioFrames.toList()
        if (frames.isEmpty()) {
            return TranscriptionResult.Error("No audio frames received")
        }

        return when (mode) {
            CactusSTTMode.RebbleOnly -> rebble.transcribe(
                encoderInfo,
                flowOf(*frames.toTypedArray()),
                isNotificationReply,
            )
            CactusSTTMode.RebbleFirst -> {
                val rebbleResult = rebble.transcribe(
                    encoderInfo,
                    flowOf(*frames.toTypedArray()),
                    isNotificationReply,
                )
                if (rebbleResult is TranscriptionResult.Success && rebbleResult.words.isNotEmpty()) {
                    return rebbleResult
                }
                logger.w { "Rebble ASR returned $rebbleResult, falling back to local" }
                runLocalFromSpeex(encoderInfo, frames)
            }
            CactusSTTMode.RebbleFallback -> {
                val localResult = runLocalFromSpeex(encoderInfo, frames)
                if (localResult is TranscriptionResult.Success && localResult.words.isNotEmpty()) {
                    return localResult
                }
                logger.w { "Local STT returned $localResult, falling back to Rebble" }
                rebble.transcribe(
                    encoderInfo,
                    flowOf(*frames.toTypedArray()),
                    isNotificationReply,
                )
            }
            else -> TranscriptionResult.Error("Unhandled mode $mode")
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun runLocalFromSpeex(
        encoderInfo: VoiceEncoderInfo.Speex,
        frames: List<UByteArray>,
    ): TranscriptionResult {
        val pcm = decodeSpeex(encoderInfo, frames)
        return try {
            val text = cactusService.transcribeLocalForFallback(
                audio = pcm,
                sampleRate = encoderInfo.sampleRate.toInt(),
            )
            TranscriptionResult.Success(
                words = text.trim().split(" ").map { TranscriptionWord(it, 0.9f) }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: TranscriptionException.NoSpeechDetected) {
            TranscriptionResult.Success(emptyList())
        } catch (_: TranscriptionException.TranscriptionRequiresDownload) {
            TranscriptionResult.Disabled
        } catch (e: Exception) {
            TranscriptionResult.Error("Local transcription failed: ${e.message}")
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun decodeSpeex(
        encoderInfo: VoiceEncoderInfo.Speex,
        frames: List<UByteArray>,
    ): ByteArray {
        val speex = SpeexCodec(
            sampleRate = encoderInfo.sampleRate,
            bitRate = encoderInfo.bitRate,
            frameSize = encoderInfo.frameSize,
        )
        val pcm = ByteArray(encoderInfo.frameSize * Short.SIZE_BYTES)
        val output = ArrayList<Byte>(frames.size * pcm.size)
        withContext(Dispatchers.IO) {
            for (frame in frames) {
                val result = speex.decodeFrame(frame.asByteArray(), pcm, hasHeaderByte = true)
                if (result != SpeexDecodeResult.Success) {
                    error("Failed to decode Speex frame: $result")
                }
                for (b in pcm) output.add(b)
            }
        }
        return output.toByteArray()
    }
}
