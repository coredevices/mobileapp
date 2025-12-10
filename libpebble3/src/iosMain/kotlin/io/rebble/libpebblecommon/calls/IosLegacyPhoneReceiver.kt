package io.rebble.libpebblecommon.io.rebble.libpebblecommon.calls

import io.rebble.libpebblecommon.calls.Call
import io.rebble.libpebblecommon.calls.LegacyPhoneReceiver
import kotlinx.coroutines.flow.MutableStateFlow

class IosLegacyPhoneReceiver : LegacyPhoneReceiver {
    override fun init(currentCall: MutableStateFlow<Call?>) {
    }
}