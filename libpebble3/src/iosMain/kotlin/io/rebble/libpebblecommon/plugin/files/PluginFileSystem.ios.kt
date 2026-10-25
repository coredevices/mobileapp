package io.rebble.libpebblecommon.plugin.files

import com.russhwolf.settings.Settings
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.plugin.PluginErrors
import io.rebble.libpebblecommon.plugin.PluginNativeException
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLBookmarkResolutionWithoutUI
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToURL
import platform.UniformTypeIdentifiers.UTType
import platform.posix.memcpy

internal actual fun platformPluginFileSystem(appContext: AppContext, settings: Settings): PluginFileSystem =
    IosPluginFileSystem(settings)

/**
 * A scope is the picked entry's path. Reaching it again needs the picker's security-scoped
 * bookmark, kept here against the path and resolved per operation.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosPluginFileSystem(private val settings: Settings) : PluginFileSystem {
    private val fileManager get() = NSFileManager.defaultManager

    override suspend fun retain(scope: String, handle: String) {
        settings.putString(bookmarkKey(scope), handle)
    }

    override suspend fun release(scope: String) {
        settings.remove(bookmarkKey(scope))
    }

    override suspend fun hasAccess(scope: String): Boolean = withContext(Dispatchers.Default) {
        val url = resolve(scope) ?: return@withContext false
        val started = url.startAccessingSecurityScopedResource()
        try {
            url.checkResourceIsReachableAndReturnError(null)
        } finally {
            if (started) url.stopAccessingSecurityScopedResource()
        }
    }

    override suspend fun stat(scope: String, path: List<String>): FileEntryInfo? = withRoot(scope) { root ->
        root.child(path).info()
    }

    override suspend fun list(scope: String, path: List<String>): List<FileEntryInfo> = withRoot(scope) { root ->
        val dir = root.child(path)
        val info = dir.info() ?: throw notFound(path)
        if (info.kind != FileEntryKind.Directory) throw typeMismatch("'${info.name}' is not a directory")
        val contents = fileManager.contentsOfDirectoryAtURL(dir, includingPropertiesForKeys = null, options = 0u, error = null)
            ?: throw io("couldn't list '${info.name}'")
        contents.filterIsInstance<NSURL>().mapNotNull { it.info() }
    }

    override suspend fun read(scope: String, path: List<String>): ByteArray = withRoot(scope) { root ->
        val file = root.child(path)
        val info = file.info() ?: throw notFound(path)
        if (info.kind == FileEntryKind.Directory) throw typeMismatch("'${info.name}' is a directory")
        NSData.dataWithContentsOfURL(file)?.toByteArray() ?: throw io("couldn't read '${info.name}'")
    }

    override suspend fun write(scope: String, path: List<String>, data: ByteArray, create: Boolean) =
        withRoot(scope) { root ->
            val file = root.child(path)
            val existing = file.info()
            when {
                existing == null && (!create || path.isEmpty()) -> throw notFound(path)
                existing?.kind == FileEntryKind.Directory -> throw typeMismatch("'${existing.name}' is a directory")
            }
            if (!data.toNSData().writeToURL(file, atomically = true)) throw io("couldn't write '${path.lastOrNull() ?: scope}'")
        }

    override suspend fun createDirectory(scope: String, path: List<String>) = withRoot(scope) { root ->
        val dir = root.child(path)
        when (dir.info()?.kind) {
            FileEntryKind.Directory -> return@withRoot
            FileEntryKind.File -> throw typeMismatch("'${path.last()}' is a file")
            null -> if (path.isEmpty()) throw notFound(path)
        }
        val created = fileManager.createDirectoryAtURL(dir, withIntermediateDirectories = false, attributes = null, error = null)
        if (!created) throw io("couldn't create '${path.last()}'")
    }

    override suspend fun remove(scope: String, path: List<String>, recursive: Boolean) = withRoot(scope) { root ->
        val entry = root.child(path)
        val info = entry.info() ?: throw notFound(path)
        if (info.kind == FileEntryKind.Directory && !recursive) {
            val contents = fileManager.contentsOfDirectoryAtURL(entry, includingPropertiesForKeys = null, options = 0u, error = null)
            if (!contents.isNullOrEmpty()) {
                throw PluginNativeException(PluginFileErrors.INVALID_MODIFICATION, "'${info.name}' is not empty")
            }
        }
        if (!fileManager.removeItemAtURL(entry, error = null)) throw io("couldn't delete '${info.name}'")
    }

    private suspend fun <T> withRoot(scope: String, block: (NSURL) -> T): T = withContext(Dispatchers.Default) {
        val url = resolve(scope)
            ?: throw PluginNativeException(PluginErrors.PERMISSION_DENIED, "access to '$scope' is no longer available")
        val started = url.startAccessingSecurityScopedResource()
        try {
            block(url)
        } finally {
            if (started) url.stopAccessingSecurityScopedResource()
        }
    }

    private fun resolve(scope: String): NSURL? {
        val bookmark = settings.getStringOrNull(bookmarkKey(scope)) ?: return null
        val data = NSData.create(base64EncodedString = bookmark, options = 0u) ?: return null
        return NSURL.URLByResolvingBookmarkData(
            data,
            options = NSURLBookmarkResolutionWithoutUI,
            relativeToURL = null,
            bookmarkDataIsStale = null,
            error = null,
        )
    }

    private fun bookmarkKey(scope: String) = "$BOOKMARK_PREFIX$scope"

    private fun NSURL.child(path: List<String>): NSURL =
        path.fold(this) { url, segment -> url.URLByAppendingPathComponent(segment) ?: throw notFound(path) }

    /** Null when nothing is there. */
    private fun NSURL.info(): FileEntryInfo? {
        val filePath = path ?: return null
        val isDirectory = memScoped {
            val flag = alloc<BooleanVar>()
            if (!fileManager.fileExistsAtPath(filePath, isDirectory = flag.ptr)) return null
            flag.value
        }
        val attributes = fileManager.attributesOfItemAtPath(filePath, error = null)
        val modified = (attributes?.get(NSFileModificationDate) as? NSDate)?.timeIntervalSince1970 ?: 0.0
        return FileEntryInfo(
            name = lastPathComponent.orEmpty(),
            kind = if (isDirectory) FileEntryKind.Directory else FileEntryKind.File,
            size = if (isDirectory) 0 else attributes?.get(NSFileSize).asLong(),
            lastModified = (modified * 1000).toLong(),
            type = if (isDirectory) null else pathExtension?.let { UTType.typeWithFilenameExtension(it)?.preferredMIMEType },
        )
    }

    // NSNumber crosses into Kotlin as a boxed number or as NSNumber, depending on the bridge.
    private fun Any?.asLong(): Long = when (this) {
        is Number -> toLong()
        is NSNumber -> longLongValue
        else -> 0
    }

    private fun io(message: String) = PluginNativeException(PluginErrors.UNKNOWN, message)

    private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).apply {
        if (length > 0u) {
            usePinned { memcpy(it.addressOf(0), bytes, length) }
        }
    }

    private fun ByteArray.toNSData(): NSData = memScoped {
        NSData.create(bytes = allocArrayOf(this@toNSData), length = size.convert())
    }

    private companion object {
        const val BOOKMARK_PREFIX = "plugin_file_bookmark_"
    }
}
