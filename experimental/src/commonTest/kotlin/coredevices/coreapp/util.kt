package coredevices.coreapp

import kotlin.time.Clock
import kotlin.time.Instant

fun fakeClockAt(instant: Instant): Clock = object : Clock {
    override fun now(): Instant = instant
}