package coredevices.ring.util

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray

/**
 * Parses an M4A (MPEG-4 Audio) container and extracts raw AAC frames.
 *
 * [readADTS] returns a [Source] of ADTS-wrapped AAC frames ready for [AudioPlayer.playAAC].
 */
class M4AReader(source: Source) : AutoCloseable {
    private val data: ByteArray = source.use { it.readByteArray() }

    /** Sample rate in Hz, read from the AudioSampleEntry. */
    val sampleRate: Int

    /** Number of audio channels. */
    val channelCount: Int

    /**
     * AAC Audio Object Type from the ESDS DecoderSpecificInfo.
     * 1 = AAC Main, 2 = AAC-LC (most common), 3 = AAC SSR, 4 = AAC LTP.
     */
    val audioObjectType: Int

    /** Playback duration in milliseconds. */
    val durationMs: Long

    /** Number of AAC frames (samples) in this file. */
    val frameCount: Int get() = sampleSizes.size

    private val sampleSizes: IntArray
    private val sampleOffsets: LongArray

    init {
        val moov = requireBox(0L, data.size.toLong(), "moov")

        val trak = findAudioTrack(moov.bodyStart, moov.bodyEnd)
            ?: error("M4A: no audio track found")

        val mdia = requireBox(trak.bodyStart, trak.bodyEnd, "mdia")
        val mdhd = requireBox(mdia.bodyStart, mdia.bodyEnd, "mdhd")
        val minf = requireBox(mdia.bodyStart, mdia.bodyEnd, "minf")
        val stbl = requireBox(minf.bodyStart, minf.bodyEnd, "stbl")

        // mdhd: compute duration in ms from timescale
        val mdhdVersion = data[mdhd.bodyStart.toInt()].toInt() and 0xFF
        val timescale: Long
        val rawDuration: Long
        if (mdhdVersion == 1) {
            timescale = readU32(mdhd.bodyStart + 20)
            rawDuration = readI64(mdhd.bodyStart + 24)
        } else {
            timescale = readU32(mdhd.bodyStart + 12)
            rawDuration = readU32(mdhd.bodyStart + 16)
        }
        durationMs = if (timescale > 0) rawDuration * 1000L / timescale else 0L

        // stsd -> mp4a: AudioSampleEntry layout after the 8-byte box header:
        //   reserved(6) + data_ref_index(2) + reserved(8) + channelcount(2) +
        //   samplesize(2) + pre_defined(2) + reserved(2) + samplerate_16_16(4)
        val stsd = requireBox(stbl.bodyStart, stbl.bodyEnd, "stsd")
        val mp4a = requireBox(stsd.bodyStart + 8L, stsd.bodyEnd, "mp4a")
        val sampleEntryChannelCount = readU16(mp4a.bodyStart + 16).toInt()
        sampleRate = (readU32(mp4a.bodyStart + 24) shr 16).toInt()

        val esds = findBox(mp4a.bodyStart + 28L, mp4a.bodyEnd, "esds")
        val codecConfig = if (esds != null) parseEsdsAudioSpecificConfig(esds.bodyStart) else null
        audioObjectType = codecConfig?.audioObjectType ?: 2
        channelCount = codecConfig?.channelCount?.takeIf { it > 0 } ?: sampleEntryChannelCount

        // Sample sizes from stsz (fixed or per-entry) or stz2
        val stsz = findBox(stbl.bodyStart, stbl.bodyEnd, "stsz")
        val stz2 = findBox(stbl.bodyStart, stbl.bodyEnd, "stz2")
        sampleSizes = when {
            stsz != null -> parseStsz(stsz.bodyStart)
            stz2 != null -> parseStz2(stz2.bodyStart)
            else -> error("M4A: no stsz or stz2 box")
        }

        // Chunk offsets from stco (32-bit) or co64 (64-bit)
        val stco = findBox(stbl.bodyStart, stbl.bodyEnd, "stco")
        val co64 = findBox(stbl.bodyStart, stbl.bodyEnd, "co64")
        val chunkOffsets = when {
            stco != null -> parseStco(stco.bodyStart)
            co64 != null -> parseCo64(co64.bodyStart)
            else -> error("M4A: no stco or co64 box")
        }

        val stsc = requireBox(stbl.bodyStart, stbl.bodyEnd, "stsc")
        sampleOffsets = buildSampleOffsets(chunkOffsets, parseStsc(stsc.bodyStart), sampleSizes)
    }

