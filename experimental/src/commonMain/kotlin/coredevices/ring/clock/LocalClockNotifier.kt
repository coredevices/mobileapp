package coredevices.ring.clock

import coredevices.ring.data.entity.room.clock.LocalClockEntry

/** Arms the OS-level notification that makes a [LocalClockEntry] actually go off. */
interface LocalClockNotifier {
    suspend fun schedule(entry: LocalClockEntry)
    suspend fun cancel(id: String)
}
