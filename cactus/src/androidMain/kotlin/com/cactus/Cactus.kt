@file:JvmName("Cactus")
package com.cactus

private object CactusCpuJNI {
    init { System.loadLibrary("cactus_cpu") }
    @JvmStatic external fun nativeIsCactusSupported(): Boolean
}

private val cactusSupported: Boolean by lazy {
    try {
        CactusCpuJNI.nativeIsCactusSupported()
    } catch (_: Throwable) {
        false
    }
}

actual fun isCactusSupported(): Boolean = cactusSupported

private object CactusJNI {
    init {
        System.loadLibrary("cactus_engine")
    }

    @JvmStatic external fun nativeInit(modelPath: String, corpusDir: String?, cacheIndex: Boolean): Long
    @JvmStatic external fun nativeDestroy(handle: Long)
    @JvmStatic external fun nativeReset(handle: Long)
    @JvmStatic external fun nativeStop(handle: Long)
    @JvmStatic external fun nativeComplete(handle: Long, messagesJson: String, responseBuffer: ByteArray, optionsJson: String?, toolsJson: String?, callback: CactusTokenCallback?, pcmData: ByteArray?): Int
    @JvmStatic external fun nativePrefill(handle: Long, messagesJson: String, responseBuffer: ByteArray, optionsJson: String?, toolsJson: String?, pcmData: ByteArray?): Int
    @JvmStatic external fun nativeTokenize(handle: Long, text: String, tokenBuffer: IntArray, outTokenLen: LongArray): Int
    @JvmStatic external fun nativeScoreWindow(handle: Long, tokens: IntArray, start: Long, end: Long, context: Long, responseBuffer: ByteArray): Int
    @JvmStatic external fun nativeTranscribe(handle: Long, audioPath: String?, prompt: String, responseBuffer: ByteArray, optionsJson: String?, callback: CactusTokenCallback?, pcmData: ByteArray?): Int
    @JvmStatic external fun nativeStreamTranscribeStart(handle: Long, optionsJson: String?): Long
    @JvmStatic external fun nativeStreamTranscribeProcess(stream: Long, pcmData: ByteArray?, responseBuffer: ByteArray): Int
    @JvmStatic external fun nativeStreamTranscribeStop(stream: Long, responseBuffer: ByteArray): Int
    @JvmStatic external fun nativeEmbed(handle: Long, text: String, embeddingsBuffer: FloatArray, outEmbeddingDim: LongArray, normalize: Boolean): Int
    @JvmStatic external fun nativeImageEmbed(handle: Long, imagePath: String, embeddingsBuffer: FloatArray, outEmbeddingDim: LongArray): Int
    @JvmStatic external fun nativeAudioEmbed(handle: Long, audioPath: String, embeddingsBuffer: FloatArray, outEmbeddingDim: LongArray): Int
    @JvmStatic external fun nativeRagQuery(handle: Long, query: String, responseBuffer: ByteArray, topK: Long): Int
    @JvmStatic external fun nativeIndexInit(indexDir: String, embeddingDim: Long): Long
    @JvmStatic external fun nativeIndexAdd(handle: Long, ids: IntArray, documents: Array<String>, metadatas: Array<String>?, embeddings: Array<FloatArray>, embeddingDim: Long): Int
    @JvmStatic external fun nativeIndexDelete(handle: Long, ids: IntArray): Int
    @JvmStatic external fun nativeIndexGet(handle: Long, ids: IntArray, documentBuffers: Array<ByteArray>, documentBufferSizes: LongArray, metadataBuffers: Array<ByteArray>, metadataBufferSizes: LongArray, embeddingBuffers: Array<FloatArray>, embeddingBufferSizes: LongArray): Int
    @JvmStatic external fun nativeIndexQuery(handle: Long, embeddings: Array<FloatArray>, embeddingDim: Long, optionsJson: String?, idBuffers: Array<IntArray>, idBufferSizes: LongArray, scoreBuffers: Array<FloatArray>, scoreBufferSizes: LongArray): Int
    @JvmStatic external fun nativeIndexCompact(handle: Long): Int
    @JvmStatic external fun nativeIndexDestroy(handle: Long)
    @JvmStatic external fun nativeGetLastError(): String
    @JvmStatic external fun nativeLogSetLevel(level: Int)
    @JvmStatic external fun nativeLogSetCallback(callback: CactusLogCallback?)
    @JvmStatic external fun nativeSetTelemetryEnvironment(framework: String?, cacheLocation: String?, version: String?)
    @JvmStatic external fun nativeSetAppId(appId: String)
    @JvmStatic external fun nativeTelemetryFlush()
    @JvmStatic external fun nativeTelemetryShutdown()
}

