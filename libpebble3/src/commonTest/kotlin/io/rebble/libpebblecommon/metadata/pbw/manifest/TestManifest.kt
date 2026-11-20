package io.rebble.libpebblecommon.metadata.pbw.manifest

import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import io.rebble.libpebblecommon.metadata.pbw.appinfo.TestAppInfo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

internal class TestManifest {
    companion object {
        const val MANIFEST_JSON_SIMPLICITY_V1 = "{\"manifestVersion\": 1, \"generatedBy\": \"46e8a544-50dc-4610-aa27-d86115f76f11\", \"generatedAt\": 1449884654, \"application\": {\"timestamp\": 1449884653, \"sdk_version\": {\"major\": 5, \"minor\": 19}, \"crc\": 893549736, \"name\": \"pebble-app.bin\", \"size\": 1339}, \"debug\": {}, \"type\": \"application\", \"resources\": {\"timestamp\": 1449884653, \"crc\": 3436670150, \"name\": \"app_resources.pbpack\", \"size\": 4232}}"
        const val MANIFEST_JSON_SIMPLICITY = "{\"manifestVersion\": 2, \"generatedBy\": \"46e8a544-50dc-4610-aa27-d86115f76f11\", \"generatedAt\": 1449884654, \"application\": {\"timestamp\": 1449884653, \"sdk_version\": {\"major\": 5, \"minor\": 72}, \"crc\": 266802728, \"name\": \"pebble-app.bin\", \"size\": 1363}, \"debug\": {}, \"app_layouts\": \"layouts.json\", \"type\": \"application\", \"resources\": {\"timestamp\": 1449884653, \"crc\": 3168848230, \"name\": \"app_resources.pbpack\", \"size\": 4218}}"

        val MANIFEST_OBJ_SIMPLICITY_V1 = PbwManifest(
            application = PbwBlob(
                crc = 893549736,
                name = "pebble-app.bin",
                sdkVersion = SdkVersion(
                    major = 5,
                    minor = 19
                ),
                size = 1339,
                timestamp = 1449884653
            ),
            resources = PbwBlob(
                crc = 3436670150,
                name = "app_resources.pbpack",
                size = 4232,
                timestamp = 1449884653
            ),
            debug = Debug(),
            generatedAt = 1449884654,
            generatedBy = "46e8a544-50dc-4610-aa27-d86115f76f11",
            manifestVersion = 1,
            type = "application"
        )
    }

    @Test
    fun deserialization() {
        val json = Json{ ignoreUnknownKeys = true }
        val simplicityv1: PbwManifest = json.decodeFromString(MANIFEST_JSON_SIMPLICITY_V1)
        assertEquals(MANIFEST_OBJ_SIMPLICITY_V1, simplicityv1)

        //TODO: v2 manifest
    }
}