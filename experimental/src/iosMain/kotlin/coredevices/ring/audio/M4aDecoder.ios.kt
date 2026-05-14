package coredevices.ring.audio

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UIntVar
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
import platform.AudioToolbox.ExtAudioFileDispose
import platform.AudioToolbox.ExtAudioFileGetProperty
import platform.AudioToolbox.ExtAudioFileOpenURL
import platform.AudioToolbox.ExtAudioFileRead
import platform.AudioToolbox.ExtAudioFileRefVar
import platform.AudioToolbox.ExtAudioFileSetProperty
import platform.AudioToolbox.kExtAudioFileProperty_ClientDataFormat
import platform.AudioToolbox.kExtAudioFileProperty_FileDataFormat
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.kAudioFormatLinearPCM
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
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.posix.memcpy

actual class M4aDecoder {
    companion object {
        private val logger = Logger.withTag("M4aDecoder")
        private const val READ_FRAMES_PER_CHUNK = 4096
    }

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun decode(m4aBytes: ByteArray): DecodedAudio =
        withContext(Dispatchers.IO) {
            val tempDir = NSTemporaryDirectory()
            val tempFilePath = "$tempDir/decode_audio_${NSUUID().UUIDString}.m4a"

            logger.d { "Decoding ${m4aBytes.size} bytes M4A" }

            // Write M4A bytes to temp file via NSData
            val nsData = m4aBytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = m4aBytes.size.toULong())
            }
            nsData.writeToFile(tempFilePath, true)

            try {
                val collected = ArrayList<Short>(m4aBytes.size * 4)
                var sampleRate = 0

                memScoped {
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

                    val extFileRef = alloc<ExtAudioFileRefVar>()
                    var status = ExtAudioFileOpenURL(cfUrl, extFileRef.ptr)
                    CFRelease(cfUrl)
                    if (status != 0) throw Exception("ExtAudioFileOpenURL failed: $status")

                    val extFile =
                        extFileRef.value ?: throw Exception("ExtAudioFile is null after open")

                    try {
                        // Read source format to learn original sample rate
                        val sourceFormat = alloc<AudioStreamBasicDescription>()
                        val sizeVar = alloc<UIntVar>()
                        sizeVar.value = sizeOf<AudioStreamBasicDescription>().toUInt()
                        status =
                            ExtAudioFileGetProperty(
                                inExtAudioFile = extFile,
                                inPropertyID = kExtAudioFileProperty_FileDataFormat,
                                ioPropertyDataSize = sizeVar.ptr,
                                outPropertyData = sourceFormat.ptr
                            )
                        if (status != 0)
                            throw Exception("Get FileDataFormat failed: $status")
                        sampleRate = sourceFormat.mSampleRate.toInt()

                        // Set client format: PCM Int16 mono — ExtAudioFile decodes for us
                        val clientASBD =
                            alloc<AudioStreamBasicDescription>().apply {
                                mSampleRate = sourceFormat.mSampleRate
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
                        status =
                            ExtAudioFileSetProperty(
                                inExtAudioFile = extFile,
                                inPropertyID = kExtAudioFileProperty_ClientDataFormat,
                                inPropertyDataSize =
                                    sizeOf<AudioStreamBasicDescription>().toUInt(),
                                inPropertyData = clientASBD.ptr
                            )
                        if (status != 0)
                            throw Exception("Set ClientDataFormat failed: $status")

                        // Allocate a chunk PCM buffer and read in a loop until 0 frames returned
                        val chunkFormat =
                            AVAudioFormat(
                                commonFormat = AVAudioPCMFormatInt16,
                                sampleRate = sourceFormat.mSampleRate,
                                channels = 1u,
                                interleaved = true
                            )
                        val chunkBuffer =
                            AVAudioPCMBuffer(
                                pCMFormat = chunkFormat,
                                frameCapacity = READ_FRAMES_PER_CHUNK.toUInt()
                            )
                                ?: throw Exception("Failed to create PCM buffer")

                        val frameCountVar = alloc<UIntVar>()
                        while (true) {
                            frameCountVar.value = READ_FRAMES_PER_CHUNK.toUInt()
                            status =
                                ExtAudioFileRead(
                                    inExtAudioFile = extFile,
                                    ioNumberFrames = frameCountVar.ptr,
                                    ioData = chunkBuffer.audioBufferList
                                )
                            if (status != 0)
                                throw Exception("ExtAudioFileRead failed: $status")
                            val framesRead = frameCountVar.value.toInt()
                            if (framesRead == 0) break

                            val int16Channel =
                                chunkBuffer.int16ChannelData?.get(0)
                                    ?: throw Exception("No int16ChannelData")
                            val chunkSamples = ShortArray(framesRead)
                            chunkSamples.usePinned { pinned ->
                                memcpy(
                                    pinned.addressOf(0),
                                    int16Channel,
                                    (framesRead * 2).toULong()
                                )
                            }
                            for (s in chunkSamples) collected.add(s)
                        }
                    } finally {
                        ExtAudioFileDispose(extFile)
                    }
                }

                val samples = ShortArray(collected.size) { collected[it] }
                logger.d { "Decoded to ${samples.size} samples at ${sampleRate}Hz" }
                DecodedAudio(samples, sampleRate)
            } finally {
                NSFileManager.defaultManager.removeItemAtPath(tempFilePath, null)
            }
        }
}