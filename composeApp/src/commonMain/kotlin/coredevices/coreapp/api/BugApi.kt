package coredevices.coreapp.api

import CommonApiConfig
import co.touchlab.kermit.Logger
import coredevices.api.ApiClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.io.Source
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlin.time.Duration.Companion.seconds

class BugApi(
    config: CommonApiConfig,
): ApiClient(config.version, 120.seconds) {
    private val baseUrl = config.bugUrl

    companion object {
        private val logger = Logger.withTag(BugApi::class.simpleName!!)
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    @Serializable
    private data class BugReport(
        val bugReportDetails: String,
        val username: String,
        val email: String,
        val timezone: String,
        val summary: String,
        val latestLogs: String,
        val googleIdToken: String? = null
    )

    @Serializable
    data class AtlasTicketInfo(
        val ticketId: String,
        val appId: String,
        val userHash: String,
        val userId: String
    )

    @Serializable
    data class LinearIssueInfo(
        val id: String,
        val title: String,
        val url: String
    )

    @Serializable
    data class UnifiedBugReportResponse(
        val success: Boolean,
        val atlas: AtlasTicketInfo? = null,
        val linear: LinearIssueInfo? = null,
        val bugReportId: String? = null
    )

    @Serializable
    data class FileMetadata(
        val fileName: String,
        val fileType: String,
        val fileSize: Long
    )

    @Serializable
    data class PresignedUrlRequest(
        val files: List<FileMetadata>
    )

    @Serializable
    data class UploadInfo(
        val fileName: String,
        val uploadUrl: String,
        val fileUrl: String
    )

    @Serializable
    data class PresignedUrlResponse(
        val success: Boolean,
        val uploads: List<UploadInfo>? = null,
        val error: String? = null
    )
    
    @Serializable
    data class UploadCompleteRequest(
        val fileKeys: JsonElement, // Can be JsonPrimitive (string) or JsonArray
        val bugReportId: String
    )
    
    @Serializable
    data class UploadCompleteResponse(
        val success: Boolean,
        val message: String? = null,
        val error: String? = null
    )

    data class BugReportResult(
        val response: UnifiedBugReportResponse?,
    )

    fun canUseService(): Boolean = baseUrl != null

    suspend fun reportBug(
        details: String,
        username: String,
        email: String,
        timezone: String,
        summary: String,
        latestLogs: String,
        googleIdToken: String? = null
    ): BugReportResult {
        val url = "$baseUrl/bug-reports/create"
        
        try {
            // Use simple JSON POST instead of multipart
            val resp = client.post(url) {
                if (googleIdToken != null) {
                    headers {
                        append("X-Google-ID-Token", googleIdToken)
                    }
                }
                contentType(ContentType.Application.Json)
                setBody(BugReport(
                    bugReportDetails = details,
                    username = username,
                    email = email,
                    timezone = timezone,
                    summary = summary,
                    latestLogs = latestLogs,
                    googleIdToken = null // Don't send in body anymore
                ))
            }
            
            if (!resp.status.isSuccess()) {
                val body = resp.bodyAsText()
                logger.e {"Failed to create bug report:\n$body"}
                
                // Check for authentication errors
                if (resp.status.value == 401) {
                    throw Exception("Authentication failed. Please sign in with Google and try again.")
                } else {
                    // Parse and provide user-friendly error messages
                    val errorMessage = try {
                        val errorData = json.decodeFromString<Map<String, String>>(body)
                        when {
                            errorData["error"]?.contains("Email and description are required") == true ->
                                "Please fill in both your email and bug description before submitting."
                            errorData["error"]?.contains("Email") == true ->
                                "Please enter a valid email address."
                            errorData["error"]?.contains("description") == true ->
                                "Please describe the bug you encountered."
                            else -> errorData["error"] ?: "Unable to submit bug report. Please try again."
                        }
                    } catch (e: Exception) {
                        "Unable to submit bug report. Please check your connection and try again."
                    }
                    throw Exception(errorMessage)
                }
            }
            
            val body = resp.bodyAsText()
            val responseData = json.decodeFromString<UnifiedBugReportResponse>(body)

            return BugReportResult(responseData)
        } catch (e: Exception) {
            logger.e(e) { "Failed to create bug report: ${e.message}" }
            throw e
        }
    }

    suspend fun getPresignedUrls(
        fileMetadata: List<FileMetadata>,
        googleIdToken: String? = null
    ): Result<PresignedUrlResponse> {
        return try {
            logger.d { "Getting presigned URLs for ${fileMetadata.size} files" }
            
            val response = client.post("$baseUrl/upload/presigned") {
                if (googleIdToken != null) {
                    headers {
                        append("X-Google-ID-Token", googleIdToken)
                    }
                }
                contentType(ContentType.Application.Json)
                setBody(PresignedUrlRequest(files = fileMetadata))
            }
            
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val presignedResponse = json.decodeFromString<PresignedUrlResponse>(body)
                Result.success(presignedResponse)
            } else {
                val error = "Failed to get presigned URLs: ${response.status}"
                logger.e { error }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to get presigned URLs" }
            Result.failure(e)
        }
    }
    
    suspend fun uploadToPresignedUrl(
        presignedUrl: String,
        data: Source,
        size: Long,
        contentType: String
    ): Result<Unit> {
        return try {
            val response = client.put(presignedUrl) {
                headers {
                    append(HttpHeaders.ContentType, contentType)
                    append(HttpHeaders.ContentLength, size.toString())
                }
                timeout {
                    requestTimeoutMillis = 40.seconds.inWholeMilliseconds
                }
                setBody(object : OutgoingContent.ReadChannelContent() {
                    override val contentType: ContentType = ContentType.parse(contentType)
                    override val contentLength: Long = size

                    override fun readFrom(): ByteReadChannel {
                        return GlobalScope.writer(Dispatchers.IO) { // Or a more specific scope
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            try {
                                while (data.readAtMostTo(buffer).also { bytesRead = it } != -1) {
                                    if (bytesRead > 0) {
                                        channel.writeFully(buffer, 0, bytesRead)
                                    }
                                }
                            } finally {
                                data.close()
                            }
                        }.channel
                    }
                })
            }
            
            if (response.status.value in 200..299) {
                logger.d { "Successfully uploaded to R2 with status ${response.status.value}" }
                Result.success(Unit)
            } else {
                val error = "Upload failed with status ${response.status.value}"
                logger.e { error }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to upload to presigned URL" }
            Result.failure(e)
        }
    }
    
    // Overload for single file key
    suspend fun completeUpload(
        fileKey: String,
        bugReportId: String,
        googleIdToken: String? = null
    ): Result<Unit> {
        return completeUpload(listOf(fileKey), bugReportId, googleIdToken)
    }
    
    suspend fun completeUpload(
        fileKeys: List<String>,
        bugReportId: String,
        googleIdToken: String? = null
    ): Result<Unit> {
        return try {
            logger.d { "Calling upload complete for ${fileKeys.size} files, bug report: $bugReportId" }
            
            // Convert fileKeys to JsonElement - use array if multiple, primitive if single
            val fileKeysJson: JsonElement = if (fileKeys.size == 1) {
                JsonPrimitive(fileKeys[0])
            } else {
                buildJsonArray {
                    fileKeys.forEach { add(JsonPrimitive(it)) }
                }
            }
            
            val response = client.post("$baseUrl/upload/complete") {
                if (googleIdToken != null) {
                    headers {
                        append("X-Google-ID-Token", googleIdToken)
                    }
                }
                contentType(ContentType.Application.Json)
                setBody(UploadCompleteRequest(
                    fileKeys = fileKeysJson,
                    bugReportId = bugReportId
                ))
            }
            
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val completeResponse = json.decodeFromString<UploadCompleteResponse>(body)
                if (completeResponse.success) {
                    logger.d { "Upload completion successful: ${completeResponse.message}" }
                    Result.success(Unit)
                } else {
                    logger.e { "Upload completion failed: ${completeResponse.error}" }
                    Result.failure(Exception(completeResponse.error ?: "Upload completion failed"))
                }
            } else {
                val error = "Failed to complete upload: ${response.status}"
                logger.e { error }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to complete upload" }
            Result.failure(e)
        }
    }
}