package io.rebble.libpebblecommon.connection.endpointmanager

import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.connection.endpointmanager.FirmwareUpdater.FirmwareUpdateStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Each watch's latest firmware update state, kept across reconnections: one that goes through
 * recovery finishes on a later connection than the one it started on.
 */
class LastFirmwareUpdateStates {
    private val states = MutableStateFlow<Map<PebbleIdentifier, FirmwareUpdateStatus>>(emptyMap())

    fun state(identifier: PebbleIdentifier): Flow<FirmwareUpdateStatus?> =
        states.map { it[identifier] }.distinctUntilChanged()

    fun clear(identifier: PebbleIdentifier) = states.update { it - identifier }

    internal fun update(identifier: PebbleIdentifier, state: FirmwareUpdateStatus) =
        states.update { it + (identifier to state) }
}
