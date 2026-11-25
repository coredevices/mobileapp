package io.rebble.libpebblecommon.connection

internal data class ConnectionException(
    val reason: ConnectionFailureReason,
) : Exception()
