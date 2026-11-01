package coredevices.ring.database

import android.content.Context
import coredevices.util.integrations.IntegrationTokenStorage
import io.rebble.libpebblecommon.storage.KeystoreSecureStorage

actual class IntegrationTokenStorageImpl(
    context: Context
) : IntegrationTokenStorage {

    private val storage = KeystoreSecureStorage(context, KEY_ALIAS, PREFS_NAME)

    suspend fun clear() = storage.destroy()

    actual override suspend fun saveToken(key: String, token: String) = storage.set(key, token)

    actual override suspend fun getToken(key: String): String? = storage.get(key)

    actual override suspend fun deleteToken(key: String) = storage.remove(key)

    companion object {
        private const val KEY_ALIAS = "integration_token_key"
        private const val PREFS_NAME = "integration_tokens"
    }
}
