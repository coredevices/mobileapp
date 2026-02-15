package io.rebble.libpebblecommon.disk.pbw

import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import io.rebble.libpebblecommon.metadata.pbw.manifest.PbwManifest
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.okio.asKotlinxIoRawSource
import kotlinx.io.readString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.openZip

object DiskUtil {
    private const val MANIFEST_FILENAME = "manifest.json"
    private const val APPINFO_FILENAME = "appinfo.json"
    private val pbwJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun openZip(path: Path) = FileSystem.SYSTEM.openZip(path.toString().toPath())
    private fun FileSystem.platformSource(watchType: WatchType, fileName: String): RawSource {
        val filePath = fileName.toPath()
        return if (watchType == WatchType.APLITE) {
            val subPath = "aplite".toPath() / filePath
            if (metadataOrNull(subPath) != null) {
                source(subPath).asKotlinxIoRawSource()
            } else {
                source(filePath).asKotlinxIoRawSource()
            }
        } else {
            val subPath = watchType.codename.toPath() / filePath
            source(subPath).asKotlinxIoRawSource()
        }
    }

    fun getPbwManifest(pbwPath: Path, watchType: WatchType): PbwManifest? {
        val source = try {
            openZip(pbwPath).platformSource(watchType, MANIFEST_FILENAME)
        } catch (e: IOException) {
            return null
        }.buffered()
        return source.use { pbwJson.decodeFromString(source.readString()) }
    }

    fun pkjsFileExists(pbwPath: Path): Boolean {
        return openZip(pbwPath).exists("pebble-js-app.js".toPath())
    }

    /**
     * @throws IllegalStateException if pbw does not contain manifest with that watch type
     */
    fun requirePbwAppInfo(pbwPath: Path): PbwAppInfo {
        val source = try {
            openZip(pbwPath).source(APPINFO_FILENAME.toPath()).asKotlinxIoRawSource()
        } catch (e: IOException) {
            throw IllegalStateException("Pbw does not contain manifest")
        }.buffered()
        return pbwJson.decodeFromString(source.use { it.readString() })
    }

    /**
     * @throws IllegalStateException if pbw does not contain binary blob with that name for that watch type
     */
    fun requirePbwBinaryBlob(pbwPath: Path, watchType: WatchType, blobName: String): Source {
        return try {
            openZip(pbwPath).platformSource(watchType, blobName).buffered()
        } catch (e: IOException) {
            throw IllegalStateException("Pbw does not contain binary blob $blobName for watch type $watchType")
        }
    }

    fun requirePbwPKJSFile(pbwPath: Path): Source {
        val source = try {
            openZip(pbwPath).source("pebble-js-app.js".toPath()).asKotlinxIoRawSource()
        } catch (e: IOException) {
            throw IllegalStateException("Pbw does not contain JS file")
        }.buffered()
        return source
    }
}