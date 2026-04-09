package coredevices.ring

import coredevices.util.Permission

expect class RingDelegate {
    suspend fun init()
    fun requiredRuntimePermissions(): Set<Permission>
}