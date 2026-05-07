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
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.openZip

object DiskUtil {
    private const val MANIFEST_FILENAME = "manifest.json"
    private const val APPINFO_FILENAME = "appinfo.json"
    private const val RESOURCE_PACK_FILENAME = "app_resources.pbpack"
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

    /**
     * Read an arbitrary file from the .pbw zip at the given root-relative
     * path. Returns null if the file isn't present in the zip — common for
     * resources that are compiled into `app_resources.pbpack` rather than
     * included as raw source files.
     *
     * Used by share-target icon extraction: some toolchains include the
     * original menu-icon PNG at the zip root in addition to the compiled
     * resource pack; if so, we can use it directly without having to parse
     * the resource pack.
     */
    fun readPbwResourceFileOrNull(pbwPath: Path, resourceFilePath: String): ByteArray? {
        return try {
            val zip = openZip(pbwPath)
            val path = resourceFilePath.toPath()
            if (!zip.exists(path)) return null
            zip.source(path).asKotlinxIoRawSource().buffered().use { src ->
                src.readByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read the entire `app_resources.pbpack` for a given watch type, or
     * null if absent. The bytes returned are the raw `.pbpack` binary,
     * suitable for parsing with [PbwResourcePack.extractResource].
     *
     * Used by share-target icon extraction when the source PNG isn't
     * present at its declared path inside the .pbw (the typical case for
     * PBWs built by the standard SDK / CloudPebble — those compile all
     * media into the resource pack and don't ship raw sources).
     */
    fun readPbwResourcePackBytesOrNull(pbwPath: Path, watchType: WatchType): ByteArray? {
        return try {
            requirePbwBinaryBlob(pbwPath, watchType, RESOURCE_PACK_FILENAME).use { src ->
                src.readByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }
}
