package coredevices.coreapp.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Individual update item in the sync response
 */
@Serializable
data class RegisterToken(
    @SerialName("device_id") val deviceId: String,
    val platform: String,
)