@file:OptIn(ExperimentalTime::class)

package coredevices.indexai.data.entity

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.firestore.BaseTimestampSerializer
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Drop-in replacement for `kotlinx.serialization.builtins.InstantComponentSerializer`
 * that survives the two underlying Firestore representations our data has
 * accreted across years of writers:
 *
 *   1. **Native Firestore Timestamp.** Older documents (and anything written
 *      from iOS) store `timestamp` as `com.google.firebase.Timestamp`. The
 *      gitlive Firebase SDK exposes that to kotlinx-serialization via
 *      `SpecialValueSerializer` — it casts the decoder to its internal
 *      `FirebaseDecoderImpl`, calls `.getValue()`, and hands back the raw
 *      `Timestamp` instance. We reach the same path by delegating to
 *      gitlive's [BaseTimestampSerializer], which encapsulates the cast.
 *
 *   2. **kotlinx-serialization map shape** `{epochSeconds, nanosecondsOfSecond}`.
 *      The shape this codebase produces on writes; standard
 *      `decodeStructure` works for these.
 *
 * Read side tries the native Timestamp path first; on the
 * `SerializationException` thrown when the value isn't a Timestamp, falls
 * back to the kotlinx struct decode. Last-resort sentinel is
 * `Instant.fromEpochSeconds(0,0)` so a malformed doc still decodes (the
 * row will sort to the bottom of the feed instead of stranding the entire
 * recording on one bad timestamp field).
 *
 * Write side emits the kotlinx map shape — the format gitlive expects from
 * a `KSerializer<Instant>` on a non-Timestamp field.
 */
object TolerantInstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("kotlin.time.Instant") {
        element<Long>("epochSeconds")
        element<Int>("nanosecondsOfSecond", isOptional = true)
    }

    override fun deserialize(decoder: Decoder): Instant {
        // Path 1: native Firestore Timestamp via gitlive's
        // SpecialValueSerializer mechanism. BaseTimestampSerializer
        // checks `decoder is FirebaseDecoderImpl`, calls `.getValue()`
        // to read the raw native value (non-consuming property access),
        // and runs gitlive's "is this a Timestamp?" lambda. If the field
        // really is a native `com.google.firebase.Timestamp`, we get a
        // wrapped [Timestamp] back. Anything else throws
        // SerializationException and we fall through to the struct-decode
        // path below.
        try {
            val base = BaseTimestampSerializer.deserialize(decoder)
            if (base is Timestamp) {
                return Instant.fromEpochSeconds(base.seconds, base.nanoseconds)
            }
        } catch (_: Throwable) {
            // Not a native Timestamp — fall through.
        }

        // Path 2: kotlinx-shape struct `{epochSeconds, nanosecondsOfSecond}`.
        // Safe to invoke decodeStructure here because the path-1 attempt
        // only does a property read on the decoder; it doesn't open a
        // sub-decoder.
        var epochSeconds = 0L
        var nanos = 0
        var sawAny = false
        try {
            decoder.decodeStructure(descriptor) {
                while (true) {
                    val idx = try {
                        decodeElementIndex(descriptor)
                    } catch (_: Throwable) {
                        CompositeDecoder.DECODE_DONE
                    }
                    if (idx == CompositeDecoder.DECODE_DONE) break
                    try {
                        when (idx) {
                            0 -> { epochSeconds = decodeLongElement(descriptor, 0); sawAny = true }
                            1 -> { nanos = decodeIntElement(descriptor, 1); sawAny = true }
                            else -> { /* unknown element — skip */ }
                        }
                    } catch (_: Throwable) {
                        return@decodeStructure
                    }
                }
            }
        } catch (_: Throwable) {
            // Top-level decode failed — last-resort sentinel.
        }

        if (!sawAny) {
            Logger.withTag("TolerantInstant")
                .w { "neither native Timestamp nor kotlinx struct shape; using epoch-0 sentinel" }
        }
        return Instant.fromEpochSeconds(epochSeconds, nanos)
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeStructure(descriptor) {
            encodeLongElement(descriptor, 0, value.epochSeconds)
            encodeIntElement(descriptor, 1, value.nanosecondsOfSecond)
        }
    }
}
