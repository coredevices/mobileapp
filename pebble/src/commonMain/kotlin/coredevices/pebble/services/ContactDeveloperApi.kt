package coredevices.pebble.services

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.random.Random
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class ContactAttachment(
    val fileName: String,
    val mimeType: String?,
    val bytes: ByteArray,
)

sealed class ContactResult {
    data object Success : ContactResult()
    data object NotSignedIn : ContactResult()
    data object EmailNotVerified : ContactResult()
    data class NotContactable(val message: String) : ContactResult()
    data class RateLimited(val message: String) : ContactResult()
    data class BadRequest(val message: String) : ContactResult()
    data object NetworkError : ContactResult()
    data class ServerError(val message: String) : ContactResult()
}

@Serializable
private data class ErrorBody(val error: String? = null)

class ContactDeveloperApi(
    private val httpClient: HttpClient,
    private val json: Json,
) {
    private val logger = Logger.withTag("ContactDeveloperApi")

    suspend fun sendMessage(
        appId: String,
        message: String,
        attachments: List<ContactAttachment>,
    ): ContactResult {
        // Force-refresh: a cached token retains stale `email_verified=false`
        // claims after the user verifies their email this session.
        val token = try {
            Firebase.auth.currentUser?.getIdToken(true)
        } catch (e: Exception) {
            logger.w(e) { "Failed to get Firebase ID token" }
            null
        }
        if (token == null) {
            return ContactResult.NotSignedIn
        }

        val boundary = "----CoreAppContactBoundary${Random.nextLong().toString(16)}"
        val body = buildMultipartBody(boundary, message, attachments)

        val url = "$PEBBLE_FEED_URL/v1/apps/$appId/contact-developer"
        val response = try {
            httpClient.post(url) {
                bearerAuth(token)
                contentType(ContentType.parse("multipart/form-data; boundary=$boundary"))
                setBody(body)
            }
        } catch (e: IOException) {
            logger.w(e) { "Network error contacting developer: ${e.message}" }
            return ContactResult.NetworkError
        }

        return when (val code = response.status.value) {
            201 -> ContactResult.Success
            401 -> {
                val errBody = response.parseError()
                if (errBody == "Email not verified") ContactResult.EmailNotVerified
                else ContactResult.NotSignedIn
            }
            403 -> ContactResult.NotContactable(response.parseError() ?: "This developer is not reachable.")
            429 -> ContactResult.RateLimited(response.parseError() ?: "You are sending messages too quickly. Try again later.")
            400 -> ContactResult.BadRequest(response.parseError() ?: "Message or attachment was rejected.")
            else -> ContactResult.ServerError(response.parseError() ?: "Server error ($code)")
        }
    }

    private fun buildMultipartBody(
        boundary: String,
        message: String,
        attachments: List<ContactAttachment>,
    ): ByteArray {
        val out = Buffer()
        // message field
        out.write("--$boundary\r\n".encodeToByteArray())
        out.write("Content-Disposition: form-data; name=\"message\"\r\n\r\n".encodeToByteArray())
        out.write(message.encodeToByteArray())
        out.write("\r\n".encodeToByteArray())
        // attachments
        for (a in attachments) {
            val safeName = a.fileName.replace("\"", "").replace("\n", " ").replace("\r", " ")
            out.write("--$boundary\r\n".encodeToByteArray())
            out.write(
                "Content-Disposition: form-data; name=\"attachment\"; filename=\"$safeName\"\r\n"
                    .encodeToByteArray()
            )
            val mime = a.mimeType ?: "application/octet-stream"
            out.write("Content-Type: $mime\r\n\r\n".encodeToByteArray())
            out.write(a.bytes)
            out.write("\r\n".encodeToByteArray())
        }
        out.write("--$boundary--\r\n".encodeToByteArray())
        return out.readByteArray()
    }

    private suspend fun io.ktor.client.statement.HttpResponse.parseError(): String? {
        return try {
            val text = body<String>()
            if (text.isBlank()) null else json.decodeFromString(ErrorBody.serializer(), text).error
        } catch (e: Exception) {
            null
        }
    }
}
