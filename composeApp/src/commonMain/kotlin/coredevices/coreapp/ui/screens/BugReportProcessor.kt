package coredevices.coreapp.ui.screens

import DocumentAttachment
import co.touchlab.kermit.Logger
import coredevices.ExperimentalDevices
import coredevices.coreapp.api.BugApi
import coredevices.coreapp.util.FileLogWriter
import coredevices.coreapp.util.generateDeviceSummary
import coredevices.coreapp.util.getLogsCacheDir
import coredevices.pebble.PebbleAppDelegate
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import size

data class BugReportGenerationParams(
    val userMessage: String,
    val userName: String?,
    val userEmail: String?,
    val screenContext: String,
    val attachments: List<DocumentAttachment>,
    val sendRecording: Boolean,
    val expOutputPath: String?,
    val imageAttachments: List<DocumentAttachment>,
    val fetchPebbleLogs: Boolean,
    val fetchPebbleCoreDump: Boolean,
    val includeExperimentalDebugInfo: Boolean
)

sealed class BugReportState {
    data object Creating : BugReportState()
    data object GatheringWatchLogs : BugReportState()
    data object UploadingAttachments : BugReportState()
    sealed class BugReportResult : BugReportState() {
        data class Success(val bugReportId: String? = null) : BugReportResult()
        data class Failed(val error: String) : BugReportResult()
    }
}

data class AttachmentUploadParams(
    val bugReportId: String,
    val attachments: List<DocumentAttachment>,
    val googleIdToken: String?
)

@Serializable
data class AtlasCreateRequest(
    val email: String,
    val description: String,
    val googleIdToken: String? = null
)

@Serializable
data class AtlasCreateResponse(
    val success: Boolean,
    val ticketId: String,
    val appId: String,
    val userHash: String,
    val userId: String
)

expect fun startForegroundService()

expect fun notifyState(message: String)

expect fun stopForegroundService()

