package coredevices.pebble.ui

import coredevices.util.Permission
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.posix.AF_INET
import platform.posix.AF_INET6
import platform.posix.IFF_LOOPBACK
import platform.posix.IFF_UP
import platform.posix.NI_MAXHOST
import platform.posix.NI_NUMERICHOST
import platform.posix.getnameinfo

actual fun scanPermission(): Permission? {
    return null
}

@OptIn(ExperimentalForeignApi::class)
actual fun getIPAddress(): Pair<String?, String?> {
    return memScoped {
        val ifap = alloc<CPointerVar<ifaddrs>>()
        if (getifaddrs(ifap.ptr) != 0) {
            return@memScoped Pair(null, null)
        }

        try {
            var tempAddr = ifap.value
            var v4: String? = null
            var v6: String? = null
            var wifiV4: String? = null
            var wifiV6: String? = null

            while (tempAddr != null) {
                val flags = tempAddr.pointed.ifa_flags
                val addr = tempAddr.pointed.ifa_addr
                val ifName = tempAddr.pointed.ifa_name?.toKString() ?: ""

                // Check if the interface is up and not a loopback
                if ((flags and IFF_UP.toUInt()) != 0u && (flags and IFF_LOOPBACK.toUInt()) == 0u && addr != null) {
                    val family = addr.pointed.sa_family.toInt()
                    if (family == AF_INET || family == AF_INET6) {
                        val host = allocArray<ByteVar>(NI_MAXHOST)
                        val saLen = addr.pointed.sa_len
                        val ret = getnameinfo(
                            addr, 
                            saLen.toUInt(), 
                            host, 
                            NI_MAXHOST.toUInt(), 
                            null, 
                            0u, 
                            NI_NUMERICHOST
                        )
                        
                        if (ret == 0) {
                            val ipStr = host.toKString()
                            val isWifi = ifName.startsWith("en")
                            
                            if (family == AF_INET) {
                                if (isWifi) {
                                    wifiV4 = ipStr
                                } else if (v4 == null) {
                                    v4 = ipStr
                                }
                            } else {
                                // For IPv6, prefer non-link-local addresses (not starting with fe80)
                                val isLinkLocal = ipStr.startsWith("fe80:")
                                if (!isLinkLocal) {
                                    if (isWifi) {
                                        wifiV6 = ipStr
                                    } else if (v6 == null) {
                                        v6 = ipStr
                                    }
                                }
                            }
                        }
                    }
                }
                tempAddr = tempAddr.pointed.ifa_next
            }
            
            // Prefer WiFi addresses over cellular
            Pair(wifiV4 ?: v4, wifiV6 ?: v6)
        } finally {
            freeifaddrs(ifap.value)
        }
    }
}