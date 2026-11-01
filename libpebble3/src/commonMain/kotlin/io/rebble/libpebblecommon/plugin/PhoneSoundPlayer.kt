package io.rebble.libpebblecommon.plugin

import kotlinx.coroutines.flow.Flow

expect class PhoneSoundPlayer {
    val isRinging: Flow<Boolean>
    fun startRinging()
    fun stopRinging()
}
