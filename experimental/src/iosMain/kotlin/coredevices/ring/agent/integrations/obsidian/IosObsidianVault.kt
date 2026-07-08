package coredevices.ring.agent.integrations.obsidian

import PlatformUiContext
import co.touchlab.kermit.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDirectoryEnumerationSkipsHiddenFiles
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLBookmarkCreationMinimalBookmark
import platform.Foundation.NSURLBookmarkResolutionWithoutUI
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSString
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.darwin.NSObject
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosObsidianVault : ObsidianVault {

    private val logger = Logger.withTag("IosObsidianVault")

    // Strong reference so the picker delegate outlives the suspension (delegate is a weak ref).
    private var pickerDelegate: UIDocumentPickerDelegateProtocol? = null

    override suspend fun pickFolder(uiContext: PlatformUiContext): VaultRef? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder))
            val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                    pickerDelegate = null
                    val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                    if (url == null) {
                        cont.resume(null)
                        return
                    }
                    val handle = bookmarkFor(url)
                    if (handle == null) {
                        cont.resume(null)
                        return
                    }
                    cont.resume(VaultRef(handle = handle, displayName = url.lastPathComponent ?: "Obsidian vault"))
                }

                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                    pickerDelegate = null
                    cont.resume(null)
                }
            }
            pickerDelegate = delegate
            picker.delegate = delegate
            cont.invokeOnCancellation { pickerDelegate = null }
            // Present from the top-most window's view controller. Compose renders dialogs in a
            // separate, higher-level UIWindow; presenting from the Compose root VC would put the
            // picker *behind* that dialog window where it can't be reached.
            topmostViewController(uiContext.viewController)
                .presentViewController(picker, animated = true, completion = null)
        }
    }

    private fun topmostViewController(fallback: UIViewController): UIViewController {
        var best: UIWindow? = null
        for (w in UIApplication.sharedApplication.windows) {
            val win = w as? UIWindow ?: continue
            if (win.hidden) continue
            if (best == null || win.windowLevel > best!!.windowLevel) best = win
        }
        var vc = best?.rootViewController ?: fallback
        while (true) {
            val presented = vc.presentedViewController ?: break
            vc = presented
        }
        return vc
    }

    override suspend fun hasAccess(handle: String): Boolean = withContext(Dispatchers.Default) {
        resolve(handle) != null
    }

    override suspend fun listMarkdownFiles(handle: String, subfolder: String): List<String> = withContext(Dispatchers.Default) {
        withVault(handle) { vaultDir ->
            val dir = if (subfolder.isBlank()) vaultDir else vaultDir.URLByAppendingPathComponent(subfolder)
            if (dir == null) return@withVault emptyList<String>()
            val fm = NSFileManager.defaultManager
            val contents = fm.contentsOfDirectoryAtURL(
                dir,
                includingPropertiesForKeys = null,
                options = NSDirectoryEnumerationSkipsHiddenFiles,
                error = null,
            ) ?: return@withVault emptyList<String>()
            contents.filterIsInstance<NSURL>()
                .mapNotNull { it.lastPathComponent }
                .filter { it.endsWith(".md", ignoreCase = true) }
                .sorted()
        } ?: emptyList()
    }

    override suspend fun releaseAccess(handle: String) {
        // Security-scoped bookmarks need no explicit release.
    }

    override suspend fun readFile(handle: String, name: String): String? = withContext(Dispatchers.Default) {
        withVault(handle) { dir ->
            val fileUrl = dir.URLByAppendingPathComponent(name) ?: return@withVault null
            val data = NSData.dataWithContentsOfURL(fileUrl)
            if (data == null) {
                logger.d { "readFile: file not found: $name" }
                return@withVault null
            }
            val content = NSString.create(data, NSUTF8StringEncoding) as String?
            logger.d { "readFile: $name -> ${content?.length ?: 0} chars" }
            content
        }
    }

    override suspend fun writeFile(handle: String, name: String, content: String): Boolean = withContext(Dispatchers.Default) {
        withVault(handle) { dir ->
            val fileUrl = dir.URLByAppendingPathComponent(name) ?: return@withVault false
            fileUrl.URLByDeletingLastPathComponent?.let { parent ->
                NSFileManager.defaultManager.createDirectoryAtURL(
                    parent,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null,
                )
            }
            val nsContent = NSString.create(string = content)
            val data = nsContent.dataUsingEncoding(NSUTF8StringEncoding) ?: return@withVault false
            val ok = data.writeToURL(fileUrl, atomically = true)
            if (ok) {
                logger.d { "writeFile: $name -> ${content.length} chars written" }
            } else {
                logger.w { "writeFile: failed to write $name (${content.length} chars)" }
            }
            ok
        } ?: false
    }

    private fun <T> withVault(handle: String, block: (NSURL) -> T): T? {
        val url = resolve(handle) ?: return null
        val started = url.startAccessingSecurityScopedResource()
        if (!started) {
            logger.w { "startAccessingSecurityScopedResource returned false for vault; file operations may silently fail" }
        }
        try {
            return block(url)
        } finally {
            if (started) url.stopAccessingSecurityScopedResource()
        }
    }

    private fun bookmarkFor(url: NSURL): String? {
        val started = url.startAccessingSecurityScopedResource()
        try {
            val data = url.bookmarkDataWithOptions(
                NSURLBookmarkCreationMinimalBookmark,
                includingResourceValuesForKeys = null,
                relativeToURL = null,
                error = null,
            ) ?: return null
            return data.base64EncodedStringWithOptions(0u)
        } catch (e: Exception) {
            logger.e(e) { "Failed to create bookmark" }
            return null
        } finally {
            if (started) url.stopAccessingSecurityScopedResource()
        }
    }

    private fun resolve(handle: String): NSURL? {
        val data = NSData.create(base64EncodedString = handle, options = 0u) ?: return null
        return NSURL.URLByResolvingBookmarkData(
            data,
            options = NSURLBookmarkResolutionWithoutUI,
            relativeToURL = null,
            bookmarkDataIsStale = null,
            error = null,
        )
    }
}
