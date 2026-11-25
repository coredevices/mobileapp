package io.rebble.libpebblecommon.io.rebble.libpebblecommon.calls

import io.rebble.libpebblecommon.calls.MissedCall
import io.rebble.libpebblecommon.calls.SystemCallLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Instant

// Stubbed implementation for iOS, as iOS does not provide a public API to access call logs.
internal class IosSystemCallLog: SystemCallLog {
    override suspend fun getMissedCalls(start: Instant): List<MissedCall> = emptyList()

    override fun registerForMissedCallChanges(): Flow<Unit> = emptyFlow()
    override fun hasPermission(): Boolean = true
}