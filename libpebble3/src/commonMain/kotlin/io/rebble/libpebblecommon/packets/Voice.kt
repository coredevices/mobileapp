package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.*
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian


sealed class IncomingVoicePacket() : PebblePacket(ProtocolEndpoint.VOICE_CONTROL) {
    /**
     * Voice command. See [VoiceCommand].
     */
    val command = SUByte(m)
    val flags = SUInt(m, endianness = Endian.Little)
}
sealed class OutgoingVoicePacket(command: VoiceCommand) :
    PebblePacket(ProtocolEndpoint.VOICE_CONTROL) {
    /**
     * Voice command. See [VoiceCommand].
     */
    val command = SUByte(m, command.value)
    val flags = SUInt(m, endianness = Endian.Little)
}

enum class VoiceCommand(val value: UByte) {
    SessionSetup(0x01u),
    DictationResult(0x02u),
}

class Word(confidence: UByte = 0u, data: String = "") : StructMappable() {
    private val utf8Size = data.encodeToByteArray().size.coerceAtMost(UShort.MAX_VALUE.toInt())
    val confidence = SUByte(m, confidence)
    val length = SUShort(m, utf8Size.toUShort(), endianness = Endian.Little)
    val data = SFixedString(m, utf8Size, data)
    init {
        this.data.linkWithSize(length)
    }
}

class Sentence(words: List<Word> = emptyList()) : StructMappable() {
    val wordCount = SUShort(m, words.size.toUShort(), endianness = Endian.Little)
    val words = SFixedList(m, words.size, words) { Word() }
    init {
        this.words.linkWithCount(wordCount)
    }
}

enum class VoiceAttributeType(val value: UByte) {
    SpeexEncoderInfo(0x01u),
    Transcription(0x02u),
    AppUuid(0x03u),
}

open class VoiceAttribute(id: UByte = 0u, content: StructMappable? = null) : StructMappable() {
    val id = SUByte(m, id)
    val length = SUShort(m, content?.size?.toUShort() ?: 0u, endianness = Endian.Little)
    val content = SBytes(m, content?.size ?: 0, content?.toBytes() ?: ubyteArrayOf())
    init {
        this.content.linkWithSize(length)
    }

    class SpeexEncoderInfo : StructMappable() {
        val version = SFixedString(m, 20)
        val sampleRate = SUInt(m, endianness = Endian.Little)
        val bitRate = SUShort(m, endianness = Endian.Little)
        val bitstreamVersion = SUByte(m)
        val frameSize = SUShort(m, endianness = Endian.Little)
    }

    class Transcription(
        type: UByte = 0x1u,
        sentences: List<Sentence> = emptyList()
    ) : StructMappable() {
        val type = SUByte(m, type) // always 0x1? (sentence list)
        val count = SUByte(m, sentences.size.toUByte())
        val sentences = SFixedList(m, sentences.size, sentences) { Sentence() }
        init {
            this.sentences.linkWithCount(count)
        }
    }

    class AppUuid : StructMappable() {
        val uuid = SUUID(m)
    }
}

/**
 * Voice session setup command. Little endian.
 */
class SessionSetupCommand : IncomingVoicePacket() {
    val sessionType = SUByte(m)
    val sessionId = SUShort(m, endianness = Endian.Little)
    val attributeCount = SUByte(m)
    val attributes = SFixedList(m, 0) {
        VoiceAttribute()
    }
    init {
        attributes.linkWithCount(attributeCount)
    }
}

enum class SessionType(val value: UByte) {
    Dictation(0x01u),
    Command(0x02u),
}

enum class Result(val value: UByte) {
    Success(0x0u),
    FailServiceUnavailable(0x1u),
    FailTimeout(0x2u),
    FailRecognizerError(0x3u),
    FailInvalidRecognizerResponse(0x4u),
    FailDisabled(0x5u),
    FailInvalidMessage(0x6u),
}

class SessionSetupResult(sessionType: SessionType, result: Result) : OutgoingVoicePacket(VoiceCommand.SessionSetup) {
    val sessionType = SUByte(m, sessionType.value)
    val result = SUByte(m, result.value)
}

class DictationResult(sessionId: UShort, result: Result, attributes: List<StructMappable>) : OutgoingVoicePacket(VoiceCommand.DictationResult) {
    val sessionId = SUShort(m, sessionId, endianness = Endian.Little)
    val result = SUByte(m, result.value)
    val attributeCount = SUByte(m, attributes.size.toUByte())
    val attributes = SFixedList(m, attributes.size, attributes) { VoiceAttribute() }
}

fun voicePacketsRegister() {
    PacketRegistry.register(ProtocolEndpoint.VOICE_CONTROL, VoiceCommand.SessionSetup.value) {
        SessionSetupCommand()
    }
}