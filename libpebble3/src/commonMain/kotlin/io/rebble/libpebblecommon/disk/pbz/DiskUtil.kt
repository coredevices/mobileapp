package io.rebble.libpebblecommon.disk.pbz

import io.rebble.libpebblecommon.metadata.pbz.manifest.PbzManifest
import io.rebble.libpebblecommon.metadata.pbz.manifest.PbzManifestWrapper
import kotlinx.io.IOException
import kotlinx.io.RawSource
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
    private val pbzJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun openZip(path: Path) = FileSystem.SYSTEM.openZip(path.toString().toPath())

    private fun FileSystem.loadManifestFrom(dir: String): PbzManifest? {
        val manifestPath = dir.toPath().resolve(MANIFEST_FILENAME)
        if (!exists(manifestPath)) {
            return null
        }
        val source = try {
            source(manifestPath).asKotlinxIoRawSource()
        } catch (e: IOException) {
            return null
        }.buffered()
        return source.use {pbzJson.decodeFromString(it.readString())}
    }

    fun requirePbzManifests(pbzPath: Path): List<PbzManifestWrapper> {
        val zip = openZip(pbzPath)
        val rootManifest = zip.loadManifestFrom("")
        if (rootManifest != null) {
            return listOf(PbzManifestWrapper(
                manifest = rootManifest,
                zipPath = pbzPath,
                manifestPath = Path(""),
            ))
        } else {
            val slot0Manifest = zip.loadManifestFrom("slot0")
            val slot1Manifest = zip.loadManifestFrom("slot1")
            if (slot0Manifest == null || slot1Manifest == null) {
                throw IllegalStateException("Pbz does not contain manifest")
            }
            if (slot0Manifest.firmware.slot != 0) {
                throw IllegalStateException("Slot for slot0 manifest was ${slot0Manifest.firmware.slot}")
            }
            if (slot1Manifest.firmware.slot != 1) {
                throw IllegalStateException("Slot for slot0 manifest was ${slot0Manifest.firmware.slot}")
            }
            return listOf(
                PbzManifestWrapper(
                    manifest = slot0Manifest,
                    zipPath = pbzPath,
                    manifestPath = Path("slot0"),
                ),
                PbzManifestWrapper(
                    manifest = slot1Manifest,
                    zipPath = pbzPath,
                    manifestPath = Path("slot1"),
                ),
            )
        }
    }

    fun getFile(pbzPath: Path, fileName: String): RawSource = openZip(pbzPath).source(fileName.toPath()).asKotlinxIoRawSource()
}
