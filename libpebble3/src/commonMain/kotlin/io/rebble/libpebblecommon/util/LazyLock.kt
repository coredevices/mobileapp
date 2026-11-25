package io.rebble.libpebblecommon.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Lock specific functions until related async function is complete
 * Use sparingly, intended for asynchronously retrying but blocking completely new attempts while we do so
 */
internal class LazyLock {
    var isLocked: Boolean = false

    /**
     * Causes syncLock to block the thread / coroutine it is called on until unlock() used
     * @see syncLock
     * @see unlock
     */
    fun lock() {isLocked = true}

    /**
     * Releases any currently blocking syncLocks of this object
     * @see syncLock
     */
    fun unlock() {isLocked = false}

    /**
     * If locked, blocks the thread / coroutine until unlock is called
     * @see lock
     * @see unlock
     */
    fun syncLock(timeout: Long) {
        var timer: Long = 0
        while (isLocked && timer*10 < timeout)  {timer++; runBlocking { delay(10)}}
    }
}