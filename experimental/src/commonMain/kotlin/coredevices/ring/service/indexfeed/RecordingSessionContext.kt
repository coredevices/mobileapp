package coredevices.ring.service.indexfeed

import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

data class RecordingSessionContext(
    val sourceRecordingId: String,
    val createdAt: Instant,
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<RecordingSessionContext>
    override val key = Key
}
