package coredevices.database

import kotlinx.serialization.Serializable

@Serializable
data class UserConfig(
    val experimentalDevices: Boolean = false,
)