package coredevices.pebble.services

import co.touchlab.kermit.Logger
import com.cactus.CactusSTT
import com.cactus.SpeechRecognitionParams
import com.russhwolf.settings.Settings
import coredevices.speex.SpeexCodec
import coredevices.speex.SpeexDecodeResult
import coredevices.util.CactusSTTMode
import coredevices.util.CommonBuildKonfig
import io.ktor.utils.io.CancellationException
import io.rebble.libpebblecommon.voice.TranscriptionProvider
import io.rebble.libpebblecommon.voice.TranscriptionResult
import io.rebble.libpebblecommon.voice.TranscriptionWord
import io.rebble.libpebblecommon.voice.VoiceEncoderInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeIntLe
import kotlinx.io.writeShortLe
import kotlinx.io.writeString
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

internal expect fun tempTranscriptionDirectory(): Path
class CactusTranscription(private val settings: Settings): TranscriptionProvider {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val logger = Logger.withTag("CactusTranscription")
    private var sttModel = CactusSTT(com.cactus.TranscriptionProvider.WHISPER)

    private var initJob: Job? = null

    private val sttMode get() = CactusSTTMode.fromId(settings.getInt("cactus_mode", 0))
    private val sttModelName get() = settings.getString("cactus_stt_model", CommonBuildKonfig.CACTUS_DEFAULT_STT_MODEL)
    private var lastInitedModel: String? = null

    private fun writeWavHeader(sink: Sink, sampleRate: Int, audioSize: Int) {
        val chunkSize = audioSize + 36
        sink.writeString("RIFF")
        sink.writeIntLe(chunkSize)
        sink.writeString("WAVE")
        sink.writeString("fmt ")
        sink.writeIntLe(16) // fmt chunk size
        sink.writeShortLe(1) // PCM format
        sink.writeShortLe(1) // Mono
        sink.writeIntLe(sampleRate) // Sample rate
        sink.writeIntLe(sampleRate * 2) // Byte rate
        sink.writeShortLe(2) // Block align
        sink.writeShortLe(16) // Bits per sample
        sink.writeString("data")
        sink.writeIntLe(audioSize)
    }

    private fun performInit(): Job {
        return scope.launch {
            try {
                initIfNeeded()
            } catch (e: Exception) {
                logger.e(e) { "Cactus STT model initialization failed: ${e.message}" }
            }
            if (!sttModel.isReady()) {
                logger.e { "Cactus STT model is not ready after initialization" }
            }
        }
    }

    override suspend fun canServeSession(): Boolean {
        if (sttMode == CactusSTTMode.Disabled) {
            return false
        }
        initJob = performInit() // Pre-init model
        return true
    }

    private suspend fun initIfNeeded() {
        when (sttMode) {
            CactusSTTMode.Disabled -> {}
            CactusSTTMode.Local, CactusSTTMode.RemoteFirst -> {
                if (sttModelName != lastInitedModel) {
                    sttModel.stop()
                    sttModel = CactusSTT(com.cactus.TranscriptionProvider.WHISPER)
                }
                if (!sttModel.isReady()) {
                    if (!sttModel.init(sttModelName)) {
                        throw IllegalStateException("Failed to initialize Cactus STT model")
                    } else {
                        logger.d { "Cactus STT model initialized successfully" }
                        lastInitedModel = sttModelName
                    }
                }
                if (sttMode == CactusSTTMode.RemoteFirst) {
                    CommonBuildKonfig.WISPR_KEY?.let {
                        sttModel.warmUpWispr(it)
                    }
                }
            }
        }

    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override suspend fun transcribe(
        encoderInfo: VoiceEncoderInfo,
        audioFrames: Flow<UByteArray>
    ): TranscriptionResult {
        require(encoderInfo is VoiceEncoderInfo.Speex) {
            "Cactus transcription only supports Speex encoding, got ${encoderInfo::class.simpleName}"
        }
        if (sttMode == CactusSTTMode.Disabled) {
            audioFrames.collect {  }
            return TranscriptionResult.Disabled
        }
        logger.i { "Transcribing with model $sttModelName (mode = ${sttMode.name})" }
        if (initJob == null || !sttModel.isReady() || lastInitedModel != sttModelName) { // Ensure model is initialized
            initJob = performInit()
        }

        val speex = SpeexCodec(
            sampleRate = encoderInfo.sampleRate,
            bitRate = encoderInfo.bitRate,
            frameSize = encoderInfo.frameSize
        )
        return try {
            withContext(Dispatchers.IO) {
                val tempDir = tempTranscriptionDirectory()
                val tempFile = Path(tempDir, "transcription-${Uuid.random()}.wav")
                try {
                    SystemFileSystem.createDirectories(tempDir, false)
                    SystemFileSystem.sink(tempFile).buffered().use { sink ->
                        val pcm = ByteArray(encoderInfo.frameSize * Short.SIZE_BYTES)
                        val buf = Buffer()
                        audioFrames.collect { frame ->
                            val result =
                                speex.decodeFrame(frame.asByteArray(), pcm, hasHeaderByte = true)
                            if (result != SpeexDecodeResult.Success) {
                                error("Failed to decode Speex frame: $result")
                            }
                            buf.write(pcm)
                        }
                        // Write WAV header
                        writeWavHeader(sink, encoderInfo.sampleRate.toInt(), buf.size.toInt())
                        // Write audio data
                        sink.transferFrom(buf)
                    }
                    logger.d { "Transcription audio saved to: $tempFile" }
                    try {
                        withTimeout(4.seconds) { // Wait up to 4 seconds for init complete, otherwise assume it's stuck
                            initJob?.join()
                        }
                    } catch (_: TimeoutCancellationException) {
                        logger.e { "Cactus STT model initialization timed out" }
                        return@withContext TranscriptionResult.Error("Cactus STT model initialization timed out")
                    }

                    val transcription = sttModel.transcribe(
                        SpeechRecognitionParams(),
                        tempFile.toString(),
                        sttMode.cactusValue,
                        CommonBuildKonfig.WISPR_KEY
                    )
                    logger.d { "Transcription complete, success = ${transcription?.success}" }
                    return@withContext transcription?.let { transcription ->
                        if (transcription.success) {
                            TranscriptionResult.Success(
                                words = transcription.text?.trim()?.split(" ")?.map {
                                    TranscriptionWord(
                                        word = it,
                                        confidence = 0.9f
                                    )
                                } ?: emptyList()
                            )
                        } else {
                            logger.d { "Transcription failed: ${transcription.text}" }
                            TranscriptionResult.Success(
                                words = emptyList()
                            )
                        }
                    } ?: run {
                        logger.d { "transcribeFile() returned null" }
                        TranscriptionResult.Failed
                    }
                } finally {
                    SystemFileSystem.delete(tempFile, mustExist = false)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Error during transcription: ${e.message}" }
            TranscriptionResult.Error(e.message ?: "Unknown error during transcription")
        } finally {
            speex.close()
        }
    }
}