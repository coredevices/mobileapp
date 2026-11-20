package io.rebble.libpebblecommon.calls

import kotlinx.coroutines.flow.MutableStateFlow

internal interface LegacyPhoneReceiver {
    fun init(currentCall: MutableStateFlow<Call?>)
}