package coredevices.ring.util

import dev.gitlive.firebase.storage.StorageReference
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatform
import kotlin.time.Duration.Companion.seconds

suspend fun StorageReference.openReadChannel(): ByteReadChannel {
    val url = getDownloadUrl()
    val httpClient: HttpClient = HttpClient(KoinPlatform.getKoin().get<HttpClientEngine> { parametersOf(5.seconds) })
    val response = httpClient.get(url) {
        //bearerAuth(Firebase.auth.currentUser!!.getIdToken(false)!!)
    }
    check(response.status.isSuccess()) { "Failed to download recording: ${response.status}" }
    return response.bodyAsChannel()
}