package coredevices.indexai.data.entity

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * `collectionStartIndex` and `advertisementReceived` were originally
 * non-null. We made them nullable because legacy Firestore docs
 * (written before those fields existed under those names — older
 * schemas had `collectionIndex`) come back as null and the strict
 * decoder would reject the whole `RecordingEntry`. New writes always
 * provide both via [createFromTimestamps].
 */
@Serializable
data class RingTransferInfo(
    val collectionStartIndex: Int? = null,
    val collectionEndIndex: Int? = null,
    val buttonPressed: Long? = null,
    val buttonReleased: Long? = null,
    val advertisementReceived: Long? = null,
    val transferCompleted: Long? = null,
    val buttonReleaseAdvertisementLatencyMs: Long? = null,
) {
    companion object
}

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