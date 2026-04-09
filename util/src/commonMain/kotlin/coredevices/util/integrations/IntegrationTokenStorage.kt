package coredevices.util.integrations

interface IntegrationTokenStorage {
    suspend fun saveToken(key: String, token: String)
    suspend fun getToken(key: String): String?
    suspend fun deleteToken(key: String)
}