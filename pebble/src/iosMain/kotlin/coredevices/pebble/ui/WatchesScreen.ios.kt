package coredevices.pebble.ui

import coredevices.util.Permission
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.posix.AF_INET
import platform.posix.AF_INET6
import platform.posix.IFF_LOOPBACK
import platform.posix.IFF_UP

actual fun scanPermission(): Permission? {
    return null
}

@OptIn(ExperimentalForeignApi::class)
actual fun getIPAddress(): Pair<String?, String?> {
    return memScoped {
        val addrs = alloc<ifaddrs>().ptr
        var tempAddr: CPointer<ifaddrs>? = null
        var v4: String? = null
        var v6: String? = null
        if (getifaddrs(cValuesOf(addrs)) == 0) {
            tempAddr = addrs
            while (tempAddr != null) {
                val flags = tempAddr.pointed.ifa_flags
                //Logger.i { "Interface: ${tempAddr.pointed.ifa_addr?.pointed?.sa_data?.toKString()}, Flags: $flags" }
                // Check if the interface is up and not a loopback
                if ((flags and IFF_UP.toUInt()) != 0u && (flags and IFF_LOOPBACK.toUInt()) == 0u) {
                    tempAddr.pointed.ifa_addr?.let {
                        when (it.pointed.sa_family.toInt()) {
                            AF_INET -> {
                                v4 = it.pointed.sa_data.toKString()
                            }
                            AF_INET6 -> {
                                v6 = it.pointed.sa_data.toKString()
                            }
                        }
                        if (v4 != null && v6 != null) {
                            break
                        }
                    }
                }
                tempAddr = tempAddr.pointed.ifa_next
            }
        }
        return@memScoped Pair(v4, v6)
    }
}