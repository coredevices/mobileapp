package coredevices.ring.database

import coredevices.util.integrations.IntegrationTokenStorage

expect class IntegrationTokenStorageImpl: IntegrationTokenStorage {
    override suspend fun saveToken(key: String, token: String)
    override suspend fun getToken(key: String): String?
    override suspend fun deleteToken(key: String)
}