package coredevices.util

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

sealed class BondingEvent(val deviceId: String?) {
    class Bonded(deviceId: String) : BondingEvent(deviceId)
    class Unbonded(deviceId: String) : BondingEvent(deviceId)
    class Error(deviceId: String?) : BondingEvent(deviceId)
}

@Composable
expect fun rememberPlatformBondingListener(): Flow<BondingEvent>