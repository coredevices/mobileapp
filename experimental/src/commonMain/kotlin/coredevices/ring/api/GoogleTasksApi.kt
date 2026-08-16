package coredevices.ring.api

import co.touchlab.kermit.Logger
import coredevices.api.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class GoogleTask(
    val id: String? = null,
    val title: String? = null,
    val due: String? = null,
    val status: String? = null,
)

@Serializable
data class GoogleTaskList(
    val id: String? = null,
    val title: String? = null,
)

@Serializable
data class GoogleTaskListsResponse(
    val items: List<GoogleTaskList>? = null
)

class GoogleTasksApi(config: ApiConfig) : ApiClient(config.version) {
    private val baseUrl = "https://www.googleapis.com/tasks/v1"

    companion object {
        val SCOPES = listOf("https://www.googleapis.com/auth/tasks")
    }

    suspend fun createTask(token: String, title: String, due: LocalDate?, listId: String?): GoogleTask {
        val body = buildMap {
            put("title", title)
            // Tasks records only the date; midnight UTC is the form it normalizes `due` to.
            due?.let { put("due", "${it}T00:00:00.000Z") }
        }
        val list = listId ?: "@default"
        val res = client.post("$baseUrl/lists/$list/tasks") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        Logger.withTag("GoogleTasksApi").d { "Create task response: ${res.bodyAsText()}" }
        if (!res.status.isSuccess()) {
            error("Failed to create Google Task: ${res.status}")
        }
        return res.body()
    }

    suspend fun deleteTask(token: String, taskId: String, listId: String?) {
        val list = listId ?: "@default"
        val res = client.delete("$baseUrl/lists/@default/tasks/$taskId") {
            bearerAuth(token)
        }
        if (!res.status.isSuccess()) {
            error("Failed to delete Google Task: ${res.status}")
        }
    }

    suspend fun getTaskLists(token: String): List<GoogleTaskList> {
        val res = client.get("$baseUrl/users/@me/lists") {
            bearerAuth(token)
        }
        if (!res.status.isSuccess()) {
            error("Failed to fetch task lists: ${res.status}")
        }
        return res.body<GoogleTaskListsResponse>().items ?: emptyList()
    }
}
