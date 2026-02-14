package coredevices.pebble.actions.watch

import io.rebble.libpebblecommon.js.RemoteTimelineEmulator
import kotlinx.coroutines.runBlocking
import kotlin.uuid.Uuid

/** Inserts a timeline pin for the given app. */
fun insertTimelinePin(
    remoteTimelineEmulator: RemoteTimelineEmulator,
    pinJson: String,
    appUuid: Uuid,
) {
    runBlocking { remoteTimelineEmulator.insertPin(pinJson = pinJson, appUuid = appUuid) }
}

/** Deletes a timeline pin by id for the given app. */
fun deleteTimelinePin(
    remoteTimelineEmulator: RemoteTimelineEmulator,
    appUuid: Uuid,
    pinId: String,
) {
    runBlocking { remoteTimelineEmulator.deletePin(appUuid = appUuid, pinIdentifier = pinId) }
}
