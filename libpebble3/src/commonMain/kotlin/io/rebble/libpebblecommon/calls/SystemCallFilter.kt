package io.rebble.libpebblecommon.calls

internal interface SystemCallFilter {
    /** Whether the phone's Do Not Disturb settings allow a call from [number] to interrupt the user. */
    suspend fun allowsCall(number: String): Boolean
}
