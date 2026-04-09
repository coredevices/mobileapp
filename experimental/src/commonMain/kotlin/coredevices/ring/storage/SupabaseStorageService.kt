package coredevices.ring.storage

import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.buffered
import kotlin.uuid.Uuid

data class SupabaseConfig(
    val url: String,
    val anonKey: String,
    val bucketName: String
) {
    companion object {
        // TODO: Replace with your actual Supabase credentials
        val DEFAULT = SupabaseConfig(
            url = "https://mamakztargnvtkhqjynx.storage.supabase.co",
            anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1hbWFrenRhcmdudnRraHFqeW54Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTQ5NDU5MzIsImV4cCI6MjA3MDUyMTkzMn0.zMfcIDGm-0NY-H6humbQAjMN1WtM7LnyolWmJornZMI",
            bucketName = "ring-recordings"
        )
    }
}

class SupabaseStorageService(
    private val httpClient: HttpClient,
    private val supabaseUrl: String,
    private val supabaseKey: String,
    private val bucketName: String = "ring-recordings"
) {
    companion object {
        private val logger = Logger.withTag(SupabaseStorageService::class.simpleName!!)
    }

    /**
     * Upload a WAV file to Supabase Storage
     * @param filePath The local path to the WAV file
     * @param fileName Optional custom filename. If null, generates a unique name
     * @return The public URL of the uploaded file, or null if upload failed
     */
    suspend fun uploadWavFile(filePath: Path, fileName: String? = null): String? = withContext(Dispatchers.Default) {
        try {
            logger.d { "Starting upload of WAV file: $filePath" }

            // Check if file exists
            if (!SystemFileSystem.exists(filePath)) {
                logger.e { "File does not exist: $filePath" }
                return@withContext null
            }

            // Read file content
            val fileBytes = SystemFileSystem.source(filePath).buffered().use { source ->
                source.readByteArray()
            }

            logger.d { "File size: ${fileBytes.size} bytes" }

            // Generate filename if not provided
            val uploadFileName = fileName ?: "recording_${Uuid.random()}.wav"
            logger.d { "Upload filename: $uploadFileName" }

            // Upload to Supabase Storage
            val uploadUrl = "$supabaseUrl/storage/v1/object/$bucketName/$uploadFileName"
            logger.d { "Upload URL: $uploadUrl" }

            val response = httpClient.post(uploadUrl) {
                header("Authorization", "Bearer $supabaseKey")
                header("Content-Type", "audio/wav")
                setBody(fileBytes)
            }

            logger.d { "Upload response status: ${response.status}" }

            if (response.status.isSuccess()) {
                val publicUrl = "$supabaseUrl/storage/v1/object/public/$bucketName/$uploadFileName"
                logger.i { "WAV file uploaded successfully: $publicUrl" }
                return@withContext publicUrl
            } else {
                val errorBody = response.bodyAsText()
                logger.e { "Upload failed with status ${response.status}: $errorBody" }
                return@withContext null
            }

        } catch (e: Exception) {
            logger.e(e) { "Error uploading WAV file: ${e.message}" }
            return@withContext null
        }
    }

    /**
     * Delete a file from Supabase Storage
     * @param fileName The name of the file to delete
     * @return true if deletion was successful, false otherwise
     */
    suspend fun deleteFile(fileName: String): Boolean = withContext(Dispatchers.Default) {
        try {
            logger.d { "Deleting file: $fileName" }

            val deleteUrl = "$supabaseUrl/storage/v1/object/$bucketName/$fileName"

            val response = httpClient.delete(deleteUrl) {
                header("Authorization", "Bearer $supabaseKey")
            }

            logger.d { "Delete response status: ${response.status}" }

            if (response.status.isSuccess()) {
                logger.i { "File deleted successfully: $fileName" }
                return@withContext true
            } else {
                val errorBody = response.bodyAsText()
                logger.e { "Delete failed with status ${response.status}: $errorBody" }
                return@withContext false
            }

        } catch (e: Exception) {
            logger.e(e) { "Error deleting file: ${e.message}" }
            return@withContext false
        }
    }
}
