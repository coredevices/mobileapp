package io.rebble.libpebblecommon.exceptions

internal class PacketEncodeException(message: String?) : Exception(message)
internal class PacketDecodeException(message: String?, cause: Exception? = null) : Exception(message, cause)