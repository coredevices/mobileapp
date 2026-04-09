package coredevices.util

import co.touchlab.kermit.Logger
import coredevices.krisp_kmp.Krisp
import coredevices.krisp_kmp.KrispInputFrameDuration
import coredevices.krisp_kmp.KrispLogLevel
import coredevices.krisp_kmp.KrispNcSession
import coredevices.krisp_kmp.KrispNcSessionConfig
import coredevices.krisp_kmp.KrispSampleRate
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory

class KrispAudioProcessor: AutoCloseable {
    companion object {
        private val logger = Logger.withTag("KrispAudioProcessor")
        private var modelBlob: ByteArray? = null
        init {
            Krisp.initialize(Path(SystemTemporaryDirectory, "krisp_temp").toString()) { message, logLevel ->
                when (logLevel) {
                    KrispLogLevel.LogLevelDebug -> logger.d { message }
                    KrispLogLevel.LogLevelInfo -> logger.i { message }
                    KrispLogLevel.LogLevelWarn -> logger.w { message }
                    KrispLogLevel.LogLevelErr -> logger.e { message }
                    KrispLogLevel.LogLevelCritical -> logger.a { message }
                    KrispLogLevel.LogLevelTrace -> logger.v { message }
                    KrispLogLevel.LogLevelOff -> { /* No logging */ }
                }
            }
            logger.i { "Krisp library initialized: ${Krisp.getVersion()}" }
        }
    }

    private lateinit var session: KrispNcSession
    private var krispDisabled = false

    fun init() {
        modelBlob ?: let {
            logger.d { "Loading model blob for the first time" }
            modelBlob = try {
                loadModelBlob()
            } catch (e: Exception) {
                logger.e(e) { "Failed to load model blob, Krisp will be disabled" }
                krispDisabled = true
                return
            }
        }
        session = try {
            Krisp.createNcSession(
                KrispNcSessionConfig(
                    inputSampleRate = inputSampleRate,
                    inputFrameDuration = inputFrameDuration,
                    outputSampleRate = inputSampleRate,
                    modelBlob = modelBlob!!,
                    enableSessionStats = false
                )
            )
        } catch (_: NotImplementedError) {
            logger.w { "Krisp is not enabled" }
            krispDisabled = true
            return
        }
    }

    private val inputSampleRate = KrispSampleRate.Sr16000Hz
    private val inputFrameDuration = KrispInputFrameDuration.Fd20ms

    val samplesPerFrame = (inputSampleRate.sampleRate * inputFrameDuration.durationMs) / 1000

    fun process(input: ShortArray, output: ShortArray) {
        if (krispDisabled) {
            input.copyInto(output)
            return
        }
        session.process(input, output, noiseSuppressionLevel = 95.0f)
    }

    override fun close() {
        if (!krispDisabled)
        {
            session.close()
        }
    }

}

internal expect fun loadModelBlob(): ByteArray