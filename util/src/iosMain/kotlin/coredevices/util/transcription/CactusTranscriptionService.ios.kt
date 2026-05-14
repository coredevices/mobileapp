package coredevices.util.transcription

import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.darwin.HOST_VM_INFO64
import platform.darwin.HOST_VM_INFO64_COUNT
import platform.darwin.KERN_SUCCESS
import platform.darwin.host_page_size
import platform.darwin.host_statistics64
import platform.darwin.mach_host_self
import platform.darwin.vm_size_tVar
import platform.darwin.vm_statistics64_data_t

actual suspend fun withHighPriorityThread(block: suspend () -> Unit) {
    block()
}

actual suspend fun getFreeMemoryMB(): Long = memScoped {
    val hostPort = mach_host_self()
    val hostSize = alloc<UIntVar> {
        value = HOST_VM_INFO64_COUNT
    }
    val pageSize = alloc<vm_size_tVar>()
    host_page_size(hostPort, pageSize.ptr)
    val vmStat = alloc<vm_statistics64_data_t>()
    val res = host_statistics64(hostPort, HOST_VM_INFO64, vmStat.ptr.reinterpret(), hostSize.ptr)
    if (res != KERN_SUCCESS) {
        throw Exception("Failed to fetch vm statistics: $res")
    }
    val memUsed = (vmStat.active_count + vmStat.inactive_count + vmStat.wire_count) * pageSize.value
    val memFree = vmStat.free_count * pageSize.value
    val memTotal = memUsed + memFree
    ((memTotal - memUsed) / (1024u * 1024u)).toLong()
}

// iOS is good at the model memory pressure / uses GPU so we can have minimum low
actual val PLATFORM_MIN_TRANSCRIPTION_MEMORY_MB: Long = 1