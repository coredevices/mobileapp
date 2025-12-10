package io.rebble.libpebblecommon.calls

import kotlinx.coroutines.flow.MutableStateFlow

interface LegacyPhoneReceiver {
    fun init(currentCall: MutableStateFlow<Call?>)
}