private fun check(rc: Int) {
    if (rc < 0) throw RuntimeException(CactusJNI.nativeGetLastError().ifEmpty { "Unknown error" })
}

private fun ByteArray.decodeResponse(): String {
    val nul = indexOf(0)
    return decodeToString(0, if (nul >= 0) nul else size)
}

actual fun cactusSetBackend(backend: String): Int = if (backend == "cpu") 0 else -1

actual fun cactusInit(modelPath: String, corpusDir: String?, cacheIndex: Boolean): Long {
    val handle = CactusJNI.nativeInit(modelPath, corpusDir, cacheIndex)
    if (handle == 0L) throw RuntimeException(CactusJNI.nativeGetLastError().ifEmpty { "Failed to initialize model" })
    return handle
}

actual fun cactusDestroy(handle: Long) = CactusJNI.nativeDestroy(handle)
actual fun cactusReset(handle: Long) = CactusJNI.nativeReset(handle)
actual fun cactusStop(handle: Long) = CactusJNI.nativeStop(handle)

actual fun cactusComplete(handle: Long, messagesJson: String, optionsJson: String?, toolsJson: String?, callback: CactusTokenCallback?, pcmData: ByteArray?): String {
    val buffer = ByteArray(1024 * 1024)
    check(CactusJNI.nativeComplete(handle, messagesJson, buffer, optionsJson, toolsJson, callback, pcmData))
    return buffer.decodeResponse()
}

actual fun cactusPrefill(handle: Long, messagesJson: String, optionsJson: String?, toolsJson: String?, pcmData: ByteArray?): String {
    val buffer = ByteArray(1024 * 1024)
    check(CactusJNI.nativePrefill(handle, messagesJson, buffer, optionsJson, toolsJson, pcmData))
    return buffer.decodeResponse()
}

actual fun cactusTokenize(handle: Long, text: String): IntArray {
    val tokenBuffer = IntArray(8192)
    val outLen = LongArray(1)
    check(CactusJNI.nativeTokenize(handle, text, tokenBuffer, outLen))
    return tokenBuffer.copyOf(outLen[0].toInt())
}

actual fun cactusScoreWindow(handle: Long, tokens: IntArray, start: Long, end: Long, context: Long): String {
    val buffer = ByteArray(1024 * 1024)
    check(CactusJNI.nativeScoreWindow(handle, tokens, start, end, context, buffer))
    return buffer.decodeResponse()
}

actual fun cactusTranscribe(handle: Long, audioPath: String?, prompt: String, optionsJson: String?, callback: CactusTokenCallback?, pcmData: ByteArray?): String {
    val buffer = ByteArray(1024 * 1024)
    check(CactusJNI.nativeTranscribe(handle, audioPath, prompt, buffer, optionsJson, callback, pcmData))
    return buffer.decodeResponse()
}

actual fun cactusStreamTranscribeStart(handle: Long, optionsJson: String?): Long {
    val stream = CactusJNI.nativeStreamTranscribeStart(handle, optionsJson)
    if (stream == 0L) throw RuntimeException(CactusJNI.nativeGetLastError().ifEmpty { "Failed to start streaming transcription" })
    return stream
}

actual fun cactusStreamTranscribeProcess(stream: Long, pcmData: ByteArray?): String {
    val buffer = ByteArray(65536)
    check(CactusJNI.nativeStreamTranscribeProcess(stream, pcmData, buffer))
    return buffer.decodeResponse()
}

actual fun cactusStreamTranscribeStop(stream: Long): String {
    val buffer = ByteArray(65536)
    check(CactusJNI.nativeStreamTranscribeStop(stream, buffer))
    return buffer.decodeResponse()
}

actual fun cactusEmbed(handle: Long, text: String, normalize: Boolean): FloatArray {
    val buffer = FloatArray(4096)
    val outDim = LongArray(1)
    check(CactusJNI.nativeEmbed(handle, text, buffer, outDim, normalize))
    return buffer.copyOf(outDim[0].toInt())
}

