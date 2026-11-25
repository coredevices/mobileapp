package io.rebble.libpebblecommon.connection.bt.ble.ppog

import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.putBytes

internal sealed class PPoGPacket {
  abstract val sequence: Int

  data class Data(
    override val sequence: Int,
    val data: ByteArray,
  ) : PPoGPacket()

  data class Ack(
    override val sequence: Int,
  ) : PPoGPacket()

  data class ResetRequest(
    override val sequence: Int,
    val ppogVersion: PPoGVersion,
  ) : PPoGPacket()

  data class ResetComplete(
    override val sequence: Int,
    val rxWindow: Int,
    val txWindow: Int,
  ) : PPoGPacket()

  fun serialize(ppogVersion: PPoGVersion): ByteArray {
    val size = when (this) {
      is Data -> 1 + data.size
      is ResetComplete -> 1 + (if (ppogVersion.supportsWindowNegotiation) 2 else 0)
      is ResetRequest -> 1 + 1
      is Ack -> 1
    }
    val buffer = DataBuffer(size)

    val sequenceBits = sequence shl 3 and SEQUENCE_MASK
    buffer.putByte((typeMask() or sequenceBits).toByte())

    when (this) {
      is Data -> buffer.putBytes(data)
      is ResetRequest -> {
        buffer.putByte(ppogVersion.version.toByte())
      }
      is ResetComplete -> {
        if (ppogVersion.supportsWindowNegotiation) {
          buffer.putByte(rxWindow.toByte())
          buffer.putByte(txWindow.toByte())
        }
      }
      is Ack -> Unit
    }
    return buffer.array().asByteArray()
  }
}

internal enum class PPoGVersion(
  val version: Int,
  val supportsWindowNegotiation: Boolean,
  val supportsCoalescedAcking: Boolean,
) {
  ZERO(version = 0, supportsWindowNegotiation = false, supportsCoalescedAcking = false),
  ONE(version = 1, supportsWindowNegotiation = true, supportsCoalescedAcking = true),
}

private const val DATA_MASK = 0b000
private const val ACK_MASK = 0b001
private const val RESET_REQUEST_MASK = 0b010
private const val RESET_COMPLETE_MASK = 0b011

private fun PPoGPacket.typeMask(): Int = when (this) {
  is PPoGPacket.Data -> DATA_MASK
  is PPoGPacket.Ack -> ACK_MASK
  is PPoGPacket.ResetRequest -> RESET_REQUEST_MASK
  is PPoGPacket.ResetComplete -> RESET_COMPLETE_MASK
}

internal fun ByteArray.asPPoGPacket(): PPoGPacket {
  val firstByte = get(0).toInt()
  val sequence = firstByte and SEQUENCE_MASK shr 3
  val packetType = firstByte and PACKET_TYPE_MASK
  return when (packetType) {
    DATA_MASK -> PPoGPacket.Data(sequence, drop(1).toByteArray())
    ACK_MASK -> PPoGPacket.Ack(sequence)
    RESET_REQUEST_MASK -> PPoGPacket.ResetRequest(sequence, get(1).asPPoGVersion())
    RESET_COMPLETE_MASK -> {
      val hasWindows = size >= 3
      if (hasWindows) {
        PPoGPacket.ResetComplete(
          sequence = sequence,
          rxWindow = get(1).toInt(),
          txWindow = get(2).toInt()
        )
      } else {
        PPoGPacket.ResetComplete(sequence, V0_WINDOW_SIZE, V0_WINDOW_SIZE)
      }
    }
    else -> throw IllegalArgumentException("Uknown packet type: $packetType")
  }
}

private fun Byte.asPPoGVersion(): PPoGVersion = PPoGVersion.entries.first { it.version == this.toInt() }
// TODO dedup constants with LeConstants.kt
private const val V0_WINDOW_SIZE = 4
internal const val MAX_SUPPORTED_WINDOW_SIZE: Int = 25

private const val SEQUENCE_MASK = 0b11111000
private const val PACKET_TYPE_MASK = 0b00000111
