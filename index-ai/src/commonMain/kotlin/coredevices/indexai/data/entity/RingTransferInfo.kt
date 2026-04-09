package coredevices.indexai.data.entity

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class RingTransferInfo(
    val collectionStartIndex: Int,
    val collectionEndIndex: Int?,
    val buttonPressed: Long? = null,
    val buttonReleased: Long? = null,
    val advertisementReceived: Long,
    val transferCompleted: Long? = null,
    val buttonReleaseAdvertisementLatencyMs: Long? = null,
)

fun RingTransferInfo.Companion.createFromTimestamps(
    collectionStartIndex: Int,
    collectionEndIndex: Int,
    buttonPressed: Instant?,
    buttonReleased: Instant?,
    advertisementReceived: Instant,
    transferCompleted: Instant,
): RingTransferInfo =
    RingTransferInfo(
        collectionStartIndex = collectionStartIndex,
        collectionEndIndex = collectionEndIndex,
        buttonPressed = buttonPressed?.toEpochMilliseconds(),
        buttonReleased = buttonReleased?.toEpochMilliseconds(),
        advertisementReceived = advertisementReceived.toEpochMilliseconds(),
        transferCompleted = transferCompleted.toEpochMilliseconds(),
        buttonReleaseAdvertisementLatencyMs = buttonReleased?.let { advertisementReceived.toEpochMilliseconds() - it.toEpochMilliseconds() },
    )