    /**
     * Returns a [Source] containing all AAC frames as a continuous ADTS stream.
     * Each frame is prefixed with a 7-byte ADTS header.
     */
    fun readADTS(): Source {
        val freqIndex = freqIndexForSampleRate(sampleRate)
        val profile = (audioObjectType - 1) and 0x3
        val ch = channelCount and 0x7
        val header = ByteArray(7)
        val buf = Buffer()
        for (i in sampleSizes.indices) {
            val frameSize = sampleSizes[i]
            val totalLen = frameSize + 7
            // ADTS header: sync(12) ID=0 layer=00 no-CRC profile(2) freqIdx(4) private(1)
            //              channelCfg(3) copy(1) home(1) cpId(1) cpStart(1) len(13) fill(11) blocks(2)
            header[0] = 0xFF.toByte()
            header[1] = 0xF1.toByte()                                            // MPEG-4, no CRC
            header[2] = ((profile shl 6) or (freqIndex shl 2) or (ch ushr 2)).toByte()
            header[3] = (((ch and 0x3) shl 6) or (totalLen ushr 11)).toByte()
            header[4] = ((totalLen ushr 3) and 0xFF).toByte()
            header[5] = (((totalLen and 0x7) shl 5) or 0x1F).toByte()
            header[6] = 0xFC.toByte()
            buf.write(header)
            buf.write(data, sampleOffsets[i].toInt(), sampleOffsets[i].toInt() + frameSize)
        }
        return buf
    }

    override fun close() {}

    // ── Box navigation ──────────────────────────────────────────────────────────

    private data class Box(val bodyStart: Long, val bodyEnd: Long)

    private fun findBox(from: Long, to: Long, type: String): Box? {
        var pos = from
        while (pos + 8 <= to) {
            val rawSize = readU32(pos)
            val boxType = readFourCC(pos + 4)
            val (headerLen, bodyLen) = when (rawSize) {
                0L    -> 8L to (to - pos - 8L)
                1L    -> 16L to (readI64(pos + 8) - 16L)
                else  -> 8L to (rawSize - 8L)
            }
            val bodyStart = pos + headerLen
            val bodyEnd   = bodyStart + bodyLen
            if (boxType == type) return Box(bodyStart, bodyEnd)
            pos = bodyEnd
        }
        return null
    }

    private fun requireBox(from: Long, to: Long, type: String): Box =
        findBox(from, to, type) ?: error("M4A: required box '$type' not found")

    /** Finds the first 'trak' box whose 'mdia/hdlr' handler type is 'soun'. */
    private fun findAudioTrack(from: Long, to: Long): Box? {
        var pos = from
        while (pos + 8 <= to) {
            val rawSize = readU32(pos)
            val boxType = readFourCC(pos + 4)
            val (headerLen, bodyLen) = when (rawSize) {
                0L   -> 8L to (to - pos - 8L)
                1L   -> 16L to (readI64(pos + 8) - 16L)
                else -> 8L to (rawSize - 8L)
            }
            val bodyStart = pos + headerLen
            val bodyEnd   = bodyStart + bodyLen
            if (boxType == "trak") {
                val mdia = findBox(bodyStart, bodyEnd, "mdia")
                if (mdia != null) {
                    val hdlr = findBox(mdia.bodyStart, mdia.bodyEnd, "hdlr")
                    // hdlr body: version(1) + flags(3) + pre_defined(4) + handler_type(4)
                    if (hdlr != null && readFourCC(hdlr.bodyStart + 8) == "soun") {
                        return Box(bodyStart, bodyEnd)
                    }
                }
            }
            pos = bodyEnd
        }
        return null
    }

