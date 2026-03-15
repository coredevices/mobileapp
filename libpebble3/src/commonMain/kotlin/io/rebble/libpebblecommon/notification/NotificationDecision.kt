package io.rebble.libpebblecommon.notification

import kotlinx.serialization.Serializable

/**
 * Never remove/rename existing entries (they are serialized).
 */
@Serializable
enum class NotificationDecision {
    SendToWatch,
    NotSentLocalOnly,
    NotSentGroupSummary,
    NotSentAppMuted,
    NotSendChannelMuted,
    NotSendContactMuted,
    NotSentDuplicate,
    NotSentScreenOn,
    NotSentRegexFiltered,
}