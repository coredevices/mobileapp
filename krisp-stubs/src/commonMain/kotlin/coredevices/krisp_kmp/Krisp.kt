package coredevices.krisp_kmp

object Krisp {
    fun initialize(workingDirectory: String, logDelegate: (String, KrispLogLevel) -> Unit) {}
    fun destroy() {}
    fun getVersion(): String = "0.0.0"
    fun createNcSession(config: KrispNcSessionConfig): KrispNcSession = throw NotImplementedError("Krisp disabled")
}

class KrispNcSession: AutoCloseable {
    fun process(input: ShortArray, output: ShortArray, noiseSuppressionLevel: Float) {
        throw NotImplementedError("Krisp disabled")
    }
    override fun close() {}
}

data class KrispNcSessionConfig(
    val inputSampleRate: KrispSampleRate,
    val inputFrameDuration: KrispInputFrameDuration,
    val outputSampleRate: KrispSampleRate,
    val modelBlob: ByteArray,
    val enableSessionStats: Boolean = false
)

enum class KrispInputFrameDuration(val durationMs: Int) {
    Fd10ms(10),
    Fd15ms(15),
    Fd20ms(20),
    Fd30ms(30),
    Fd32ms(32)
}

enum class KrispSampleRate(val sampleRate: Int) {
    Sr8000Hz(8000),
    Sr16000Hz(16000),
    Sr24000Hz(24000),
    Sr32000Hz(32000),
    Sr44100Hz(44100),
    Sr48000Hz(48000),
    Sr88200Hz(88200),
    Sr96000Hz(96000)
}

enum class KrispLogLevel {
    LogLevelTrace,
    LogLevelDebug,
    LogLevelInfo,
    LogLevelWarn,
    LogLevelErr,
    LogLevelCritical,
    LogLevelOff;

    val nativeValue = this.ordinal.toUInt()

    companion object {
        fun fromNativeValue(value: UInt): KrispLogLevel {
            return entries.first { it.nativeValue == value }
        }
    }
}