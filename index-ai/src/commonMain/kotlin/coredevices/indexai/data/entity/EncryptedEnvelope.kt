package coredevices.indexai.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EncryptedEnvelope(
    val version: Int = 1,
    val iv: String,
    val ciphertext: String,
    @SerialName("key_fingerprint")
    val keyFingerprint: String,
)
