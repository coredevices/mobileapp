package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.*
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian


internal sealed class IncomingVoicePacket() : PebblePacket(ProtocolEndpoint.VOICE_CONTROL) {
    /**
     * Voice command. See [VoiceCommand].
     */
    val command: SUByte = SUByte(m)
    val flags: SUInt = SUInt(m, endianness = Endian.Little)
}
internal sealed class OutgoingVoicePacket(command: VoiceCommand) :
    PebblePacket(ProtocolEndpoint.VOICE_CONTROL) {
    /**
     * Voice command. See [VoiceCommand].
     */
    val command: SUByte = SUByte(m, command.value)
    val flags: SUInt = SUInt(m, endianness = Endian.Little)
}

internal enum class VoiceCommand(val value: UByte) {
    SessionSetup(0x01u),
    DictationResult(0x02u),
}

internal class Word internal constructor(confidence: UByte = 0u, data: String = "") : StructMappable() {
    private val utf8Size = data.encodeToByteArray().size.coerceAtMost(UShort.MAX_VALUE.toInt())
    internal val confidence: SUByte = SUByte(m, confidence)
    internal val length: SUShort = SUShort(m, utf8Size.toUShort(), endianness = Endian.Little)
    internal val data: SFixedString = SFixedString(m, utf8Size, data)
    init {
        this.data.linkWithSize(length)
    }
}

internal class Sentence internal constructor(words: List<Word> = emptyList()) : StructMappable() {
    internal val wordCount: SUShort = SUShort(m, words.size.toUShort(), endianness = Endian.Little)
    internal val words: SFixedList<Word> = SFixedList(m, words.size, words) { Word() }
    init {
        this.words.linkWithCount(wordCount)
    }
}

internal enum class VoiceAttributeType(val value: UByte) {
    SpeexEncoderInfo(0x01u),
    Transcription(0x02u),
    AppUuid(0x03u),
}

public open class VoiceAttribute internal constructor(id: UByte = 0u, content: StructMappable? = null) : StructMappable() {
    internal val id: SUByte = SUByte(m, id)
    internal val length: SUShort = SUShort(m, content?.size?.toUShort() ?: 0u, endianness = Endian.Little)
    internal val content: SBytes = SBytes(m, content?.size ?: 0, content?.toBytes() ?: ubyteArrayOf())
    init {
        this.content.linkWithSize(length)
    }

    internal class SpeexEncoderInfo internal constructor() : StructMappable() {
        internal val version: SFixedString = SFixedString(m, 20)
        val sampleRate: SUInt = SUInt(m, endianness = Endian.Little)
        internal val bitRate: SUShort = SUShort(m, endianness = Endian.Little)
        internal val bitstreamVersion: SUByte = SUByte(m)
        internal val frameSize: SUShort = SUShort(m, endianness = Endian.Little)
    }

    internal class Transcription internal constructor(
        type: UByte = 0x1u,
        sentences: List<Sentence> = emptyList()
    ) : StructMappable() {
        internal val type: SUByte = SUByte(m, type) // always 0x1? (sentence list)
        internal val count: SUByte = SUByte(m, sentences.size.toUByte())
        internal val sentences: SFixedList<Sentence> = SFixedList(m, sentences.size, sentences) { Sentence() }
        init {
            this.sentences.linkWithCount(count)
        }
    }

    internal class AppUuid internal constructor() : StructMappable() {
        internal val uuid: SUUID = SUUID(m)
    }
}

/**
 * Voice session setup command. Little endian.
 */
internal class SessionSetupCommand : IncomingVoicePacket() {
    val sessionType: SUByte = SUByte(m)
    val sessionId: SUShort = SUShort(m, endianness = Endian.Little)
    val attributeCount: SUByte = SUByte(m)
    val attributes: SFixedList<VoiceAttribute> = SFixedList(m, 0) {
        VoiceAttribute()
    }
    init {
        attributes.linkWithCount(attributeCount)
    }
}

internal enum class SessionType(val value: UByte) {
    Dictation(0x01u),
    Command(0x02u),
    /** Live audio for an app's companion; no transcription, no result. */
    AudioStream(0x04u),
}

internal enum class Result(val value: UByte) {
    Success(0x0u),
    FailServiceUnavailable(0x1u),
    FailTimeout(0x2u),
    FailRecognizerError(0x3u),
    FailInvalidRecognizerResponse(0x4u),
    FailDisabled(0x5u),
    FailInvalidMessage(0x6u),
}

internal class SessionSetupResult(sessionType: SessionType, result: Result) : OutgoingVoicePacket(VoiceCommand.SessionSetup) {
    val sessionType: SUByte = SUByte(m, sessionType.value)
    val result: SUByte = SUByte(m, result.value)
}

/**
 * [SessionSetupResult] for [SessionType.AudioStream]. Carries the audio endpoint session it
 * answers, so the watch can drop a late answer for a stream it already tore down.
 */
internal class AudioStreamSetupResult(result: Result, sessionId: UShort) : OutgoingVoicePacket(VoiceCommand.SessionSetup) {
    val sessionType: SUByte = SUByte(m, SessionType.AudioStream.value)
    val result: SUByte = SUByte(m, result.value)
    val sessionId: SUShort = SUShort(m, sessionId, endianness = Endian.Little)
}

internal class DictationResult(sessionId: UShort, result: Result, attributes: List<StructMappable>) : OutgoingVoicePacket(VoiceCommand.DictationResult) {
    val sessionId: SUShort = SUShort(m, sessionId, endianness = Endian.Little)
    val result: SUByte = SUByte(m, result.value)
    val attributeCount: SUByte = SUByte(m, attributes.size.toUByte())
    val attributes: SFixedList<StructMappable> = SFixedList(m, attributes.size, attributes) { VoiceAttribute() }
}

internal fun voicePacketsRegister() {
    PacketRegistry.register(ProtocolEndpoint.VOICE_CONTROL, VoiceCommand.SessionSetup.value) {
        SessionSetupCommand()
    }
}