    // ── Sample table parsing ────────────────────────────────────────────────────

    /** stsz body: version(1)+flags(3)+sample_size(4)+sample_count(4)+[entry_size*N] */
    private fun parseStsz(bodyStart: Long): IntArray {
        val fixedSize = readU32(bodyStart + 4).toInt()
        val count = readU32(bodyStart + 8).toInt()
        return if (fixedSize != 0) IntArray(count) { fixedSize }
        else IntArray(count) { i -> readU32(bodyStart + 12 + i * 4).toInt() }
    }

    /** stz2 body: version(1)+flags(3)+reserved(3)+field_size(1)+sample_count(4)+[entries] */
    private fun parseStz2(bodyStart: Long): IntArray {
        val fieldSize = data[(bodyStart + 7).toInt()].toInt() and 0xFF
        val count = readU32(bodyStart + 8).toInt()
        return IntArray(count) { i ->
            when (fieldSize) {
                4  -> { val b = data[(bodyStart + 12 + i / 2).toInt()].toInt() and 0xFF; if (i % 2 == 0) b ushr 4 else b and 0xF }
                8  -> data[(bodyStart + 12 + i).toInt()].toInt() and 0xFF
                16 -> readU16(bodyStart + 12 + i * 2).toInt()
                else -> error("M4A: unsupported stz2 field_size $fieldSize")
            }
        }
    }

    /** stco body: version(1)+flags(3)+entry_count(4)+[chunk_offset(4)*N] */
    private fun parseStco(bodyStart: Long): LongArray {
        val count = readU32(bodyStart + 4).toInt()
        return LongArray(count) { i -> readU32(bodyStart + 8 + i * 4) }
    }

    /** co64 body: version(1)+flags(3)+entry_count(4)+[chunk_offset(8)*N] */
    private fun parseCo64(bodyStart: Long): LongArray {
        val count = readU32(bodyStart + 4).toInt()
        return LongArray(count) { i -> readI64(bodyStart + 8 + i * 8) }
    }

    private data class StscEntry(val firstChunk: Int, val samplesPerChunk: Int)

    /** stsc body: version(1)+flags(3)+entry_count(4)+[first_chunk(4)+samples_per_chunk(4)+desc_idx(4)]*N */
    private fun parseStsc(bodyStart: Long): List<StscEntry> {
        val count = readU32(bodyStart + 4).toInt()
        return List(count) { i ->
            val base = bodyStart + 8 + i * 12
            StscEntry(readU32(base).toInt(), readU32(base + 4).toInt())
        }
    }

    /**
     * Resolves the absolute file offset of every sample by combining chunk offsets
     * (stco/co64), sample-to-chunk mapping (stsc), and sample sizes (stsz).
     */
    private fun buildSampleOffsets(
        chunkOffsets: LongArray,
        stscEntries: List<StscEntry>,
        sampleSizes: IntArray
    ): LongArray {
        val offsets = LongArray(sampleSizes.size)
        var sampleIdx = 0
        for (chunkIdx in chunkOffsets.indices) {
            val chunkNum = chunkIdx + 1  // stsc uses 1-based chunk indices
            val samplesInChunk = stscEntries
                .lastOrNull { it.firstChunk <= chunkNum }
                ?.samplesPerChunk ?: continue
            var byteOffset = chunkOffsets[chunkIdx]
            repeat(samplesInChunk) {
                if (sampleIdx < sampleSizes.size) {
                    offsets[sampleIdx] = byteOffset
                    byteOffset += sampleSizes[sampleIdx]
                    sampleIdx++
                }
            }
        }
        return offsets
    }

    // ── ESDS / AudioSpecificConfig parsing ─────────────────────────────────────

    private data class AudioSpecificConfig(
        val audioObjectType: Int,
        val channelCount: Int
    )

