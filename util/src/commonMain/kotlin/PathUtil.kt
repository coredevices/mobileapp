import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

fun Path.size(): Long = SystemFileSystem.metadataOrNull(this)?.size ?: -1L