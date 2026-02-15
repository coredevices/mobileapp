package io.rebble.libpebblecommon.connection.endpointmanager.blobdb

import co.touchlab.kermit.Logger
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.Settings
import com.russhwolf.settings.serialization.decodeValue
import com.russhwolf.settings.serialization.encodeValue
import coredev.BlobDatabase
import io.rebble.libpebblecommon.NotificationConfigFlow
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.database.dao.BlobDbDao
import io.rebble.libpebblecommon.database.dao.BlobDbRecord
import io.rebble.libpebblecommon.database.dao.LockerEntryRealDao
import io.rebble.libpebblecommon.database.dao.NotificationAppRealDao
import io.rebble.libpebblecommon.database.dao.TimelineNotificationRealDao
import io.rebble.libpebblecommon.database.dao.TimelinePinRealDao
import io.rebble.libpebblecommon.database.dao.TimelineReminderRealDao
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.database.dao.VibePatternDao
import io.rebble.libpebblecommon.database.dao.WatchPrefRealDao
import io.rebble.libpebblecommon.database.dao.WeatherAppRealDao
import io.rebble.libpebblecommon.database.entity.AppPrefsEntryDao
import io.rebble.libpebblecommon.database.entity.HealthSettingsEntryDao
import io.rebble.libpebblecommon.database.entity.HealthStatDao
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.di.PlatformConfig
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.packets.blobdb.BlobCommand
import io.rebble.libpebblecommon.packets.blobdb.BlobDB2Command
import io.rebble.libpebblecommon.packets.blobdb.BlobDB2Response
import io.rebble.libpebblecommon.packets.blobdb.BlobResponse
import io.rebble.libpebblecommon.services.blobdb.BlobDBService
import io.rebble.libpebblecommon.services.blobdb.WriteType
import io.rebble.libpebblecommon.web.withTimeoutOr
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class BlobDbDaos(
    private val lockerEntryDao: LockerEntryRealDao,
    private val notificationsDao: TimelineNotificationRealDao,
    private val timelinePinDao: TimelinePinRealDao,
    private val timelineReminderDao: TimelineReminderRealDao,
    private val notificationAppRealDao: NotificationAppRealDao,
    private val healthSettingsDao: HealthSettingsEntryDao,
    private val healthStatDao: HealthStatDao,
    private val vibePatternDao: VibePatternDao,
    private val platformConfig: PlatformConfig,
    private val watchPrefDao: WatchPrefRealDao,
    private val weatherAppDao: WeatherAppRealDao,
    private val appPrefsEntryDao: AppPrefsEntryDao,
) {
    fun get(): Set<BlobDbDao<BlobDbRecord>> = buildSet {
        add(lockerEntryDao)
        add(notificationsDao)
        add(timelinePinDao)
        add(timelineReminderDao)
        add(healthSettingsDao)
        add(healthStatDao)
        if (platformConfig.syncNotificationApps) {
            add(notificationAppRealDao)
        }
        add(watchPrefDao)
        add(weatherAppDao)
        add(appPrefsEntryDao)
        // because typing
    } as Set<BlobDbDao<BlobDbRecord>>
    
    fun getVibePatternDao(): VibePatternDao = vibePatternDao
}

interface TimeProvider {
    fun now(): Instant
}

class RealTimeProvider : TimeProvider {
    override fun now(): Instant = Clock.System.now()
}

@Serializable
data class DevicesWhichHaveSyncedSettings(val identifiers: Set<String>)

