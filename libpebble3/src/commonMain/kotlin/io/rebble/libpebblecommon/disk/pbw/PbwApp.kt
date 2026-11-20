package io.rebble.libpebblecommon.disk.pbw

import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.database.entity.LockerEntryPlatform
import io.rebble.libpebblecommon.disk.PbwBinHeader
import io.rebble.libpebblecommon.disk.pbw.DiskUtil.getPbwManifest
import io.rebble.libpebblecommon.disk.pbw.DiskUtil.pkjsFileExists
import io.rebble.libpebblecommon.disk.pbw.DiskUtil.requirePbwAppInfo
import io.rebble.libpebblecommon.disk.pbw.DiskUtil.requirePbwBinaryBlob
import io.rebble.libpebblecommon.disk.pbw.DiskUtil.requirePbwPKJSFile
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import io.rebble.libpebblecommon.metadata.pbw.manifest.PbwManifest
import io.rebble.libpebblecommon.plugin.PluginManifest
import kotlinx.serialization.json.Json
import kotlinx.io.RawSource
import kotlinx.io.Source
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlin.time.Instant
import kotlin.uuid.Uuid

public class PbwApp(private val path: Path) {
    public val info: PbwAppInfo by lazy { requirePbwAppInfo(path) }
    internal val hasPKJS: Boolean by lazy { pkjsFileExists(path) }
    internal fun getManifest(watchType: WatchType): PbwManifest? = getPbwManifest(path, watchType)
    internal fun getBinaryFor(watchType: WatchType): Source? {
        val filename = getManifest(watchType)?.application?.name ?: return null
        return requirePbwBinaryBlob(path, watchType, filename)
    }
    internal fun getResourcesFor(watchType: WatchType): Source? {
        val resources = getManifest(watchType)?.resources ?: return null
        return requirePbwBinaryBlob(path, watchType, resources.name)
    }
    internal fun getBinaryHeaderFor(watchType: WatchType): PbwBinHeader? {
        return getBinaryFor(watchType)?.use { source ->
            PbwBinHeader.parseFileHeader(source.readByteArray(PbwBinHeader.SIZE).asUByteArray())
        }
    }
    internal fun getWorkerFor(watchType: WatchType): Source? {
        val filename = getManifest(watchType)?.worker ?: return null
        return requirePbwBinaryBlob(path, watchType, filename.name)
    }
    internal fun getPKJSFile(): Source {
        return requirePbwPKJSFile(path)
    }
    internal fun getTextFile(name: String): String? = DiskUtil.getPbwTextFile(path, name)
    internal fun source(fileSystem: FileSystem = SystemFileSystem): RawSource {
        return fileSystem.source(path)
    }
}

/**
 * Pick best variant, ignoring appinfo targetPlatforms which can be wrong for old pbws
 */
internal fun PbwApp.bestVariantFor(watchType: WatchType): WatchType? =
    watchType.getCompatibleAppVariants().firstOrNull { getManifest(it) != null }

/** False for a plugin-only pbw (a plugin, no watchapp binary for any platform). */
internal fun PbwApp.hasWatchappBuild(): Boolean = WatchType.entries.any { getManifest(it) != null }

internal fun PbwApp.toLockerEntry(now: Instant, orderIndex: Int): LockerEntry {
    val uuid = Uuid.parse(info.uuid)
    // Built from the variants actually in the pbw; appinfo targetPlatforms can be wrong (see [bestVariantFor])
    val platforms = WatchType.entries.mapNotNull { watchType ->
        val header = getBinaryHeaderFor(watchType) ?: return@mapNotNull null
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
        // A pbw with a plugin but no watchapp build is plugin-only: its own type, so it stays out
        // of the app lists and off the watch (see AppType.Plugin).
        type = when {
            info.loadablePlugin != null && !hasWatchappBuild() -> "plugin"
            info.watchapp.watchface -> "watchface"
            else -> "watchapp"
        },
        developerName = info.companyName,
        // A config page makes an app configurable, even without the legacy "configurable" capability.
        configurable = info.configPage != null || info.capabilities.any { it == "configurable" },
        pbwVersionCode = info.versionCode.toString(),
        sideloaded = true,
        sideloadeTimestamp = now.asMillisecond(),
        platforms = platforms,
        appstoreData = null,
        orderIndex = orderIndex,
        capabilities = info.capabilities,
        pluginManifest = info.encodedPluginManifest(),
        configPage = info.configPage,
        manifestVersion = info.manifestVersion,
    )
}

private val pbwPluginJson = Json { encodeDefaults = true }

/** As stored in `LockerEntry.pluginManifest`. */
internal fun PbwAppInfo.encodedPluginManifest(): String? =
    loadablePlugin?.let { pbwPluginJson.encodeToString(PluginManifest.serializer(), it) }