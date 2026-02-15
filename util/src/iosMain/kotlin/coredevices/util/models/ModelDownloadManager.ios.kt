package coredevices.util.models

import co.touchlab.kermit.Logger
import com.cactus.CactusModelManager
import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.files.Path as IoPath
import kotlinx.io.files.SystemFileSystem
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import okio.use
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTask
import platform.darwin.NSObject
import platform.posix.int64_t

actual class ModelDownloadManager {

    private val session: NSURLSession by lazy {
        val config =
            NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier("coredevices.util.models.ModelDownloader")
        config.setSessionSendsLaunchEvents(true)
        NSURLSession.sessionWithConfiguration(config, delegate = DownloadDelegate(this), delegateQueue = null)
    }

    private val _downloadStatus = MutableStateFlow<ModelDownloadStatus>(ModelDownloadStatus.Idle)
    actual val downloadStatus: StateFlow<ModelDownloadStatus> = _downloadStatus.asStateFlow()

    init {
        session.getTasksWithCompletionHandler { _, _, downloadTasks ->
            (downloadTasks?.firstOrNull() as? NSURLSessionDownloadTask)?.let { task ->
                val (_, slug) = task.taskDescription?.split(":", limit = 2) ?: return@let
                updateDownloadStatus(ModelDownloadStatus.Downloading(slug))
            }
        }
    }

    internal fun updateDownloadStatus(status: ModelDownloadStatus) {
        MainScope().launch {
            _downloadStatus.value = status
        }
    }

    private fun download(modelInfo: ModelInfo, isStt: Boolean, allowMetered: Boolean): Boolean {
        if (_downloadStatus.value is ModelDownloadStatus.Downloading) {
            return false
        }
        val modelUrl = modelInfo.url

        val url = NSURL(string = modelUrl)
        val request = NSMutableURLRequest.requestWithURL(url).apply {
            setAllowsCellularAccess(allowMetered)
        }

        val task = session.downloadTaskWithRequest(request)
        task.taskDescription = "${if (isStt) "stt" else "lm"}:${modelInfo.slug}"
        task.resume()

        updateDownloadStatus(ModelDownloadStatus.Downloading(modelInfo.slug))
        return true
    }

    actual fun downloadSTTModel(
        modelInfo: ModelInfo,
        allowMetered: Boolean
    ): Boolean {
        return download(modelInfo, isStt = true, allowMetered)
    }

    actual fun downloadLanguageModel(
        modelInfo: ModelInfo,
        allowMetered: Boolean
    ): Boolean {
        return download(modelInfo, isStt = false, allowMetered)
    }

    actual fun cancelDownload() {
        session.getTasksWithCompletionHandler { _, _, downloadTasks ->
            downloadTasks?.forEach { task ->
                (task as? NSURLSessionDownloadTask)?.let {
                    if (
                        it.taskDescription?.startsWith("stt:") == true ||
                        it.taskDescription?.startsWith("lm:") == true
                    ) {
                        it.cancel()
                    }
                }
            }
        }
    }
}

private class DownloadDelegate(private val manager: ModelDownloadManager) : NSObject(),
    NSURLSessionDownloadDelegateProtocol {
    private val logger = Logger.withTag("ModelDownloadDelegate")

    @OptIn(BetaInteropApi::class)
    override fun URLSession(session: NSURLSession, downloadTask: NSURLSessionDownloadTask, didFinishDownloadingToURL: NSURL) {
        val (_, slug) = downloadTask.taskDescription?.split(":", limit = 2) ?: return
        // Return to indeterminate progress while extracting
        manager.updateDownloadStatus(ModelDownloadStatus.Downloading(slug, null))

        val fileManager = NSFileManager.defaultManager
        val modelsPath = CactusModelManager.getModelsDirectory()
        val outputDir = modelsPath.toPath() / slug.toPath()

        fileManager.removeItemAtPath(outputDir.toString(), null)
        fileManager.createDirectoryAtPath(outputDir.toString(), withIntermediateDirectories = true, attributes = null, error = null)

        //Extract the file from the temporary location to the destination
        FileSystem.SYSTEM.openZip(didFinishDownloadingToURL.path!!.toPath()).use { zipFs ->
            val paths = zipFs.listRecursively("/".toPath())
                .filter { zipFs.metadata(it).isRegularFile }
                .toList()

            paths.forEach { zipEntryPath ->
                zipFs.source(zipEntryPath).buffer().use { source ->
                    val fullPath = zipEntryPath.toString().trimStart('/')

                    // Preserve .mlpackage directory structure, otherwise strip first directory level
                    val relativeFilePath = if (fullPath.contains(".mlpackage/") || fullPath.endsWith(".mlpackage")) {
                        fullPath
                    } else if (fullPath.contains('/')) {
                        fullPath.substringAfter('/')
                    } else {
                        fullPath
                    }

                    val fileToWrite = outputDir.resolve(relativeFilePath)
                    fileToWrite.createParentDirectories()
                    FileSystem.SYSTEM.sink(fileToWrite).buffer().use { sink ->
                        val bytes = sink.writeAll(source)
                        logger.d {"Wrote $bytes bytes to $fileToWrite"}
                    }
                }
            }
        }
        logger.i {"Model $slug downloaded and extracted to $outputDir"}
        manager.updateDownloadStatus(ModelDownloadStatus.Idle)
    }

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: int64_t,
        totalBytesWritten: int64_t,
        totalBytesExpectedToWrite: int64_t
    ) {
        val (_, slug) = downloadTask.taskDescription?.split(":", limit = 2) ?: return
        if (totalBytesExpectedToWrite > 0) {
            val progress = totalBytesWritten.toFloat() / totalBytesExpectedToWrite.toFloat()
            manager.updateDownloadStatus(ModelDownloadStatus.Downloading(slug, progress))
        } else {
            manager.updateDownloadStatus(ModelDownloadStatus.Downloading(slug, null))
        }
    }

    private fun Path.createParentDirectories() {
        this.parent?.let { parent ->
            FileSystem.Companion.SYSTEM.createDirectories(parent)
        }
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        if (didCompleteWithError != null) {
            val (_, slug) = task.taskDescription?.split(":", limit = 2) ?: return
            logger.e {"Download failed for model $slug: ${didCompleteWithError.localizedDescription}"}

            // Clean up any partially extracted data
            val modelsPath = CactusModelManager.getModelsDirectory()
            val fileManager = NSFileManager.defaultManager
            val outputDir = modelsPath.toPath() / slug.toPath()
            if (fileManager.fileExistsAtPath(outputDir.toString())) {
                fileManager.removeItemAtPath(outputDir.toString(), null)
            }

            if (didCompleteWithError.domain == "NSURLErrorDomain" && didCompleteWithError.code == -999L) {
                // Download was cancelled. This is not an error.
                manager.updateDownloadStatus(ModelDownloadStatus.Cancelled)
            } else {
                manager.updateDownloadStatus(ModelDownloadStatus.Failed(slug, didCompleteWithError.localizedDescription))
            }
        }
    }

    override fun URLSession(
        session: NSURLSession,
        didReceiveChallenge: NSURLAuthenticationChallenge,
        completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit
    ) {
        completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
    }
}
