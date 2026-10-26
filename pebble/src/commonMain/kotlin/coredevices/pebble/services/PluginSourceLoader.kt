package coredevices.pebble.services

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.disk.pbw.DiskUtil
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.uuid.Uuid

/** Reads a plugin's script: from the installed pbw when the app is in the locker, else by fetching the store's pbw. */
class PluginSourceLoader(
    private val libPebble: LibPebble,
    private val httpClient: HttpClient,
) {
    private val logger = Logger.withTag("PluginSourceLoader")

    suspend fun load(uuid: Uuid?, pbwUrl: String?, script: String): String? {
        if (uuid != null) {
            libPebble.getAppFile(uuid, script)?.let { return it }
        }
        if (pbwUrl == null) return null
        val path = Path(SystemTemporaryDirectory, "plugin_source_${Uuid.random()}.pbw")
        return try {
            download(pbwUrl, path)
            DiskUtil.getPbwTextFile(path, script)
        } catch (e: Exception) {
            logger.w(e) { "Failed to fetch plugin source from $pbwUrl" }
            null
        } finally {
            SystemFileSystem.delete(path, mustExist = false)
        }
    }

    private suspend fun download(url: String, path: Path) {
        httpClient.prepareGet(url).execute { response ->
            check(response.status.isSuccess()) { "pbw download failed: ${response.status}" }
            SystemFileSystem.sink(path).use { sink ->
                response.bodyAsChannel().readRemaining().transferTo(sink)
            }
        }
    }
}
