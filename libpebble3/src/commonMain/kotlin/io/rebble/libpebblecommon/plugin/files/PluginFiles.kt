package io.rebble.libpebblecommon.plugin.files

import com.russhwolf.settings.Settings
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.plugin.PluginNativeException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FileAccessMode {
    @SerialName("read") Read,
    @SerialName("readwrite") ReadWrite,
}

@Serializable
enum class FileEntryKind {
    @SerialName("file") File,
    @SerialName("directory") Directory,
}

/**
 * An entry the user handed a plugin, as the plugin and its settings page see it. [id] is the
 * permission scope — the entry's path (iOS) or uri (Android) — which is what `ReadFiles/<scope>`
 * and `WriteFiles/<scope>` are granted on.
 */
@Serializable
data class FileHandleInfo(
    val id: String,
    val kind: FileEntryKind,
    val name: String,
    val mode: FileAccessMode,
)

@Serializable
data class FileEntryInfo(
    val name: String,
    val kind: FileEntryKind,
    val size: Long = 0,
    /** Epoch millis. */
    val lastModified: Long = 0,
    /** MIME type, when the platform knows one. */
    val type: String? = null,
)

/** Error codes raised by file operations; the JS side maps them onto DOMException names. */
object PluginFileErrors {
    const val NOT_FOUND = "NOT_FOUND"
    const val TYPE_MISMATCH = "TYPE_MISMATCH"
    const val INVALID_MODIFICATION = "INVALID_MODIFICATION"
}

/**
 * Platform access to the entries under a scope. [path] is relative to the scoped entry, already
 * validated by [PluginFilePath], and empty for the entry itself. Failures are
 * [PluginNativeException]s carrying a [PluginFileErrors] or `PluginErrors` code.
 */
interface PluginFileSystem {
    /** Keep whatever the platform needs to reach [scope] later: on iOS, the picker's bookmark. */
    suspend fun retain(scope: String, handle: String)

    /** No app holds a permission on [scope] any more. */
    suspend fun release(scope: String)

    /** Whether the platform still honours [scope]; a user can revoke access outside the app. */
    suspend fun hasAccess(scope: String): Boolean

    /** Null when nothing exists at [path]. */
    suspend fun stat(scope: String, path: List<String>): FileEntryInfo?

    suspend fun list(scope: String, path: List<String>): List<FileEntryInfo>

    suspend fun read(scope: String, path: List<String>): ByteArray

    /** Replaces the file's contents. With [create], a missing file is made first. */
    suspend fun write(scope: String, path: List<String>, data: ByteArray, create: Boolean)

    /** A directory that already exists is left alone. */
    suspend fun createDirectory(scope: String, path: List<String>)

    suspend fun remove(scope: String, path: List<String>, recursive: Boolean)
}

internal expect fun platformPluginFileSystem(appContext: AppContext, settings: Settings): PluginFileSystem

object PluginFilePath {
    private val illegal = charArrayOf('/', '\\', '\u0000')

    fun isValidName(name: String) =
        name.isNotEmpty() && name != "." && name != ".." && name.none { it in illegal }

    /** Checks every segment, so a plugin cannot climb out of the entry it was handed. */
    fun validate(path: List<String>): List<String> {
        require(path.all(::isValidName)) { "invalid path ${path.joinToString("/")}" }
        return path
    }
}

internal fun notFound(path: List<String>) =
    PluginNativeException(PluginFileErrors.NOT_FOUND, "no entry at '${path.joinToString("/")}'")

internal fun typeMismatch(message: String) =
    PluginNativeException(PluginFileErrors.TYPE_MISMATCH, message)
