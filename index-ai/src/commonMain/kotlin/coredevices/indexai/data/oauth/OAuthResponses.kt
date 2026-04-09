package coredevices.indexai.data.oauth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthURLResponse(val url: String)

@Serializable
data class OAuthTokenResponse(
    @SerialName("access_token")
    val accessToken: String? = null,
    val error: String? = null
)