actual fun cactusImageEmbed(handle: Long, imagePath: String): FloatArray {
    val buffer = FloatArray(4096)
    val outDim = LongArray(1)
    check(CactusJNI.nativeImageEmbed(handle, imagePath, buffer, outDim))
    return buffer.copyOf(outDim[0].toInt())
}

actual fun cactusAudioEmbed(handle: Long, audioPath: String): FloatArray {
    val buffer = FloatArray(4096)
    val outDim = LongArray(1)
    check(CactusJNI.nativeAudioEmbed(handle, audioPath, buffer, outDim))
    return buffer.copyOf(outDim[0].toInt())
}

actual fun cactusRagQuery(handle: Long, query: String, topK: Long): String {
    val buffer = ByteArray(1024 * 1024)
    check(CactusJNI.nativeRagQuery(handle, query, buffer, topK))
    return buffer.decodeResponse()
}

actual fun cactusIndexInit(indexDir: String, embeddingDim: Long): Long {
    val handle = CactusJNI.nativeIndexInit(indexDir, embeddingDim)
    if (handle == 0L) throw RuntimeException(CactusJNI.nativeGetLastError().ifEmpty { "Failed to initialize index" })
    return handle
}

actual fun cactusIndexAdd(handle: Long, ids: IntArray, documents: Array<String>, metadatas: Array<String>?, embeddings: Array<FloatArray>, embeddingDim: Long): Int {
    val rc = CactusJNI.nativeIndexAdd(handle, ids, documents, metadatas, embeddings, embeddingDim)
    check(rc)
    return rc
}

actual fun cactusIndexDelete(handle: Long, ids: IntArray): Int {
    val rc = CactusJNI.nativeIndexDelete(handle, ids)
    check(rc)
    return rc
}

actual fun cactusIndexGet(handle: Long, ids: IntArray): String {
    val count = ids.size
    val docBuffers = Array(count) { ByteArray(4096) }
    val docSizes = LongArray(count) { 4096L }
    val metaBuffers = Array(count) { ByteArray(4096) }
    val metaSizes = LongArray(count) { 4096L }
    val embBuffers = Array(count) { FloatArray(4096) }
    val embSizes = LongArray(count) { 4096L }
    check(CactusJNI.nativeIndexGet(handle, ids, docBuffers, docSizes, metaBuffers, metaSizes, embBuffers, embSizes))
    return encodeIndexEntries(
        ids.indices.map { i ->
            IndexEntry(docBuffers[i].decodeResponse(), metaBuffers[i].decodeResponse())
        }
    )
}

actual fun cactusIndexQuery(handle: Long, embedding: FloatArray, optionsJson: String?): String {
    val idBuffers = arrayOf(IntArray(1000))
    val idSizes = longArrayOf(1000L)
    val scoreBuffers = arrayOf(FloatArray(1000))
    val scoreSizes = longArrayOf(1000L)
    check(CactusJNI.nativeIndexQuery(handle, arrayOf(embedding), embedding.size.toLong(), optionsJson, idBuffers, idSizes, scoreBuffers, scoreSizes))
    return encodeIndexMatches(
        (0 until idSizes[0].toInt()).map { i -> IndexMatch(idBuffers[0][i], scoreBuffers[0][i]) }
    )
}

actual fun cactusIndexCompact(handle: Long): Int {
    val rc = CactusJNI.nativeIndexCompact(handle)
    check(rc)
    return rc
}

actual fun cactusIndexDestroy(handle: Long) = CactusJNI.nativeIndexDestroy(handle)
actual fun cactusGetLastError(): String = CactusJNI.nativeGetLastError()
actual fun cactusLogSetLevel(level: Int) = CactusJNI.nativeLogSetLevel(level)
actual fun cactusLogSetCallback(callback: CactusLogCallback?) = CactusJNI.nativeLogSetCallback(callback)

actual fun cactusSetTelemetryEnvironment(framework: String?, cacheLocation: String?, version: String?) =
    CactusJNI.nativeSetTelemetryEnvironment(framework, cacheLocation, version)

actual fun cactusSetAppId(appId: String) = CactusJNI.nativeSetAppId(appId)
actual fun cactusTelemetryFlush() = CactusJNI.nativeTelemetryFlush()
actual fun cactusTelemetryShutdown() = CactusJNI.nativeTelemetryShutdown()
