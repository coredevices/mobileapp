package coredevices.ring.util

import co.touchlab.kermit.Logger
import coredevices.util.AudioEncoding
import coredevices.ring.util.IOSAudioUtil
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.EOFException
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.writeShortLe
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionPortBuiltInMic
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.availableInputs
import platform.AVFAudio.setActive
import platform.Foundation.NSError

actual class AudioRecorder : AutoCloseable {
    actual val encoding: AudioEncoding = AudioEncoding.PCM_16BIT
    actual val sampleRate: Int = 16000
    private val session = AVAudioSession.sharedInstance()
    private val engine = AVAudioEngine()
    private val mic = engine.inputNode
    private val logger = Logger.withTag("AudioRecorder")
    var availableChannel = Channel<Unit>()

    @OptIn(BetaInteropApi::class)
    private fun startEngine(): NSError? = memScoped {
        val error = allocPointerTo<ObjCObjectVar<NSError?>>().value
        engine.startAndReturnError(error)
        error?.pointed?.value
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    actual suspend fun startRecording(): RawSource {
        val ioThread = newSingleThreadContext("AudioRecorder-IO")
        withContext(Dispatchers.Main) {
            engine.prepare()
        }
        session.setCategory(AVAudioSessionCategoryRecord, null)
        session.setActive(true, null)
        val builtinMic = session.availableInputs
            ?.firstOrNull { (it as AVAudioSessionPortDescription).portType == AVAudioSessionPortBuiltInMic!! }
                as AVAudioSessionPortDescription?
        checkNotNull(builtinMic) { "Built-in microphone not found" }
        session.setPreferredInput(builtinMic, null)
        val micFormat = withContext(Dispatchers.Main) {
            mic.inputFormatForBus(0u)
        }
        val buf = Buffer()
        val targetFormat = AVAudioFormat(
            AVAudioPCMFormatInt16,
            sampleRate.toDouble(),
            1u,
            false
        )

        mic.installTapOnBus(0u, 1024u, micFormat) { buffer, time ->
            val monoBuf = IOSAudioUtil.convertPCMBuffer(buffer!!, micFormat, targetFormat)
            try {
                val data = monoBuf.int16ChannelData!![0]!!
                runBlocking(ioThread) {
                    for (i in 0u until monoBuf.frameLength) {
                        buf.writeShortLe(data[i.toLong()])
                    }
                }
                runBlocking {
                    availableChannel.send(Unit)
                }
            } catch (e: IOException) {
                logger.i { "Failed to write to buffer: $e, closing engine" }
                mic.removeTapOnBus(0u)
                engine.stop()
                engine.reset()
                session.setActive(false, null)
                availableChannel.close(IOException("Failed to write to buffer"))
                return@installTapOnBus
            }
        }
        val error = withContext(Dispatchers.Main) {
            startEngine()
        }
        check(error == null) { "Failed to start audio engine: ${error?.localizedDescription}" }
        return object : RawSource {
            override fun close() {
                this@AudioRecorder.close()
            }

            override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
                runBlocking {
                    availableChannel.receive()
                }
                return runBlocking(ioThread) {
                    buf.readAtMostTo(sink, byteCount)
                }
            }

        }
    }

    actual suspend fun stopRecording() {
        logger.d { "stopRecording()" }
        availableChannel.close(EOFException("Recording stopped"))
        mic.removeTapOnBus(0u)
        engine.stop()
        engine.reset()
        session.setActive(false, null)
        availableChannel = Channel()
    }

    actual override fun close() {
        logger.d { "close()" }
        runBlocking { stopRecording() }
        engine.finalize()
    }
}