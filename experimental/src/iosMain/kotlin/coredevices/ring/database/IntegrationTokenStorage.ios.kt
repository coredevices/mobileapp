package coredevices.ring.database

import coredevices.util.integrations.IntegrationTokenStorage
import io.rebble.libpebblecommon.storage.KeychainSecureStorage

actual class IntegrationTokenStorageImpl : IntegrationTokenStorage {

    private val storage = KeychainSecureStorage(SERVICE_NAME)

    actual override suspend fun saveToken(key: String, token: String) = storage.set(key, token)

    actual override suspend fun getToken(key: String): String? = storage.get(key)

    actual override suspend fun deleteToken(key: String) = storage.remove(key)

    companion object {
        private const val SERVICE_NAME = "coredevices.integration_tokens"
    }
}