@OptIn(ExperimentalSettingsApi::class)
class BlobDB(
    private val watchScope: ConnectionCoroutineScope,
    private val blobDBService: BlobDBService,
    private val identifier: PebbleIdentifier,
    private val blobDatabases: BlobDbDaos,
    private val timeProvider: TimeProvider,
    private val notificationConfigFlow: NotificationConfigFlow,
    private val settings: Settings,
) {
    protected val watchIdentifier: String = identifier.asString

    companion object {
        private val BLOBDB_RESPONSE_TIMEOUT = 10.seconds
        private val QUERY_REFRESH_PERIOD = 1.hours
        private val PREF_KEY_DEVICES_HAVE_SYNCED_SETTINGS = "devicesHaveSyncedSettings"
    }

    private val logger = Logger.withTag("BlobDB-$watchIdentifier")
    private val random = Random
    // To prevent overlapping insert/delete operations
    private val operationLock = Mutex()
    private val databasesWhichWillSync = MutableStateFlow(setOf(BlobDatabase.WatchPrefs))

    /**
     * Run [query] continually, updating the query timestamp (so that it does not become stale).
     */
    private fun dynamicQuery(
        dao: BlobDbDao<BlobDbRecord>,
        insert: Boolean,
        collector: suspend (items: List<BlobDbRecord>) -> Unit,
    ) {
        val initialTimestamp = timeProvider.now()
        watchScope.launch {
            val tickerFlow = flow {
                while (true) {
                    emit(Unit)
                    delay(QUERY_REFRESH_PERIOD)
                }
            }
            tickerFlow
                .flatMapLatest {
                    logger.d { "dynamicQuery: refreshing (${dao.databaseId()}" }
                    if (insert) {
                        dao.dirtyRecordsForWatchInsert(
                            transport = identifier.asString,
                            timestampMs = timeProvider.now().toEpochMilliseconds(),
                            insertOnlyAfterMs = initialTimestamp.toEpochMilliseconds(),
                        )
                    } else {
                        dao.dirtyRecordsForWatchDelete(
                            transport = identifier.asString,
                            timestampMs = timeProvider.now().toEpochMilliseconds(),
                        )
                    }
                }
                .conflate()
                .distinctUntilChanged()
                .collect { items ->
                    collector(items)
                    // debounce
                    delay(1.seconds)
                }
        }
    }

    fun init(
        watchType: WatchType,
        unfaithful: Boolean,
        previouslyConnected: Boolean,
        capabilities: Set<ProtocolCapsFlag>,
    ) {
        val params = ValueParams(
            platform = watchType,
            capabilities = capabilities,
            vibePatternDao = blobDatabases.getVibePatternDao(),
        )
        val deviceHasPreviouslySyncedSettings =
            loadDevicePreviousSettingsSyncState().identifiers.contains(identifier.asString)
        watchScope.launch {
            // Watch isn't sending a version response
            val blobDbVersion = if (capabilities.contains(ProtocolCapsFlag.SupportsBlobDbVersion)) {
                val response = blobDBService.send(BlobDB2Command.Version(generateToken()))
                logger.v { "got version response: $response" }
                if (response is BlobDB2Response.VersionResponseCmd) {
                    response.version.get().toInt()
                } else {
                    0
                }
            } else {
                0
            }
            logger.d { "BlobDb version: $blobDbVersion" }

            if (unfaithful || !previouslyConnected) {
                // Mark all of our local DBs not synched (before handling any writes from the watch)
                blobDatabases.get().forEach { db ->
                    db.markAllDeletedFromWatch(identifier.asString)
                }
            }

            if (unfaithful || !deviceHasPreviouslySyncedSettings) {
                if (blobDbVersion >= 1) {
                    // Request a full sync of watch prefs
                    val dirtyResponse = blobDBService.send(
                        BlobDB2Command.MarkAllDirty(
                            generateToken(),
                            BlobDatabase.WatchPrefs,
                        )
                    )
                    logger.v { "Marked all dirty for watch prefs: $dirtyResponse" }
                }
            }

            watchScope.launch {
                blobDBService.writes.collect { message ->
                    logger.v { "write: $message" }
                    if (message.database == BlobDatabase.WatchPrefs && !deviceHasPreviouslySyncedSettings) {
                        markDeviceHasSyncedSettings()
                    }
                    val dao = blobDatabases.get().find { it.databaseId() == message.database }
                    val result = dao?.handleWrite(
                        write = message,
                        transport = identifier.asString,
                        params,
                    ) ?: run {
                        logger.v { "unhandled write: $message (key=${message.key.joinToString()} value=${message.value.joinToString()}" }
                        BlobResponse.BlobStatus.Success
                    }
                    val response = when (message.writeType) {
                        // TODO only one of these during initial sync?
                        WriteType.Write -> BlobDB2Response.WriteResponse(message.token, result)
                        WriteType.WriteBack -> BlobDB2Response.WriteBackResponse(
                            message.token,
                            result
                        )
                    }
                    blobDBService.sendResponse(response)
                }
            }

            if (blobDbVersion >= 1) {
                watchScope.launch {
                    blobDBService.syncCompletes.collect {
                        databasesWhichWillSync.value -= it
                    }
                }

                withTimeoutOr(timeout = 30.seconds, block = {
                    databasesWhichWillSync.first { it.isEmpty() }
                }, onTimeout = {
                    logger.w { "Timed out waiting for sync to complete" }
                })
            }

            if (unfaithful || !previouslyConnected) {
                logger.d("unfaithful: wiping DBs on watch")
                // Clear all DBs on watch (whether we have local DBs for them or not)
                BlobDatabase.entries.forEach { db ->
                    if (db.sendClear) {
                        sendWithTimeout(
                            BlobCommand.ClearCommand(
                                token = generateToken(),
                                database = db,
                            )
                        )
                    }
                }
            }

            blobDatabases.get().forEach { db ->
                db.deleteStaleRecords(timeProvider.now().toEpochMilliseconds())
                dynamicQuery(dao = db, insert = true) { dirty ->
                    dirty.forEach { item ->
                        operationLock.withLock {
                            handleInsert(db, item, params, blobDbVersion)
                        }
                    }
                }
                dynamicQuery(dao = db, insert = false) { dirty ->
                    dirty.forEach { item ->
                        operationLock.withLock {
                            handleDelete(db, item)
                        }
                    }
                }
            }
        }
    }

    private fun loadDevicePreviousSettingsSyncState() =
        settings.decodeValue(PREF_KEY_DEVICES_HAVE_SYNCED_SETTINGS, DevicesWhichHaveSyncedSettings(emptySet()))

    private fun markDeviceHasSyncedSettings() {
        logger.d { "markDeviceHasSyncedSettings: ${identifier.asString}" }
        val devices = loadDevicePreviousSettingsSyncState()
        settings.encodeValue(
            PREF_KEY_DEVICES_HAVE_SYNCED_SETTINGS,
            devices.copy(identifiers = devices.identifiers + identifier.asString),
        )
    }

    private suspend fun handleInsert(
        db: BlobDbDao<BlobDbRecord>,
        item: BlobDbRecord,
        params: ValueParams,
        blobDbVersion: Int,
    ) {
        val value = item.record.value(params)
        val key = item.record.key()
        val keyString = key.keyAsString(db.databaseId())
        if (value == null) {
            logger.d { "handleInsert: value is null: ${db.databaseId()} $keyString" }
            return
        }
        if (notificationConfigFlow.value.obfuscateContent) {
            logger.d("insert: ${db.databaseId()} $key ($keyString) hashcode: ${item.recordHashcode}")
        } else {
            logger.d("insert: ${db.databaseId()} $keyString - $item")
        }
        val timestamp = item.record.timestamp()
        val command = when {
            timestamp == null -> BlobCommand.InsertCommand(
                token = generateToken(),
                database = db.databaseId(),
                key = key,
                value = value,
            )
            blobDbVersion >= 1 -> {
                logger.v { "inserting with timestamp: $timestamp" }
                BlobCommand.InsertWithTimestampCommand(
                    token = generateToken(),
                    database = db.databaseId(),
                    key = key,
                    value = value,
                    timestamp = timestamp,
                )
            }
            else -> null
        }
        if (command == null) {
            logger.i { "Not processing insert for ${db.databaseId()}: $keyString because not supported by watch" }
            return
        }
        val bytes = command.serialize().asByteArray()
        val result = sendWithTimeout(command)
        logger.d("insert: result = ${result?.responseValue}")
        when (result?.responseValue) {
            BlobResponse.BlobStatus.Success,
            // Stale = mark as synced (watch will never accept this record)
            BlobResponse.BlobStatus.DataStale-> db.markSyncedToWatch(
                transport = identifier.asString,
                item = item,
                hashcode = item.recordHashcode,
            )

            else -> Unit
        }
    }

    private suspend fun handleDelete(
        db: BlobDbDao<BlobDbRecord>,
        item: BlobDbRecord,
    ) {
        val key = item.record.key()
        val keyString = key.keyAsString(db.databaseId())
        if (notificationConfigFlow.value.obfuscateContent) {
            logger.d("delete: ${db.databaseId()} $key ($keyString) hashcode: ${item.recordHashcode}")
        } else {
            logger.d("delete: ${db.databaseId()} $keyString - $item")
        }
        val result = sendWithTimeout(
            BlobCommand.DeleteCommand(
                token = generateToken(),
                database = db.databaseId(),
                key = key,
            )
        )
        logger.d("delete: result = ${result?.responseValue}")
        if (result?.responseValue == BlobResponse.BlobStatus.Success) {
            db.markDeletedFromWatch(
                transport = identifier.asString,
                item = item,
                hashcode = item.recordHashcode,
            )
        }
    }

    private fun generateToken(): UShort {
        return random.nextInt(0, UShort.MAX_VALUE.toInt()).toUShort()
    }

    private suspend fun sendWithTimeout(command: BlobCommand): BlobResponse? =
        withTimeoutOrNull(BLOBDB_RESPONSE_TIMEOUT) {
            blobDBService.send(command)
        }
}

private fun UByteArray.keyAsString(db: BlobDatabase): String = when {
    db.keyIsUuid() -> try {
        Uuid.fromUByteArray(this).toString()
    } catch (_: Exception) {
        ""
    }
    else -> ""
}

private fun BlobDatabase.keyIsUuid(): Boolean = when (this) {
    BlobDatabase.Pin, BlobDatabase.App, BlobDatabase.Reminder, BlobDatabase.Notification -> true
    else -> false
}