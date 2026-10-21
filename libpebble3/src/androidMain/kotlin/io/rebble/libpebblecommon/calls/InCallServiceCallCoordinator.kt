package io.rebble.libpebblecommon.calls

import android.telecom.Call

class InCallServiceCallCoordinator {
    private val activeCalls = mutableSetOf<Int>()

    @Synchronized
    fun markHandling(call: Call) {
        activeCalls += System.identityHashCode(call)
    }

    @Synchronized
    fun clear(call: Call) {
        activeCalls -= System.identityHashCode(call)
    }

    @Synchronized
    fun clearAll() {
        activeCalls.clear()
    }

    @Synchronized
    fun isHandlingCall(): Boolean = activeCalls.isNotEmpty()
}
