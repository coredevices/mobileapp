package io.rebble.libpebblecommon.util

import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import io.rebble.libpebblecommon.metadata.pbw.manifest.PbwManifest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object SerializationUtil {
    private val json = Json { ignoreUnknownKeys = true }
    @Throws(SerializationException::class)
    fun serializeAppInfo(appInfo: PbwAppInfo): String = json.encodeToString(appInfo)
    @Throws(SerializationException::class)
    fun deserializeAppInfo(jsonString: String): PbwAppInfo = json.decodeFromString(jsonString)

    @Throws(SerializationException::class)
    fun serializeManifest(manifest: PbwManifest): String = json.encodeToString(manifest)
    @Throws(SerializationException::class)
    fun deserializeManifest(jsonString: String): PbwManifest = json.decodeFromString(jsonString)
}