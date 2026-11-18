package io.rebble.libpebblecommon.plugin

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.dao.PluginActionLogDao
import io.rebble.libpebblecommon.database.entity.PluginActionLogEntity
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

fun interface PluginActionRecorder {
    /** Never blocks. */
    fun record(appUuid: Uuid, pluginUuid: Uuid, action: String, args: JsonObject, result: ActionResult)
}

interface PluginActionLogApi {
    /** Actions apps had plugins run, newest first; only those run by [targetUuid] and asked for by [callerUuid] if set. */
    fun pluginActionLog(targetUuid: Uuid? = null, callerUuid: Uuid? = null): Flow<List<PluginActionLogEntity>>
}

class PluginActionLog(
    private val dao: PluginActionLogDao,
    private val scope: LibPebbleCoroutineScope,
    private val clock: Clock,
) : PluginActionRecorder, PluginActionLogApi {
    private val logger = Logger.withTag("PluginActionLog")

    override fun record(appUuid: Uuid, pluginUuid: Uuid, action: String, args: JsonObject, result: ActionResult) {
        val row = PluginActionLogEntity(
            atMs = clock.now().toEpochMilliseconds(),
            appUuid = appUuid,
            pluginUuid = pluginUuid,
            action = action,
            // Only Watch Settings' are ever shown; others' can hold the user's own text.
            args = if (pluginUuid == WatchSettingsPlugin.BUILT_IN_WATCH_SETTINGS_UUID) args.toString().take(MAX_ARGS_CHARS) else "",
            error = if (result.ok) null else listOfNotNull(result.code, result.message).joinToString(": "),
        )
        scope.launch {
            try {
                dao.insertKeeping(row, KEPT_PER_APP)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(e) { "Failed to log $pluginUuid/$action" }
            }
        }
    }

    override fun pluginActionLog(targetUuid: Uuid?, callerUuid: Uuid?): Flow<List<PluginActionLogEntity>> =
        dao.flow(targetUuid, callerUuid)

    suspend fun deleteOlderThan(days: Int) = dao.deleteBefore((clock.now() - days.days).toEpochMilliseconds())

    companion object {
        const val MAX_ARGS_CHARS = 1000
        const val KEPT_PER_APP = 150
    }
}
