package coredevices.pebble.services

import coredevices.speex.SpeexCodec
import coredevices.speex.SpeexDecodeResult
import coredevices.util.transcription.CactusTranscriptionService
import coredevices.util.transcription.STTLanguage
import coredevices.util.transcription.TranscriptionException
import coredevices.util.transcription.TranscriptionSessionStatus
import io.ktor.utils.io.CancellationException
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.voice.TranscriptionProvider
import io.rebble.libpebblecommon.voice.TranscriptionResult
import io.rebble.libpebblecommon.voice.TranscriptionWord
import io.rebble.libpebblecommon.voice.VoiceEncoderInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.readByteArray

internal expect fun tempTranscriptionDirectory(): Path
class CactusTranscription(
    private val service: CactusTranscriptionService,
    private val libPebbleLazy: Lazy<LibPebble>
): TranscriptionProvider {
    override suspend fun canServeSession(): Boolean {
        service.earlyInit()
        return true
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override suspend fun transcribe(
        encoderInfo: VoiceEncoderInfo,
        audioFrames: Flow<UByteArray>,
        isNotificationReply: Boolean
    ): TranscriptionResult {
        require(encoderInfo is VoiceEncoderInfo.Speex) {
            "Cactus transcription only supports Speex encoding, got ${encoderInfo::class.simpleName}"
        }

        val speex = SpeexCodec(
            sampleRate = encoderInfo.sampleRate,
            bitRate = encoderInfo.bitRate,
            frameSize = encoderInfo.frameSize
        )
        val decodedBuffer = Buffer()
        val pcm = ByteArray(encoderInfo.frameSize * Short.SIZE_BYTES)
        withContext(Dispatchers.IO) {
            audioFrames.collect { frame ->
                val result =
                    speex.decodeFrame(frame.asByteArray(), pcm, hasHeaderByte = true)
                if (result != SpeexDecodeResult.Success) {
                    error("Failed to decode Speex frame: $result")
                }
                decodedBuffer.write(pcm)
            }
        }
        return try {
            val recentContacts = if (isNotificationReply) {
                libPebbleLazy.value.mostRecentNotificationParticipants(limit = 10).first().takeIf { it.isNotEmpty() }?.flatMap {
                    it.split(" ", limit = 2)
                }
            } else null
            val result = service.transcribe(
                audioStreamFrames = flow {
                    val totalBytes = decodedBuffer.size.toInt()
                    val chunkSize = encoderInfo.sampleRate * Short.SIZE_BYTES // 1 second of audio
                    var bytesRead = 0
                    while (bytesRead < totalBytes) {
                        val bytesToRead = minOf(chunkSize.toInt(), totalBytes - bytesRead)
                        val chunk = decodedBuffer.readByteArray(bytesToRead)
                        emit(chunk)
                        bytesRead += bytesToRead
                    }
                }.flowOn(Dispatchers.IO),
                language = STTLanguage.Automatic, //TODO: Allow language selection based on user preference
                contentContext = if (isNotificationReply) "Reply to Instant Message" else null,
                dictionaryContext = recentContacts,
                sampleRate = encoderInfo.sampleRate.toInt(),
                encoding = coredevices.util.AudioEncoding.PCM_16BIT,
            ).filterIsInstance<TranscriptionSessionStatus.Transcription>().first()
            TranscriptionResult.Success(
                words = result.text.trim().split(" ").map {
                    TranscriptionWord(
                        word = it,
                        confidence = 0.9f
                    )
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoSuchElementException) {
            TranscriptionResult.Error("No transcription result received")
        } catch (_: TranscriptionException.NoSpeechDetected) {
            TranscriptionResult.Success(emptyList())
        } catch (_: TranscriptionException.TranscriptionRequiresDownload) {
            TranscriptionResult.Disabled
        } catch (e: TranscriptionException) {
            TranscriptionResult.Error("Transcription failed: ${e.message}")
        } finally {
            decodedBuffer.close()
        }
    }
}