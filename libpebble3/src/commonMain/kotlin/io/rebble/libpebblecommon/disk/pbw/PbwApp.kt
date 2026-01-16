package io.rebble.libpebblecommon.disk.pbw

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.database.entity.LockerEntryPlatform
import io.rebble.libpebblecommon.disk.PbwBinHeader
import io.rebble.libpebblecommon.disk.pbw.DiskUtil.pkjsFileExists
import io.rebble.libpebblecommon.disk.pbw.DiskUtil.requirePbwAppInfo
import io.rebble.libpebblecommon.disk.pbw.DiskUtil.requirePbwBinaryBlob
import io.rebble.libpebblecommon.disk.pbw.DiskUtil.requirePbwManifest
import io.rebble.libpebblecommon.disk.pbw.DiskUtil.requirePbwPKJSFile
import io.rebble.libpebblecommon.metadata.WatchType
import kotlin.time.Instant
import kotlinx.io.RawSource
import kotlinx.io.Source
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlin.uuid.Uuid

class PbwApp(private val path: Path) {
    val info by lazy { requirePbwAppInfo(path) }
    val hasPKJS by lazy { pkjsFileExists(path) }
    fun getManifest(watchType: WatchType) = requirePbwManifest(path, watchType)
    fun getBinaryFor(watchType: WatchType): Source {
        val filename = getManifest(watchType).application.name
        return requirePbwBinaryBlob(path, watchType, filename)
    }
    fun getResourcesFor(watchType: WatchType): Source? {
        val resources = getManifest(watchType).resources ?: return null
        return requirePbwBinaryBlob(path, watchType, resources.name)
    }
    fun getBinaryHeaderFor(watchType: WatchType): PbwBinHeader {
        return getBinaryFor(watchType).use { source ->
            PbwBinHeader.parseFileHeader(source.readByteArray(PbwBinHeader.SIZE).asUByteArray())
        }
    }
    fun getWorkerFor(watchType: WatchType): Source? {
        val filename = getManifest(watchType).worker ?: return null
        return requirePbwBinaryBlob(path, watchType, filename.name)
    }
    fun getPKJSFile(): Source {
        return requirePbwPKJSFile(path)
    }
    fun source(fileSystem: FileSystem = SystemFileSystem): RawSource {
        return fileSystem.source(path)
    }
}

fun PbwApp.toLockerEntry(now: Instant, orderIndex: Int): LockerEntry {
    val uuid = Uuid.parse(info.uuid)
    val platforms = info.targetPlatforms.mapNotNull {
        val watchType = WatchType.fromCodename(it) ?: run {
            Logger.w { "Unknown watch type in pbw while processing sideload request: $it" }
            return@mapNotNull null
        }
        val header = getBinaryHeaderFor(watchType)
        LockerEntryPlatform(
            lockerEntryId = uuid,
            sdkVersion = "${header.sdkVersionMajor.get()}.${header.sdkVersionMinor.get()}",
            processInfoFlags = header.flags.get().toInt(),
            name = watchType.codename,
            pbwIconResourceId = header.icon.get().toInt(),
        )
    }
    return LockerEntry(
        id = uuid,
        version = info.versionLabel,
        title = info.longName.ifBlank { info.shortName },
        type = if (info.watchapp.watchface) "watchface" else "watchapp",
        developerName = info.companyName,
        configurable = info.capabilities.any { it == "configurable" },
        pbwVersionCode = info.versionCode.toString(),
        sideloaded = true,
        sideloadeTimestamp = now.asMillisecond(),
        platforms = platforms,
        appstoreData = null,
        orderIndex = orderIndex,
    )
}