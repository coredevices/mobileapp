package coredevices.util.queue

import kotlin.time.Clock
import kotlin.time.Instant

interface QueueTask {
    val id: Long
    val created: Instant
    val lastAttempt: Instant?
    val attempts: Int
    val status: TaskStatus
}

enum class TaskStatus {
    Pending,
    Success,
    Failed
}