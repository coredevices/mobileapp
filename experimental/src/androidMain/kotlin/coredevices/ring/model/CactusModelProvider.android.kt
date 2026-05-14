package coredevices.ring.model

import android.content.Context
import co.touchlab.kermit.Logger
import com.cactus.Cactus
import coredevices.util.CommonBuildKonfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.mp.KoinPlatform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

actual class CactusModelProvider actual constructor() : coredevices.util.transcription.CactusModelPathProvider {
    companion object {
        private val logger = Logger.withTag("CactusModelProvider")
        private const val HF_BASE = "https://huggingface.co/Cactus-Compute"
        private const val QUANTIZATION = "int8"
        private const val DOWNLOAD_BUFFER_SIZE = 256 * 1024
        private val downloadMutex = Mutex()
    }

    private val context: Context get() = KoinPlatform.getKoin().get()
    private val modelsDir: File get() = context.filesDir.resolve("models").also { it.mkdirs() }

    actual override suspend fun getSTTModelPath(): String {
        val modelName = CommonBuildKonfig.CACTUS_STT_MODEL
        return resolveModelPath(modelName, CommonBuildKonfig.CACTUS_STT_WEIGHTS_VERSION)
    }

    actual override suspend fun getLMModelPath(): String {
        val modelName = CommonBuildKonfig.CACTUS_LM_MODEL_NAME
        return resolveModelPath(modelName, CommonBuildKonfig.CACTUS_LM_WEIGHTS_VERSION)
    }

    actual override fun isModelDownloaded(modelName: String): Boolean {
        val modelDir = modelsDir.resolve(modelName)
        return modelDir.exists() && modelDir.resolve("config.txt").exists()
    }

    actual override fun getDownloadedModels(): List<String> {
        return modelsDir.listFiles()
            ?.filter { it.isDirectory && it.resolve("config.txt").exists() }
            ?.map { it.name }
            ?: emptyList()
    }

    actual override fun getIncompatibleModels(): List<String> {
        val compatible = setOf(CommonBuildKonfig.CACTUS_STT_MODEL, CommonBuildKonfig.CACTUS_LM_MODEL_NAME)
        return getDownloadedModels().filter { it !in compatible }
    }

    actual override fun deleteModel(modelName: String) {
        modelsDir.resolve(modelName).deleteRecursively()
    }

    actual override fun getModelSizeBytes(modelName: String): Long {
        val dir = modelsDir.resolve(modelName)
        return if (dir.exists()) dir.walkTopDown().sumOf { it.length() } else 0L
    }

    private suspend fun resolveModelPath(modelName: String, version: String): String = downloadMutex.withLock {
        val modelDir = modelsDir.resolve(modelName)
        val versionFile = modelDir.resolve(".cactus_version")

        val currentVersion = version
        val needsDownload = !modelDir.exists()
            || !modelDir.resolve("config.txt").exists()
            || (versionFile.exists() && versionFile.readText().trim() != currentVersion)

        if (needsDownload) {
            downloadAndExtract(modelName, modelDir, version)
            versionFile.writeText(currentVersion)
        }

        logger.d { "Model '$modelName' at: ${modelDir.absolutePath}" }
        return modelDir.absolutePath
    }

    private fun downloadAndExtract(modelName: String, targetDir: File, version: String) {
        val zipName = "${modelName.lowercase()}-$QUANTIZATION.zip"
        val url = "$HF_BASE/$modelName/resolve/$version/weights/$zipName"
        logger.i { "Downloading model: $url" }

        val tempZip = File(context.cacheDir, "cactus_download_$modelName.zip")
        try {
            // Download using OkHttp which handles redirects and HTTP/2 properly
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.MINUTES)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val request = Request.Builder()
                .url(url)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()?.take(500) ?: "no body"
                    throw Exception("Download failed: HTTP ${response.code} for $url — $errorBody")
                }

                val body = response.body
                    ?: throw Exception("Download failed: empty response body for $url")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                var lastLoggedPct = -1

                body.byteStream().use { input ->
                    FileOutputStream(tempZip).use { output ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val pct = (downloadedBytes * 100 / totalBytes).toInt()
                                if (pct / 10 > lastLoggedPct / 10) {
                                    lastLoggedPct = pct
                                    logger.d { "Download progress: $pct% ($downloadedBytes / $totalBytes)" }
                                }
                            }
                        }
                    }
                }
            }
            logger.i { "Download complete: ${tempZip.length()} bytes" }

            // Clear old model if present
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            targetDir.mkdirs()

            // Extract
            ZipInputStream(tempZip.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outputFile = File(targetDir, entry.name)
                    // ZIP Slip protection
                    if (!outputFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                        throw SecurityException("ZIP entry outside target dir: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        FileOutputStream(outputFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            logger.i { "Extraction complete to ${targetDir.absolutePath}" }
        } catch (e: Exception) {
            logger.e(e) { "Model download/extract failed for $modelName" }
            targetDir.deleteRecursively()
            throw e
        } finally {
            tempZip.delete()
        }
    }

    actual fun setCloudApiKey(key: String) {
        val cacheDir = getCactusCacheDir()
        val keyFile = File(cacheDir, "cloud_api_key")
        keyFile.writeText(key)
        logger.d { "Cloud API key written to ${keyFile.absolutePath}" }
    }

    actual override fun initTelemetry() {
        val cacheDir = getCactusCacheDir()
        try {
            Cactus.setTelemetryEnvironment(cacheDir.absolutePath)
            logger.d { "Telemetry environment set to ${cacheDir.absolutePath}" }
        } catch (e: Throwable) {
            logger.e(e) { "Failed to initialize telemetry environment" }
        }
    }

    private fun getCactusCacheDir(): File {
        val dir = context.cacheDir.resolve("cactus")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
