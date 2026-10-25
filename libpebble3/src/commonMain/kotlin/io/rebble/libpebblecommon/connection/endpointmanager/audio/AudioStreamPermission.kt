package io.rebble.libpebblecommon.connection.endpointmanager.audio

import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest

internal suspend fun streamAudioWhileGranted(
    grantedKeys: Flow<Set<String>>,
    stream: suspend () -> Boolean,
): Boolean = grantedKeys.transformLatest { granted ->
    emit(PluginPermissions.WATCH_MICROPHONE in granted && stream())
}.first()
