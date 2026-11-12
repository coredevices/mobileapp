package io.rebble.libpebblecommon.permissions

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.dao.AppDataUsageDao
import io.rebble.libpebblecommon.database.entity.AppDataUsageEntity
import io.rebble.libpebblecommon.database.entity.UNKNOWN_CONNECTION
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.plugin.Connections
import io.rebble.libpebblecommon.plugin.PhoneNetworkMonitor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

fun interface DataUsageRecorder {
    /** Never blocks: safe from a JS bridge callback. */
    fun record(appUuid: Uuid, url: String, sentBytes: Long, receivedBytes: Long, requests: Int)
}

/** What the app put in a request: the URL and its headers carry data as much as the body does. */
fun requestBytes(method: String, url: String, headers: Map<String, Any>, bodyBytes: Long): Long =
    method.length + url.encodeToByteArray().size + bodyBytes +
        headers.entries.sumOf { (name, value) -> name.length + value.toString().encodeToByteArray().size }

interface AppDataUsageApi {
    /** Every app's traffic over the last [days] days, today included. */
    fun appDataUsage(days: Int): Flow<List<AppDataUsageEntity>>
}

class AppDataUsage(
    private val dao: AppDataUsageDao,
    private val permissions: AppPermissions,
    private val network: PhoneNetworkMonitor,
    private val scope: LibPebbleCoroutineScope,
    private val clock: Clock,
) : DataUsageRecorder, AppDataUsageApi {
    private val logger = Logger.withTag("AppDataUsage")
    private val connection = MutableStateFlow<String?>(null)
    private val pending = Channel<Pending>(Channel.UNLIMITED)

    private class Pending(
        val appUuid: Uuid,
        val url: String,
        val sentBytes: Long,
        val receivedBytes: Long,
        val requests: Int,
        val at: Instant,
        val connection: String?,
    )

    fun init() {
        scope.launch { network.connection.collect { connection.value = it } }
        scope.launch {
            while (true) {
                // Writes whatever has queued up meanwhile together.
                val batch = listOf(pending.receive()) + generateSequence { pending.tryReceive().getOrNull() }
                try {
                    write(batch)
                } catch (e: Exception) {
                    logger.w(e) { "Failed to record data usage" }
                }
            }
        }
    }

    override fun record(appUuid: Uuid, url: String, sentBytes: Long, receivedBytes: Long, requests: Int) {
        pending.trySend(Pending(appUuid, url, sentBytes, receivedBytes, requests, clock.now(), connection.value))
    }

    private suspend fun write(batch: List<Pending>) {
        batch.groupBy { it.appUuid }.forEach { (appUuid, usages) ->
            val keys = permissions.usageKeys(appUuid, usages.map { it.url }.toSet())
            usages
                .mapNotNull { usage ->
                    val key = keys[usage.url] ?: return@mapNotNull null
                    AppDataUsageEntity(
                        appUuid = appUuid,
                        permission = key.permission,
                        day = usage.at.localDay(),
                        connection = usage.connection ?: UNKNOWN_CONNECTION,
                        declared = key.declared,
                        requests = usage.requests.toLong(),
                        sentBytes = usage.sentBytes,
                        receivedBytes = usage.receivedBytes,
                    )
                }
                .groupBy { it.copy(requests = 0, sentBytes = 0, receivedBytes = 0) }
                .forEach { (row, rows) ->
                    dao.add(
                        row.copy(
                            requests = rows.sumOf { it.requests },
                            sentBytes = rows.sumOf { it.sentBytes },
                            receivedBytes = rows.sumOf { it.receivedBytes },
                        )
                    )
                }
        }
    }

    override fun appDataUsage(days: Int): Flow<List<AppDataUsageEntity>> = dao.flowSince(firstDay(days))

    suspend fun deleteOlderThan(days: Int) = dao.deleteBefore(firstDay(days))

    private fun firstDay(days: Int) = clock.now().localDay() - (days - 1)
}

data class UsageKey(val permission: String, val declared: Boolean)

data class PermissionDataUsage(
    val permission: String,
    val declared: Boolean,
    val requests: Long,
    val sentBytes: Long,
    val receivedBytes: Long,
    val mobileBytes: Long,
) {
    val totalBytes: Long get() = sentBytes + receivedBytes

    /** [permission], with a domain the app didn't declare hashed: it may be one of the user's own. */
    val reportableKey: String
        get() {
            val domain = domainOf(permission)
            return if (declared || domain == null) permission else "${permissionOf(permission)}/#${domain.hashCode()}"
        }
}

data class AppDataUsageSummary(val appUuid: Uuid, val byPermission: List<PermissionDataUsage>) {
    val requests: Long get() = byPermission.sumOf { it.requests }
    val sentBytes: Long get() = byPermission.sumOf { it.sentBytes }
    val receivedBytes: Long get() = byPermission.sumOf { it.receivedBytes }
    val totalBytes: Long get() = byPermission.sumOf { it.totalBytes }
    val mobileBytes: Long get() = byPermission.sumOf { it.mobileBytes }
}

/** Per app and permission, the heaviest first. */
fun List<AppDataUsageEntity>.byApp(): List<AppDataUsageSummary> =
    groupBy { it.appUuid }
        .map { (appUuid, appRows) ->
            AppDataUsageSummary(
                appUuid,
                appRows.groupBy { it.permission }
                    .map { (permission, rows) ->
                        PermissionDataUsage(
                            permission = permission,
                            declared = rows.maxBy { it.day }.declared,
                            requests = rows.sumOf { it.requests },
                            sentBytes = rows.sumOf { it.sentBytes },
                            receivedBytes = rows.sumOf { it.receivedBytes },
                            mobileBytes = rows.filter { it.connection == Connections.CELLULAR }
                                .sumOf { it.sentBytes + it.receivedBytes },
                        )
                    }
                    .sortedWith(heaviestFirst({ it.totalBytes }, { it.requests })),
            )
        }
        .sortedWith(heaviestFirst({ it.totalBytes }, { it.requests }))

private fun <T> heaviestFirst(bytes: (T) -> Long, requests: (T) -> Long): Comparator<T> =
    compareByDescending(bytes).thenByDescending(requests)

private fun Instant.localDay(): Int = toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays().toInt()
