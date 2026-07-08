package coredevices.ring.agent.integrations.obsidian

import PlatformUiContext
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AndroidObsidianVault(private val context: Context) : ObsidianVault {

    private val logger = Logger.withTag("AndroidObsidianVault")

    override suspend fun pickFolder(uiContext: PlatformUiContext): VaultRef? {
        val activity = uiContext.activity
        val registry = activity as? ActivityResultRegistryOwner
            ?: error("Activity is not an ActivityResultRegistryOwner")
        return suspendCancellableCoroutine { cont ->
            val launcher = registry.activityResultRegistry.register(
                "pickObsidianVault",
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
                if (uri == null) {
                    cont.resume(null)
                    return@register
                }
                // Persist access so later (background) writes keep working across reboots. Require
                // that the picker actually granted write, and never let a failed grant throw out of
                // this result callback.
                val grantedWrite = (result.data?.flags ?: 0) and
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0
                val persisted = grantedWrite && runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }.onFailure { logger.e(it) { "Failed to persist Obsidian vault access" } }.isSuccess
                if (!persisted) {
                    logger.w { "Obsidian vault folder did not grant persistable write access" }
                    cont.resume(null)
                    return@register
                }
                val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "Obsidian vault"
                cont.resume(VaultRef(handle = uri.toString(), displayName = name))
            }
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                )
            }
            launcher.launch(intent)
        }
    }

    override suspend fun hasAccess(handle: String): Boolean = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(handle) }.getOrNull() ?: return@withContext false
        val held = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
        if (!held) return@withContext false
        DocumentFile.fromTreeUri(context, uri)?.canWrite() == true
    }

    override suspend fun listMarkdownFiles(handle: String, subfolder: String): List<String> = withContext(Dispatchers.IO) {
        val dir = resolveDir(handle, subfolder, create = false) ?: return@withContext emptyList()
        dir.listFiles()
            .filter { it.isFile && it.name?.endsWith(".md", ignoreCase = true) == true }
            .mapNotNull { it.name }
            .sorted()
    }

    override suspend fun releaseAccess(handle: String) = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(handle),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        Unit
    }

    override suspend fun readFile(handle: String, name: String): String? = withContext(Dispatchers.IO) {
        val file = resolveFile(handle, name, create = false) ?: return@withContext null
        runCatching {
            context.contentResolver.openInputStream(file.uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull()
    }

    override suspend fun writeFile(handle: String, name: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val file = resolveFile(handle, name, create = true) ?: return@withContext false
        runCatching {
            // mode "wt" truncates so overwrite replaces prior content rather than appending bytes.
            context.contentResolver.openOutputStream(file.uri, "wt")?.use {
                it.write(content.encodeToByteArray())
            } ?: return@withContext false
            true
        }.getOrElse {
            logger.e(it) { "Failed to write $name" }
            false
        }
    }

    private fun treeRoot(handle: String): DocumentFile? {
        val uri = runCatching { Uri.parse(handle) }.getOrNull() ?: return null
        return DocumentFile.fromTreeUri(context, uri)
    }

    /** Walks [subfolder] (slash-separated; blank = root) under the tree, optionally creating dirs. */
    private fun resolveDir(handle: String, subfolder: String, create: Boolean): DocumentFile? {
        var dir = treeRoot(handle) ?: return null
        for (segment in subfolder.split('/').filter { it.isNotBlank() }) {
            val existing = dir.findFile(segment)
            dir = when {
                existing != null && existing.isDirectory -> existing
                create -> dir.createDirectory(segment) ?: return null
                else -> return null
            }
        }
        return dir
    }

    /** Resolves "Sub/Name.md" under the tree, optionally creating the subfolder + file. */
    private fun resolveFile(handle: String, name: String, create: Boolean): DocumentFile? {
        val parts = name.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        val dir = resolveDir(handle, parts.dropLast(1).joinToString("/"), create) ?: return null
        val fileName = parts.last()
        val existing = dir.findFile(fileName)
        return when {
            existing != null -> existing
            create -> dir.createFile("text/markdown", fileName)
            else -> null
        }
    }
}
