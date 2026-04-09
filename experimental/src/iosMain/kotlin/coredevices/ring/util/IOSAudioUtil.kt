package coredevices.ring.util

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioConverterInputBlock
import platform.AVFAudio.AVAudioConverterInputStatus_HaveData
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.Foundation.NSError
import kotlin.math.floor

object IOSAudioUtil {
    private val logger = Logger.withTag("IOSAudioUtil")
    fun convertPCMBuffer(source: AVAudioPCMBuffer, sourceFormat: AVAudioFormat, targetFormat: AVAudioFormat): AVAudioPCMBuffer {
        val newSize = floor(targetFormat.sampleRate*source.frameLength.toLong()/sourceFormat.sampleRate).toUInt()
        val targetBuffer = AVAudioPCMBuffer(
            targetFormat,
            newSize
        )
        memScoped {
            val error = allocPointerTo<ObjCObjectVar<NSError?>>().value
            val inputBlock: AVAudioConverterInputBlock = { inNumPackets, outStatus ->
                outStatus?.set(0, AVAudioConverterInputStatus_HaveData)
                source
            }
            val formatConverter = AVAudioConverter(sourceFormat, targetFormat)
            formatConverter.convertToBuffer(targetBuffer, error, inputBlock)
            if (error?.pointed?.value != null) {
                logger.e { "Failed to convert buffer: ${error.pointed.value!!.localizedDescription}" }
            }
        }
        return targetBuffer
    }
}