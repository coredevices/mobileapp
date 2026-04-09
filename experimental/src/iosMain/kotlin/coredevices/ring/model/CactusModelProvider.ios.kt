package coredevices.ring.model

import co.touchlab.kermit.Logger
import com.cactus.Cactus
import coredevices.util.CommonBuildKonfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.openZip
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile

actual class CactusModelProvider actual constructor() : coredevices.util.transcription.CactusModelPathProvider {
    companion object {
        private val logger = Logger.withTag("CactusModelProvider")
        private const val HF_BASE = "https://huggingface.co/Cactus-Compute"
        private const val QUANTIZATION = "int8"
        private val downloadMutex = Mutex()
    }

    private val cachesDir: String
        get() = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory, NSUserDomainMask, true
        ).first() as String

    private val modelsDir: String get() = "$cachesDir/models"

    actual override suspend fun getSTTModelPath(): String {
        val modelName = CommonBuildKonfig.CACTUS_STT_MODEL
        return resolveModelPath(modelName, CommonBuildKonfig.CACTUS_STT_WEIGHTS_VERSION)
    }

    actual override suspend fun getLMModelPath(): String {
        val modelName = CommonBuildKonfig.CACTUS_LM_MODEL_NAME
        return resolveModelPath(modelName, CommonBuildKonfig.CACTUS_LM_WEIGHTS_VERSION)
    }

    actual override fun isModelDownloaded(modelName: String): Boolean {
        val configPath = Path("$modelsDir/$modelName", "config.txt")
        return SystemFileSystem.exists(configPath)
    }

    actual override fun getDownloadedModels(): List<String> {
        val dir = Path(modelsDir)
        if (!SystemFileSystem.exists(dir)) return emptyList()
        return SystemFileSystem.list(dir)
            .filter { SystemFileSystem.exists(Path(it.toString(), "config.txt")) }
            .map { it.name }
    }

    actual override fun getIncompatibleModels(): List<String> {
        val compatible = setOf(CommonBuildKonfig.CACTUS_STT_MODEL, CommonBuildKonfig.CACTUS_LM_MODEL_NAME)
        return getDownloadedModels().filter { it !in compatible }
    }

    actual override fun deleteModel(modelName: String) {
        val fileManager = NSFileManager.defaultManager
        val path = "$modelsDir/$modelName"
        if (fileManager.fileExistsAtPath(path)) {
            fileManager.removeItemAtPath(path, null)
        }
    }

    actual override fun getModelSizeBytes(modelName: String): Long {
        val fileManager = NSFileManager.defaultManager
        val path = "$modelsDir/$modelName"
        if (!fileManager.fileExistsAtPath(path)) return 0L
        val attributes = fileManager.attributesOfItemAtPath(path, null)
        // For directories, walk files
        var totalSize = 0L
        val enumerator = fileManager.enumeratorAtPath(path) ?: return 0L
        while (true) {
            val file = enumerator.nextObject() as? String ?: break
            val filePath = "$path/$file"
            val fileAttrs = fileManager.attributesOfItemAtPath(filePath, null)
            totalSize += (fileAttrs?.get("NSFileSize") as? Long) ?: 0L
        }
        return totalSize
    }

    private suspend fun resolveModelPath(modelName: String, version: String): String = downloadMutex.withLock {
        val modelPath = "$modelsDir/$modelName"
        val configPath = Path(modelPath, "config.txt")
        val versionFilePath = "$modelPath/.cactus_version"
        val versionPath = Path(versionFilePath)

        val currentVersion = if (SystemFileSystem.exists(versionPath)) {
            SystemFileSystem.source(versionPath).buffered().use { it.readString() }.trim()
        } else null

        val needsDownload = !SystemFileSystem.exists(configPath)
                || (currentVersion != null && currentVersion != version)

        if (needsDownload) {
            downloadAndExtract(modelName, modelPath, version)
            SystemFileSystem.sink(versionPath).buffered().use { sink ->
                sink.write(version.encodeToByteArray())
            }
        }

        logger.d { "Model '$modelName' at: $modelPath" }
        return modelPath
    }

    private suspend fun downloadAndExtract(modelName: String, targetDir: String, version: String) {
        val zipName = "${modelName.lowercase()}-$QUANTIZATION.zip"
        val url = "$HF_BASE/$modelName/resolve/$version/weights/$zipName"
        logger.i { "Downloading model: $url" }

        val tempZipPath = "${NSTemporaryDirectory()}cactus_download_$modelName.zip"
        val fileManager = NSFileManager.defaultManager

        try {
            downloadToFile(url, tempZipPath)
            logger.i { "Download complete: $tempZipPath" }

            // Clear old model if present
            if (fileManager.fileExistsAtPath(targetDir)) {
                fileManager.removeItemAtPath(targetDir, null)
            }
            fileManager.createDirectoryAtPath(
                targetDir, withIntermediateDirectories = true,
                attributes = null, error = null
            )

            extractZip(tempZipPath, targetDir)
            logger.i { "Extraction complete to $targetDir" }
        } catch (e: Exception) {
            logger.e(e) { "Model download/extract failed for $modelName" }
            if (fileManager.fileExistsAtPath(targetDir)) {
                fileManager.removeItemAtPath(targetDir, null)
            }
            throw e
        } finally {
            if (fileManager.fileExistsAtPath(tempZipPath)) {
                fileManager.removeItemAtPath(tempZipPath, null)
            }
        }
    }

    private fun downloadToFile(url: String, destPath: String) {
        val nsUrl = NSURL.URLWithString(url)
            ?: throw Exception("Invalid URL: $url")
        val data = NSData.dataWithContentsOfURL(nsUrl)
            ?: throw Exception("Download failed for $url")
        if (!data.writeToFile(destPath, atomically = true)) {
            throw Exception("Failed to write download to $destPath")
        }
    }

    private fun extractZip(zipPath: String, targetDir: String) {
        val zipFs = FileSystem.SYSTEM.openZip(zipPath.toPath())
        val targetOkioPath = targetDir.toPath()

        val entries = mutableListOf<okio.Path>()
        zipFs.listRecursively("/".toPath()).forEach { entries.add(it) }

        for (entry in entries) {
            val entryStr = entry.toString().removePrefix("/")
            if (entryStr.isEmpty()) continue

            // ZIP Slip protection
            if (".." in entryStr) {
                throw IllegalArgumentException("ZIP entry contains ..: $entryStr")
            }

            val outputPath = targetOkioPath / entryStr
            val metadata = zipFs.metadata(entry)

            if (metadata.isDirectory) {
                FileSystem.SYSTEM.createDirectories(outputPath)
            } else {
                outputPath.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
                val source = zipFs.source(entry)
                try {
                    val bufferedSource = source.buffer()
                    val sink = FileSystem.SYSTEM.sink(outputPath)
                    try {
                        val bufferedSink = sink.buffer()
                        try {
                            bufferedSink.writeAll(bufferedSource)
                        } finally {
                            bufferedSink.close()
                        }
                    } finally {
                        sink.close()
                    }
                } finally {
                    source.close()
                }
            }
        }
    }

    actual fun setCloudApiKey(key: String) {
        val cacheDir = getCactusCacheDir()
        val keyFile = "$cacheDir/cloud_api_key"
        SystemFileSystem.sink(Path(keyFile)).buffered().use { sink ->
            sink.write(key.encodeToByteArray())
        }
        logger.d { "Cloud API key written to $keyFile" }
    }

    actual override fun initTelemetry() {
        val cacheDir = getCactusCacheDir()
        Cactus.setTelemetryEnvironment(cacheDir)
        logger.d { "Telemetry environment set to $cacheDir" }
    }

    private fun getCactusCacheDir(): String {
        val dir = "$cachesDir/cactus"
        val path = Path(dir)
        if (!SystemFileSystem.exists(path)) {
            SystemFileSystem.createDirectories(path)
        }
        return dir
    }
}