class BugReportProcessor(
    private val logWriter: FileLogWriter,
    private val experimentalDevices: ExperimentalDevices,
    private val bugApi: BugApi,
    private val pebbleAppDelegate: PebbleAppDelegate,
) {
    private val logger = Logger.withTag("BugReportProcessor")

    private fun getPKJSSummary(): String {
        return try {
            pebbleAppDelegate.getPKJSSessions()
        } catch (e: Exception) {
            logger.e(e) { "Error grabbing PKJS sessions: ${e.message}" }
            "Error grabbing PKJS sessions\n"
        }
    }

    private suspend fun getPebbleLogFile(): List<DocumentAttachment> {
        val logFile = pebbleAppDelegate.getWatchLogs()
        return if (logFile != null) {
            withContext(Dispatchers.IO) {
                listOf(
                    DocumentAttachment(
                        fileName = "watch-logs.txt",
                        mimeType = "text/plain",
                        source = SystemFileSystem.source(logFile).buffered(),
                        size = logFile.size(),
                    )
                )
            }
        } else {
            emptyList()
        }
    }

    private suspend fun getPebbleCoreDump(): List<DocumentAttachment> {
        val coreDumpFile = pebbleAppDelegate.getCoreDump()
        return if (coreDumpFile != null) {
            withContext(Dispatchers.IO) {
                listOf(
                    DocumentAttachment(
                        fileName = "core-dump-unencrypted.bin",
                        mimeType = null,
                        source = SystemFileSystem.source(coreDumpFile).buffered(),
                        size = coreDumpFile.size(),
                    )
                )
            }
        } else {
            emptyList()
        }
    }

    private fun createSummaryAttachment(attachments: List<DocumentAttachment>): DocumentAttachment {
        val summaryWithAttachmentCount =
            createSummary("", attachments)
        val summaryFile = Path(getLogsCacheDir() + "/summary.txt")
        SystemFileSystem.sink(summaryFile, append = false).buffered().use { sink ->
            sink.writeString(summaryWithAttachmentCount)
        }
        return DocumentAttachment(
            fileName = "bugreport_summary.txt",
            mimeType = "text/plain",
            source = SystemFileSystem.source(summaryFile).buffered(),
            size = summaryFile.size(),
        )
    }

    fun updateBugReportWithNewLogs(bugReportId: String) {
        processBugReport { state, userIdToken ->
            state.value = BugReportState.GatheringWatchLogs
            val logsPath = logWriter.dumpLogs()
            val attachments = getPebbleLogFile() +
                    getPebbleCoreDump() +
                    DocumentAttachment(
                        fileName = "full_logs.txt",
                        mimeType = "text/plain",
                        source = SystemFileSystem.source(logsPath).buffered(),
                        size = logsPath.size(),
                    )
            val uploadResult = uploadAttachments(
                state = state,
                bugReportId = bugReportId,
                attachments = listOf(createSummaryAttachment(attachments)) + attachments,
                googleIdToken = userIdToken,
            )
            if (uploadResult.isSuccess) {
                state.value = BugReportState.BugReportResult.Success(bugReportId)
            } else {
                state.value = BugReportState.BugReportResult.Failed(
                    uploadResult.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
        }
    }

    fun updateBugReportWithNewAttachments(
        bugReportId: String,
        attachments: List<DocumentAttachment>
    ) {
        processBugReport { state, userIdToken ->
            val uploadResult = uploadAttachments(
                state = state,
                bugReportId = bugReportId,
                attachments = listOf(createSummaryAttachment(attachments)) + attachments,
                googleIdToken = userIdToken,
            )
            if (uploadResult.isSuccess) {
                state.value = BugReportState.BugReportResult.Success(bugReportId)
            } else {
                state.value = BugReportState.BugReportResult.Failed(
                    uploadResult.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
        }
    }

    private fun createSummary(
        screenContext: String,
        attachments: List<DocumentAttachment>
    ): String {
        val deviceSummary = generateDeviceSummary(experimentalDevices)
        val pkjsSummary = getPKJSSummary()
        val summaryWithAttachmentCount = buildString {
            append(deviceSummary)
            append(pkjsSummary)
            if (screenContext.isNotEmpty()) {
                append("\n${screenContext}")
            }
            attachments.onEach {
                append("\nAttachment: ${it.fileName}")
            }
        }
        return summaryWithAttachmentCount
    }

    fun canSendReports(): Boolean = bugApi.canUseService()

    fun newBugReport(params: BugReportGenerationParams): Flow<BugReportState> {
        return processBugReport { state, userIdToken ->
            logger.d { "processBugReport - Phase 1: Creating bug report without attachments" }
            val summaryWithAttachmentCount =
                createSummary(params.screenContext, params.attachments + params.imageAttachments)
            val logs = logWriter.dumpLogs()

            // Collect all attachments to get count for summary
            val lastNLines = logs.readMostRecent(25000) ?: "<no logs>"

            // Phase 1: Submit bug report without attachments
            val bugReportResult = try {
                bugApi.reportBug(
                    details = params.userMessage,
                    username = params.userName ?: "Unknown",
                    email = params.userEmail ?: "Unknown",
                    timezone = TimeZone.currentSystemDefault().id,
                    summary = summaryWithAttachmentCount,
                    latestLogs = lastNLines,
                    googleIdToken = userIdToken,
                    sourceIsExperimentalDevice = params.includeExperimentalDebugInfo
                )
            } catch (e: Exception) {
                Logger.e(e) { "Failed to send bug report" }
                // Provide user-friendly error message
                val userMessage = when {
                    e.message?.contains("Authentication failed") == true -> e.message!!
                    e.message?.contains("Please fill in") == true -> e.message!!
                    e.message?.contains("Please enter") == true -> e.message!!
                    e.message?.contains("Please describe") == true -> e.message!!
                    e.message?.contains("Unable to submit") == true -> e.message!!
                    e.message?.contains("network") == true -> "Network error. Please check your connection and try again."
                    e.message?.contains("timeout") == true -> "Request timed out. Please try again."
                    else -> "Unable to submit bug report. Please try again later."
                }
                state.value = BugReportState.BugReportResult.Failed(userMessage)
                return@processBugReport
            }

            // Store Atlas ticket info for navigation
            bugReportResult.response?.atlas?.let { atlasInfo ->
                logger.d { "Ticket created: ${atlasInfo.ticketId}" }
            }

            // Get bug report ID for Phase 2
            val bugReportId = bugReportResult.response?.bugReportId
                ?: bugReportResult.response?.atlas?.ticketId
                ?: bugReportResult.response?.linear?.id

            if (bugReportId == null) {
                logger.e { "No bug report ID returned from server" }
                state.value =
                    BugReportState.BugReportResult.Failed("Bug report created but no ID returned")
                return@processBugReport
            }

            val attachments = gatherAttachments(params, state) + DocumentAttachment(
                fileName = "full_logs.txt",
                mimeType = "text/plain",
                source = SystemFileSystem.source(logs).buffered(),
                size = logs.size(),
            )
            val uploadResult = uploadAttachments(
                state = state,
                bugReportId = bugReportId,
                attachments = attachments,
                googleIdToken = userIdToken,
            )
            if (uploadResult.isSuccess) {
                state.value = BugReportState.BugReportResult.Success(bugReportId)
            } else {
                state.value = BugReportState.BugReportResult.Failed(
                    uploadResult.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
        }
    }

    private fun processBugReport(block: suspend (state: MutableStateFlow<BugReportState>, userIdToken: String) -> Unit): Flow<BugReportState> {
        val state = MutableStateFlow<BugReportState>(BugReportState.Creating)
        startForegroundService()
        notifyState("Creating bug report...")
        GlobalScope.launch {
            state.transformWhile {
                emit(it)
                it !is BugReportState.BugReportResult
            }.collect {
                when (it) {
                    BugReportState.Creating -> Unit
                    BugReportState.GatheringWatchLogs -> notifyState("Gathering watch logs...")
                    BugReportState.UploadingAttachments -> notifyState("Uploading attachments")
                    is BugReportState.BugReportResult.Failed -> notifyState("Bug report failed: ${it.error}")
                    is BugReportState.BugReportResult.Success -> notifyState("Bug report successfully uploaded!")
                }
            }
            logger.d { "Bug report processing complete; stopping service" }
            stopForegroundService()
        }
        GlobalScope.launch(Dispatchers.IO) {
            val userIdToken = try {
                Firebase.auth.currentUser?.getIdToken(false)
            } catch (e: Exception) {
                logger.e(e) { "No user token: ${e.message}" }
                null
            }
            if (userIdToken == null) {
                state.value = BugReportState.BugReportResult.Failed("Invalid user ID")
                return@launch
            }
            block(state, userIdToken)
        }
        return state
    }

    fun sourceFromByteArray(byteArray: ByteArray): Source {
        val buffer = Buffer()
        buffer.write(byteArray)
        return buffer
    }

    private suspend fun gatherAttachments(
        params: BugReportGenerationParams,
        state: MutableStateFlow<BugReportState>
    ): List<DocumentAttachment> {
        // Phase 2: Gather attachments (including watch logs/core dump)
        state.value = BugReportState.GatheringWatchLogs

        // Add full logs as the first attachment (if available)
        val attachments = mutableListOf<DocumentAttachment>()

        // Add user attachments
        attachments.addAll(params.attachments)
        attachments.addAll(params.imageAttachments)

        val screenContextByteArray = params.screenContext.encodeToByteArray()
        attachments.add(
            DocumentAttachment(
                fileName = "screen-context.json",
                mimeType = "application/json",
                source = sourceFromByteArray(screenContextByteArray),
                size = screenContextByteArray.size.toLong(),
            )
        )

        // Add recording if requested
        if (params.sendRecording && params.expOutputPath != null) {
            withContext(Dispatchers.IO) {
                experimentalDevices.exportOutput(params.expOutputPath)?.let {
                    attachments.add(it)
                }
            }
        }

        // Add debug info files
        if (params.includeExperimentalDebugInfo) {
            val experimentalDebugInfoPath = getExperimentalDebugInfoDirectory()
            try {
                SystemFileSystem.list(Path(experimentalDebugInfoPath))
                    .sortedByDescending { it.name }
                    .take(10)
                    .forEach {
                        attachments.add(
                            DocumentAttachment(
                                fileName = it.name,
                                mimeType = "application/json",
                                SystemFileSystem.source(it).buffered(),
                                size = it.size(),
                            )
                        )
                    }
            } catch (e: Exception) {
                logger.e(e) { "Failed to collect experimental debug info files" }
            }
        }

        if (params.fetchPebbleLogs) {
            attachments.addAll(getPebbleLogFile())
        }
        if (params.fetchPebbleCoreDump) {
            attachments.addAll(getPebbleCoreDump())
        }
        return attachments
    }

    suspend fun uploadAttachments(
        state: MutableStateFlow<BugReportState>,
        bugReportId: String,
        attachments: List<DocumentAttachment>,
        googleIdToken: String?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        state.value = BugReportState.UploadingAttachments
        try {
            logger.d { "Starting upload of ${attachments.size} attachments for bug report $bugReportId" }

            if (attachments.isEmpty()) {
                return@withContext Result.success(Unit)
            }

            val fileMetadata = attachments.map {
                BugApi.FileMetadata(
                    fileName = it.fileName,
                    fileType = it.mimeType ?: "application/octet-stream",
                    fileSize = it.size,
                )
            }

            // Step 2: Get presigned URLs for all files
            val presignedResult = bugApi.getPresignedUrls(fileMetadata, googleIdToken)
            if (presignedResult.isFailure) {
                logger.e { "Failed to get presigned URLs: ${presignedResult.exceptionOrNull()}" }
                return@withContext Result.failure(Exception("Unable to prepare file uploads. Please check your connection and try again."))
            }

            val presignedResponse = presignedResult.getOrThrow()
            if (!presignedResponse.success || presignedResponse.uploads == null) {
                logger.e { "Failed to get presigned URLs: ${presignedResponse.error}" }
                return@withContext Result.failure(Exception("Unable to prepare file uploads. Please try again later."))
            }

            // Step 3: Upload each file to R2
            val uploadedFileKeys = mutableListOf<String>()
            var uploadedCount = 0

            attachments.forEachIndexed { index, attachment ->
                val uploadInfo = presignedResponse.uploads[index]
                logger.d { "Uploading ${attachment.fileName} to R2 (${attachment.size} bytes)" }

                // Upload to presigned URL using pre-read data
                val uploadResult = bugApi.uploadToPresignedUrl(
                    presignedUrl = uploadInfo.uploadUrl,
                    data = attachment.source,
                    contentType = attachment.mimeType ?: "application/octet-stream",
                    size = attachment.size,
                )

                if (uploadResult.isSuccess) {
                    // Extract file key from URL (exact pattern from test script)
                    val fileKey = uploadInfo.fileUrl.split("/").let { parts ->
                        val bucketIndex = parts.indexOf("eng-dash-temp-logs-attachments")
                        parts.subList(bucketIndex + 1, parts.size).joinToString("/")
                    }
                    uploadedFileKeys.add(fileKey)
                    uploadedCount++
                    logger.d { "Successfully uploaded ${attachment.fileName}, key: $fileKey" }

                    // Call upload complete immediately for this file
                    try {
                        val completeResult = bugApi.completeUpload(
                            fileKey = fileKey,
                            bugReportId = bugReportId,
                            googleIdToken = googleIdToken
                        )
                        if (completeResult.isFailure) {
                            logger.e { "Failed to complete upload for ${attachment.fileName}: ${completeResult.exceptionOrNull()}" }
                            // Don't fail the whole process, just log the error
                        }
                    } catch (e: Exception) {
                        logger.e(e) { "Error calling upload complete for ${attachment.fileName}" }
                        // Continue with other files
                    }
                } else {
                    logger.e { "Failed to upload ${attachment.fileName}: ${uploadResult.exceptionOrNull()}" }
                }
            }

            logger.d { "Upload complete: $uploadedCount/${attachments.size} files uploaded successfully" }

            if (uploadedCount == attachments.size) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Some attachments failed to upload ($uploadedCount of ${attachments.size} successful). The bug report was submitted but without all attachments."))
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to upload attachments" }
            val userMessage = when {
                e.message?.contains("Unable to prepare") == true -> e.message!!
                e.message?.contains("Network") == true -> "Network error during file upload. Please check your connection and try again."
                e.message?.contains("timeout") == true -> "File upload timed out. Please try again with a better connection."
                else -> "Unable to upload attachments. Please try again later."
            }
            Result.failure(Exception(userMessage))
        }
    }
}

fun Path?.readMostRecent(bytes: Int): String? {
    if (this == null) return null
    return try {
        SystemFileSystem.source(this).buffered().use { source ->
            val size = SystemFileSystem.metadataOrNull(this)?.size ?: 0
            val offset = maxOf(0, size - bytes)
            source.skip(offset)
            source.readString()
        }
    } catch (e: Exception) {
        Logger.e(e) { "Failed to read most recent $bytes bytes from $this" }
        null
    }
}
