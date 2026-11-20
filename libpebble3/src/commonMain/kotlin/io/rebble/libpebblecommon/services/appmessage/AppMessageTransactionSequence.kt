package io.rebble.libpebblecommon.services.appmessage

import kotlinx.atomicfu.atomic

internal class AppMessageTransactionSequence: Sequence<UByte> {
    private val sequence = atomic(0)

    override fun iterator(): Iterator<UByte> {
        if (sequence.value != 0) {
            error("Sequence can only be iterated once")
        }
        return object : Iterator<UByte> {
            override fun hasNext(): Boolean = true
            override fun next(): UByte {
                sequence.compareAndSet(0x100, 0)
                return (sequence.getAndIncrement() and 0xff).toUByte()
            }
        }
    }

}