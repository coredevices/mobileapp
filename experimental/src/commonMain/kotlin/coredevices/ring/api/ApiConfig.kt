package coredevices.ring.api

import CommonApiConfig
import kotlinx.serialization.Serializable

@Serializable
data class ApiConfig(
    val nenyaUrl: String,
    val notionOAuthBackendUrl: String,
    val notionApiUrl: String,
    override val bugUrl: String?,
    override val version: String,
    override val tokenUrl: String?,
) : CommonApiConfig
