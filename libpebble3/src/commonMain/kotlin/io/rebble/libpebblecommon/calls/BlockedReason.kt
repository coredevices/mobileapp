package io.rebble.libpebblecommon.calls

internal enum class BlockedReason {
    NotBlocked,
    BlockedNumber,
    CallScreening,
    DirectToVoicemail,
    Other
}