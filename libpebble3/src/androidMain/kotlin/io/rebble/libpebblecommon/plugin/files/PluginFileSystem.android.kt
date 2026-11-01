package io.rebble.libpebblecommon.plugin.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.russhwolf.settings.Settings
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.plugin.PluginErrors
import io.rebble.libpebblecommon.plugin.PluginNativeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual fun platformPluginFileSystem(appContext: AppContext, settings: Settings): PluginFileSystem =
    AndroidPluginFileSystem(appContext.context)

/** Storage Access Framework: a scope is a tree uri (directory) or a document uri (file). */
internal class AndroidPluginFileSystem(context: Context) : PluginFileSystem {
    private val context = context.applicationContext
    private val resolver get() = context.contentResolver

    /** The persistable permission was taken when the entry was picked; the uri is the scope. */
    override suspend fun retain(scope: String, handle: String) = Unit

    override suspend fun release(scope: String) = withContext(Dispatchers.IO) {
        runCatching {
            resolver.releasePersistableUriPermission(
                Uri.parse(scope),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        Unit
    }

    override suspend fun hasAccess(scope: String): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.parse(scope)
        resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission } &&
            runCatching { root(scope)?.exists() == true }.getOrDefault(false)
    }

    override suspend fun stat(scope: String, path: List<String>): FileEntryInfo? = withContext(Dispatchers.IO) {
        resolve(scope, path)?.takeIf { it.exists() }?.info()
    }

    override suspend fun list(scope: String, path: List<String>): List<FileEntryInfo> = withContext(Dispatchers.IO) {
        val dir = require(scope, path)
        if (!dir.isDirectory) throw typeMismatch("'${dir.name}' is not a directory")
        dir.listFiles().map { it.info() }
    }

    override suspend fun read(scope: String, path: List<String>): ByteArray = withContext(Dispatchers.IO) {
        val file = require(scope, path)
        if (file.isDirectory) throw typeMismatch("'${file.name}' is a directory")
        resolver.openInputStream(file.uri)?.use { it.readBytes() } ?: throw io("couldn't open '${file.name}'")
    }

    override suspend fun write(scope: String, path: List<String>, data: ByteArray, create: Boolean) =
        withContext(Dispatchers.IO) {
            val existing = resolve(scope, path)?.takeIf { it.exists() }
            val file = when {
                existing != null && existing.isDirectory -> throw typeMismatch("'${existing.name}' is a directory")
                existing != null -> existing
                !create || path.isEmpty() -> throw notFound(path)
                else -> {
                    val name = path.last()
                    require(scope, path.dropLast(1)).createFile(mimeTypeFor(name), name) ?: throw io("couldn't create '$name'")
                }
            }
            // "wt" truncates, so a shorter write doesn't leave the old tail behind.
            resolver.openOutputStream(file.uri, "wt")?.use { it.write(data) } ?: throw io("couldn't open '${file.name}'")
        }

    override suspend fun createDirectory(scope: String, path: List<String>) = withContext(Dispatchers.IO) {
        val existing = resolve(scope, path)?.takeIf { it.exists() }
        when {
            existing == null && path.isEmpty() -> throw notFound(path)
            existing == null -> {
                val name = path.last()
                require(scope, path.dropLast(1)).createDirectory(name) ?: throw io("couldn't create '$name'")
            }
            !existing.isDirectory -> throw typeMismatch("'${existing.name}' is a file")
        }
        Unit
    }

    override suspend fun remove(scope: String, path: List<String>, recursive: Boolean) = withContext(Dispatchers.IO) {
        val entry = require(scope, path)
        if (entry.isDirectory && !recursive && entry.listFiles().isNotEmpty()) {
            throw PluginNativeException(PluginFileErrors.INVALID_MODIFICATION, "'${entry.name}' is not empty")
        }
        if (!entry.delete()) throw io("couldn't delete '${entry.name}'")
    }

    private fun root(scope: String): DocumentFile? {
        val uri = Uri.parse(scope)
        return if (DocumentsContract.isTreeUri(uri)) {
            DocumentFile.fromTreeUri(context, uri)
        } else {
            DocumentFile.fromSingleUri(context, uri)
        }
    }

    private fun resolve(scope: String, path: List<String>): DocumentFile? {
        val root = root(scope) ?: return null
        if (path.isEmpty()) return root
        if (!root.isDirectory) throw typeMismatch("'${root.name}' is a file, not a directory")
        return path.fold<String, DocumentFile?>(root) { dir, segment -> dir?.findFile(segment) }
    }

    private fun require(scope: String, path: List<String>): DocumentFile =
        resolve(scope, path)?.takeIf { it.exists() } ?: throw notFound(path)

    private fun DocumentFile.info() = FileEntryInfo(
        name = name.orEmpty(),
        kind = if (isDirectory) FileEntryKind.Directory else FileEntryKind.File,
        size = length(),
        lastModified = lastModified(),
        type = if (isDirectory) null else type,
    )

    // SAF appends the extension it derives from the MIME type, so derive the type from the name.
    private fun mimeTypeFor(name: String): String =
        name.substringAfterLast('.', "").takeIf { it.isNotEmpty() }
            ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase()) }
            ?: "application/octet-stream"

    private fun io(message: String) = PluginNativeException(PluginErrors.UNKNOWN, message)
}