    /**
     * Extracts selected fields from the ESDS box's AudioSpecificConfig.
     * Navigates: ES_Descriptor → DecoderConfigDescriptor → DecoderSpecificInfo.
     * Assumes typical M4A flags (no streamDependence / URL / OCR stream).
     */
    private fun parseEsdsAudioSpecificConfig(bodyStart: Long): AudioSpecificConfig? {
        var pos = bodyStart.toInt() + 4  // skip version(1) + flags(3)
        if (pos >= data.size || data[pos] != 0x03.toByte()) return null
        pos++
        pos += readDescriptorSize(pos)   // skip ES_Descriptor size field
        pos += 3                         // ES_ID(2) + flags(1)

        if (pos >= data.size || data[pos] != 0x04.toByte()) return null
        pos++
        pos += readDescriptorSize(pos)   // skip DecoderConfigDescriptor size field
        pos += 13                        // objectTypeIndication(1) + streamType+bufferSize(4) + maxBitrate(4) + avgBitrate(4)

        if (pos >= data.size || data[pos] != 0x05.toByte()) return null
        pos++
        pos += readDescriptorSize(pos)   // skip DecoderSpecificInfo size field

        if (pos + 1 >= data.size) return null
        val firstByte = data[pos].toInt() and 0xFF
        val secondByte = data[pos + 1].toInt() and 0xFF

        // AudioSpecificConfig starts with audioObjectType(5), samplingFrequencyIndex(4), channelConfiguration(4).
        var aot = firstByte ushr 3
        if (aot == 31) {
            if (pos + 2 >= data.size) return null
            aot = 32 + ((firstByte and 0x07) shl 3) + (secondByte ushr 5)
        }
        val channelCount = (secondByte ushr 3) and 0x0F
        return AudioSpecificConfig(
            audioObjectType = aot.takeIf { it > 0 } ?: 2,
            channelCount = channelCount
        )
    }

    /** Returns the number of bytes consumed by a variable-length MPEG-4 descriptor size field. */
    private fun readDescriptorSize(pos: Int): Int {
        var p = pos
        while (p < data.size && data[p].toInt() and 0x80 != 0) p++
        return p - pos + 1
    }

    // ── Binary read helpers ─────────────────────────────────────────────────────

    private fun readU16(offset: Long): Long {
        val i = offset.toInt()
        return ((data[i].toLong() and 0xFF) shl 8) or (data[i + 1].toLong() and 0xFF)
    }

    private fun readU32(offset: Long): Long {
        val i = offset.toInt()
        return ((data[i].toLong()     and 0xFF) shl 24) or
               ((data[i + 1].toLong() and 0xFF) shl 16) or
               ((data[i + 2].toLong() and 0xFF) shl  8) or
               ((data[i + 3].toLong() and 0xFF))
    }

    private fun readI64(offset: Long): Long {
        val i = offset.toInt()
        return ((data[i].toLong()     and 0xFF) shl 56) or
               ((data[i + 1].toLong() and 0xFF) shl 48) or
               ((data[i + 2].toLong() and 0xFF) shl 40) or
               ((data[i + 3].toLong() and 0xFF) shl 32) or
               ((data[i + 4].toLong() and 0xFF) shl 24) or
               ((data[i + 5].toLong() and 0xFF) shl 16) or
               ((data[i + 6].toLong() and 0xFF) shl  8) or
               ((data[i + 7].toLong() and 0xFF))
    }

    private fun readFourCC(offset: Long): String {
        val i = offset.toInt()
        return buildString(4) {
            append(data[i].toInt().toChar())
            append(data[i + 1].toInt().toChar())
            append(data[i + 2].toInt().toChar())
            append(data[i + 3].toInt().toChar())
        }
    }

    companion object {
        private val SAMPLE_RATES = intArrayOf(
            96000, 88200, 64000, 48000, 44100, 32000,
            24000, 22050, 16000, 12000, 11025, 8000, 7350
        )

        fun freqIndexForSampleRate(rate: Int): Int =
            SAMPLE_RATES.indexOfFirst { it == rate }.takeIf { it >= 0 } ?: 4  // default 44100
    }
}
