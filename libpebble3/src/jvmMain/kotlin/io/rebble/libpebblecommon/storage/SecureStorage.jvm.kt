package io.rebble.libpebblecommon.storage

import io.rebble.libpebblecommon.connection.AppContext
import kotlin.uuid.Uuid

internal actual fun pluginSecureStorage(appContext: AppContext, pluginUuid: Uuid): SecureStorage =
    object : SecureStorage {
        override suspend fun get(key: String): String? = null
        override suspend fun set(key: String, value: String) {}
        override suspend fun remove(key: String) {}
        override suspend fun destroy() {}
    }
