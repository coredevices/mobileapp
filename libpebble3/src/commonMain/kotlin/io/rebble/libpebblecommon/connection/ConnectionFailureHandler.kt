package io.rebble.libpebblecommon.connection

import io.rebble.libpebblecommon.LibPebbleAnalytics
import io.rebble.libpebblecommon.metadata.WatchColor

internal interface ConnectionFailureHandler {
    suspend fun handleConnectionFailure(identifier: PebbleIdentifier, color: WatchColor, failure: ConnectionFailureInfo)
}

internal class RealConnectionFailureHandler(
    private val appContext: AppContext,
    private val analytics: LibPebbleAnalytics,
) : ConnectionFailureHandler {
    override suspend fun handleConnectionFailure(identifier: PebbleIdentifier, color: WatchColor, failure: ConnectionFailureInfo) {
        if (failure.times >= 5) {
            when (failure.reason) {
                ConnectionFailureReason.MtuGattError -> appContext.handleMtuGattError(
                    identifier,
                    color,
                    analytics
                )

                ConnectionFailureReason.GattInsufficientAuth -> appContext.handleGattInsufficientAuth(
                    identifier,
                    color,
                    analytics
                )

                ConnectionFailureReason.CreateBondFailed -> appContext.handleCreateBondFailed(
                    identifier,
                    color,
                    analytics
                )

                else -> Unit
            }
        }
    }
}

internal expect fun AppContext.handleMtuGattError(identifier: PebbleIdentifier, color: WatchColor, analytics: LibPebbleAnalytics)
internal expect fun AppContext.handleGattInsufficientAuth(identifier: PebbleIdentifier, color: WatchColor, analytics: LibPebbleAnalytics)
internal expect fun AppContext.handleCreateBondFailed(identifier: PebbleIdentifier, color: WatchColor, analytics: LibPebbleAnalytics)