package coredevices.ring.audio

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AudioToolbox.ExtAudioFileCreateWithURL
import platform.AudioToolbox.ExtAudioFileDispose
import platform.AudioToolbox.ExtAudioFileRefVar
import platform.AudioToolbox.ExtAudioFileSetProperty
import platform.AudioToolbox.ExtAudioFileWrite
import platform.AudioToolbox.kAudioFileFlags_EraseFile
import platform.AudioToolbox.kAudioFileM4AType
import platform.AudioToolbox.kExtAudioFileProperty_ClientDataFormat
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.CoreAudioTypes.kLinearPCMFormatFlagIsPacked
import platform.CoreAudioTypes.kLinearPCMFormatFlagIsSignedInteger
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFURLCreateWithFileSystemPath
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFURLPOSIXPathStyle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

actual class M4aEncoder {
    companion object {
        private val logger = Logger.withTag("M4aEncoder")
    }

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun encode(samples: ShortArray, sampleRate: Int): ByteArray =
            withContext(Dispatchers.IO) {
                val tempDir = NSTemporaryDirectory()
                val tempFilePath = "$tempDir/temporary_audio_${NSUUID().UUIDString}.m4a"

                logger.d { "Encoding ${samples.size} samples at ${sampleRate}Hz to M4A" }

                try {
                    memScoped {
                        // Output format: AAC in M4A container
                        val outputASBD =
                                alloc<AudioStreamBasicDescription>().apply {
                                    mSampleRate = sampleRate.toDouble()
                                    mFormatID = kAudioFormatMPEG4AAC
                                    mChannelsPerFrame = 1u
                                }

                        // Input format: 16-bit signed integer PCM
                        val inputASBD =
                                alloc<AudioStreamBasicDescription>().apply {
                                    mSampleRate = sampleRate.toDouble()
                                    mFormatID = kAudioFormatLinearPCM
                                    mFormatFlags =
                                            (kLinearPCMFormatFlagIsSignedInteger or
                                                            kLinearPCMFormatFlagIsPacked)
                                                    .toUInt()
                                    mBytesPerPacket = 2u
                                    mFramesPerPacket = 1u
                                    mBytesPerFrame = 2u
                                    mChannelsPerFrame = 1u
                                    mBitsPerChannel = 16u
                                }

                        // Create CFURL from file path (NSURL can't be passed directly to C APIs)
                        val cfStr =
                                CFStringCreateWithCString(null, tempFilePath, kCFStringEncodingUTF8)
                        val cfUrl =
                                CFURLCreateWithFileSystemPath(
                                        null,
                                        cfStr,
                                        kCFURLPOSIXPathStyle,
                                        false
                                )
                        CFRelease(cfStr)

                        // Create ExtAudioFile for writing
                        val extFileRef = alloc<ExtAudioFileRefVar>()
                        var status =
                                ExtAudioFileCreateWithURL(
                                        inURL = cfUrl,
                                        inFileType = kAudioFileM4AType,
                                        inStreamDesc = outputASBD.ptr,
                                        inChannelLayout = null,
                                        inFlags = kAudioFileFlags_EraseFile,
                                        outExtAudioFile = extFileRef.ptr
                                )
                        CFRelease(cfUrl)
                        if (status != 0)
                                throw Exception("ExtAudioFileCreateWithURL failed: $status")

                        val extFile =
                                extFileRef.value
                                        ?: throw Exception("ExtAudioFile is null after creation")

                        try {
                            // Tell ExtAudioFile our input (client) format is PCM;
                            // it will convert to AAC automatically on write.
                            status =
                                    ExtAudioFileSetProperty(
                                            inExtAudioFile = extFile,
                                            inPropertyID = kExtAudioFileProperty_ClientDataFormat,
                                            inPropertyDataSize =
                                                    sizeOf<AudioStreamBasicDescription>().toUInt(),
                                            inPropertyData = inputASBD.ptr
                                    )
                            if (status != 0)
                                    throw Exception("ExtAudioFileSetProperty failed: $status")

                            // Create PCM buffer and copy samples into it
                            val inputFormat =
                                    AVAudioFormat(
                                            commonFormat = AVAudioPCMFormatInt16,
                                            sampleRate = sampleRate.toDouble(),
                                            channels = 1u,
                                            interleaved = true
                                    )
                            val pcmBuffer =
                                    AVAudioPCMBuffer(
                                            pCMFormat = inputFormat,
                                            frameCapacity = samples.size.toUInt()
                                    )
                                            ?: throw Exception("Failed to create PCM buffer")
                            pcmBuffer.setFrameLength(samples.size.toUInt())

                            val int16ChannelData = pcmBuffer.int16ChannelData
                            if (int16ChannelData != null) {
                                val channelData = int16ChannelData[0]
                                if (channelData != null) {
                                    samples.usePinned { pinned ->
                                        memcpy(
                                                channelData,
                                                pinned.addressOf(0),
                                                (samples.size * 2).toULong()
                                        )
                                    }
                                }
                            }

                            // Write PCM samples — ExtAudioFile encodes to AAC internally
                            status =
                                    ExtAudioFileWrite(
                                            inExtAudioFile = extFile,
                                            inNumberFrames = samples.size.toUInt(),
                                            ioData = pcmBuffer.audioBufferList
                                    )
                            if (status != 0) throw Exception("ExtAudioFileWrite failed: $status")
                        } finally {
                            // Explicitly finalize and close — writes the moov atom
                            ExtAudioFileDispose(extFile)
                        }
                    }

                    // Read the finalized M4A file
                    val fileData =
                            NSData.dataWithContentsOfFile(tempFilePath)
                                    ?: throw Exception("Failed to read encoded file")

                    val result = ByteArray(fileData.length.toInt())
                    if (result.isNotEmpty()) {
                        result.usePinned { pinned ->
                            memcpy(pinned.addressOf(0), fileData.bytes, fileData.length)
                        }
                    }

                    logger.d { "Encoded to ${result.size} bytes M4A" }

                    result
                } finally {
                    // Clean up temp file
                    NSFileManager.defaultManager.removeItemAtPath(tempFilePath, null)
                }
            }
}
