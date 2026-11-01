package io.rebble.libpebblecommon.storage

import io.rebble.libpebblecommon.connection.AppContext
import kotlin.uuid.Uuid

/** Strings encrypted at rest with a key held by the platform (Android Keystore, iOS Keychain). */
interface SecureStorage {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
    suspend fun remove(key: String)

    /** Removes every value, and the key protecting them. */
    suspend fun destroy()
}

internal expect fun pluginSecureStorage(appContext: AppContext, pluginUuid: Uuid): SecureStorage
