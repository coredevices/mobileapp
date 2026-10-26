package io.rebble.libpebblecommon.locker

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.readRemaining
import io.rebble.libpebblecommon.ErrorTracker
import io.rebble.libpebblecommon.WatchConfigFlow
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.LockerApi
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.connection.UserFacingError
import io.rebble.libpebblecommon.connection.WatchManager
import io.rebble.libpebblecommon.connection.WebServices
import io.rebble.libpebblecommon.connection.endpointmanager.blobdb.TimeProvider
import io.rebble.libpebblecommon.database.Database
import io.rebble.libpebblecommon.database.dao.LockerEntryRealDao
import io.rebble.libpebblecommon.database.entity.CompanionApp
import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.database.entity.LockerEntryAppstoreData
import io.rebble.libpebblecommon.database.entity.LockerEntryPlatform
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.disk.pbw.PbwApp
import io.rebble.libpebblecommon.disk.pbw.hasWatchappBuild
import io.rebble.libpebblecommon.disk.pbw.toLockerEntry
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.permissions.AppPermissionStatus
import io.rebble.libpebblecommon.permissions.AppPermissions
import io.rebble.libpebblecommon.plugin.files.PluginFileAccess
import io.rebble.libpebblecommon.storage.pluginSecureStorage
import io.rebble.libpebblecommon.permissions.InstallReview
import io.rebble.libpebblecommon.permissions.NetworkGate
import io.rebble.libpebblecommon.permissions.RequestedPermissions
import io.rebble.libpebblecommon.permissions.isLegacy
import io.rebble.libpebblecommon.permissions.requestedPermissions
import io.rebble.libpebblecommon.plugin.Connections
import io.rebble.libpebblecommon.plugin.PhoneNetworkMonitor
import io.rebble.libpebblecommon.plugin.PluginNetworkVerdict
import io.rebble.libpebblecommon.plugin.PluginManifest
import io.rebble.libpebblecommon.web.LockerModelWrapper
import io.rebble.libpebblecommon.web.WebSyncManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json

class WebSyncManagerProvider(val webSyncManager: () -> WebSyncManager)

internal data class AppVersionState(
    val id: Uuid,
    val currentVersion: String,
    val sideloaded: Boolean,
    val cachedVersions: List<String>,
)

/**
 * Apps to proactively re-download: the current version isn't cached, but a previous version is
 * (so the user had it before). Sideloaded apps are excluded — they can't be re-fetched.
 */
internal fun appsNeedingUpdatePrefetch(apps: List<AppVersionState>): List<Uuid> =
    apps.filter {
        !it.sideloaded && it.currentVersion !in it.cachedVersions && it.cachedVersions.isNotEmpty()
    }.map { it.id }

interface AppFileReader {
    suspend fun getAppFile(uuid: Uuid, name: String): String?
}

class Locker(
    private val watchManager: WatchManager,
    database: Database,
    private val lockerPBWCache: LockerPBWCache,
    private val config: WatchConfigFlow,
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val clock: Clock,
    private val webServices: WebServices,
    private val webSyncManagerProvider: WebSyncManagerProvider,
    private val timeProvider: TimeProvider,
    private val errorTracker: ErrorTracker,
    private val coroutineScope: LibPebbleCoroutineScope,
    private val settings: Settings,
    private val permissions: AppPermissions,
    private val appContext: AppContext,
    private val phoneNetworkMonitor: PhoneNetworkMonitor,
    private val pluginFileAccess: PluginFileAccess,
) : LockerApi, AppFileReader {
    private val lockerEntryDao = database.lockerEntryDao()
    private val timelinePinDao = database.timelinePinDao()
    private val timelineReminderDao = database.timelineReminderDao()
    private val appPermissionDao = database.lockerAppPermissionDao()

    companion object {
        private val logger = Logger.withTag("Locker")
        private val PREF_KEY_HAVE_INSERTED_SYSTEM_APPS_AT_CORRECT_POSITION = "have_inserted_system_apps_at_correct_position_v4"
        private const val PREF_KEY_HAVE_BACKFILLED_DECLARED_PERMISSIONS = "have_backfilled_declared_permissions"
    }

    override suspend fun sideloadApp(pbwPath: Path, loadOnWatch: Boolean, granted: Map<String, Boolean?>): Boolean =
        try {
            sideloadApp(pbwApp = PbwApp(pbwPath), loadOnWatch = loadOnWatch, granted = granted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e) { "Error while sideloading app" }
            errorTracker.reportError(UserFacingError.FailedToSideloadApp("Error while sideloading app"))
            false
        }

    /**
     * Get the locker contents, filtered by type/search query. Limit this, so that you don't run
     * out of memory if there is a particularly large locker.
     */
    override fun getLocker(
        type: AppType,
        searchQuery: String?,
        limit: Int
    ): Flow<List<LockerWrapper>> =
        // The Plugins tab shows every pbw that carries a plugin, watchapp-hosted ones included.
        (if (type == AppType.Plugin) lockerEntryDao.getPluginEntriesFlow(searchQuery, limit)
        else lockerEntryDao.getAllFlow(type.code, searchQuery, limit))
            .map { entries ->
                entries.mapNotNull { app ->
                    app.wrap(config)
                }
            }

    override fun getAllLockerBasicInfo(): Flow<List<AppBasicProperties>> {
        return lockerEntryDao.getAllBasicInfoFlow().map {
            it.mapNotNull { entry ->
                val appType = AppType.fromString(entry.type) ?: return@mapNotNull null
                AppBasicProperties(
                    id = entry.id,
                    title = entry.title,
                    type = appType,
                    developerName = entry.developerName,
                )
            }
        }
    }

    override fun getAllLockerUuids(): Flow<List<Uuid>> {
        return lockerEntryDao.getAllUuidsFlow()
    }

    override fun getLockerApp(id: Uuid): Flow<LockerWrapper?> {
        return lockerEntryDao.getEntryFlow(id).map { it?.wrap(config) }
    }

    override fun appPermissionStatuses(id: Uuid): Flow<List<AppPermissionStatus>> = permissions.statuses(id)

    private suspend fun pbwFor(uuid: Uuid): PbwApp? {
        val version = lockerEntryDao.getEntryFlow(uuid).first()?.version ?: return null
        return try {
            PbwApp(lockerPBWCache.getPBWFileForApp(uuid, version, this))
        } catch (e: Exception) {
            logger.w(e) { "no cached pbw for $uuid" }
            null
        }
    }

    override suspend fun appConfigPageUrl(uuid: Uuid): String? {
        // V2 config pages won't work with plugins disabled (no messaging channel)
        if (!config.value.enablePlugins) return null
        val entry = lockerEntryDao.getEntryFlow(uuid).first() ?: return null
        val page = entry.configPage ?: return null
        if (page.startsWith("http://") || page.startsWith("https://")) {
            // Like a PKJS config URL (`JsRunner.mayOpen`), opening it can carry data off the phone.
            val verdict = NetworkGate(permissions, entry.title, uuid).check(page, wait = true)
            if (verdict is PluginNetworkVerdict.Denied) {
                logger.w { "Not opening ${entry.title}'s config page: ${verdict.reason}" }
                return null
            }
            return page
        }
        // A bundled page still needs its HTML from the pbw, delivered as a data URL.
        return pbwFor(uuid)?.getTextFile(page)?.let { "data:text/html;charset=utf-8,${it.encodeURLPathPart()}" }
    }

    override suspend fun getAppFile(uuid: Uuid, name: String): String? = pbwFor(uuid)?.getTextFile(name)

    override suspend fun setAppOrder(id: Uuid, order: Int) {
        libPebbleCoroutineScope.async {
            lockerEntryDao.setOrder(id, order, config.value.lockerSyncLimitV2)
        }.await()
    }

    override suspend fun waitUntilAppSyncedToWatch(id: Uuid, identifier: PebbleIdentifier, timeout: Duration): Boolean {
        logger.v { "waitUntilAppSyncedToWatch $id" }
        try {
            withTimeout(timeout) {
                lockerEntryDao.dirtyRecordsForWatchInsert(
                    identifier = identifier.asString,
                    timestampMs = timeProvider.now().toEpochMilliseconds(),
                    insertOnlyAfterMs = timeProvider.now().toEpochMilliseconds(),
                ).filter { entries ->
                    // Wait until there is no match for this app in the dirty records
                    entries.find { entry ->
                        entry.record.id == id
                    } == null
                }.first()
            }
            logger.v { "waitUntilAppSyncedToWatch $id done!" }
            return true
        } catch (_: TimeoutCancellationException) {
            logger.w { "waitUntilAppSyncedToWatch: timed out" }
            return false
        }
    }

    override suspend fun removeApp(id: Uuid): Boolean = removeApp(id, keepSecrets = false)

    /** [keepSecrets] for an app that is about to be reinstalled. */
    suspend fun removeApp(id: Uuid, keepSecrets: Boolean): Boolean {
        val lockerEntry = lockerEntryDao.getEntry(id)
        if (lockerEntry == null) {
            logger.e { "removeApp: not found: $id" }
            return false
        }
        logger.d { "Deleting app: $lockerEntry" }
        lockerPBWCache.deleteApp(id)
        if (!lockerEntry.sideloaded) {
            // Need to remove from remote locker (and only process deletion if that succeeds)
            if (!webServices.removeFromLocker(id)) {
                logger.w { "Failed to remove from remote locker" }
                errorTracker.reportError(UserFacingError.FailedToRemovePbwFromLocker("Failed to remove app from remote locker"))
                return false
            }
        }
        lockerEntryDao.markForDeletion(id)
        timelinePinDao.markAllForDeletionByParentIdsWithReminders(listOf(id), timelineReminderDao)
        if (!keepSecrets) deletePluginData(listOf(id))
        if (lockerEntry.sideloaded) {
            logger.d { "Requesting locker sync after removing sideloaded app" }
            // If it was sideloaded, trigger a resync (in case the same app is in the locker).
            webSyncManagerProvider.webSyncManager().requestLockerSync()
        }
        return true
    }

    override suspend fun reviewSideload(pbwPath: Path): InstallReview? = try {
        val pbwApp = PbwApp(pbwPath)
        val info = pbwApp.info
        reviewInstall(Uuid.parse(info.uuid), info.longName.ifBlank { info.shortName }, pbwApp.requestedPermissions())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.w(e) { "Couldn't read pbw to review its permissions" }
        null
    }

    override suspend fun reviewInstall(appUuid: Uuid, appName: String, requested: RequestedPermissions): InstallReview? {
        val toReview = permissions.toReview(appUuid, requested)
        return if (toReview.isEmpty()) null else InstallReview(appUuid, appName, toReview)
    }

    internal suspend fun recordDeclaredPermissions(appId: Uuid, pbwPath: Path) {
        val entry = lockerEntryDao.getEntry(appId)
        val pbw = PbwApp(pbwPath)
        permissions.recordRequested(appId, pbw.info.requestedPermissions(pbw.hasPKJS, companionsOf = entry).all)
    }

    /** For apps installed before declarations were recorded, whose pbws won't be fetched again. */
    private suspend fun backfillDeclaredPermissions() {
        lockerEntryDao.getAll().filterNot { it.systemApp }.forEach { entry ->
            val pbw = lockerPBWCache.cachedPBWFileForApp(entry.id, entry.version) ?: return@forEach
            try {
                recordDeclaredPermissions(entry.id, pbw)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(e) { "Couldn't record what ${entry.id} declares" }
            }
        }
    }

    override suspend fun addAppToLocker(app: io.rebble.libpebblecommon.web.LockerEntry, granted: Map<String, Boolean?>) {
        val orderIndex = orderIndexForInsert(AppType.fromString(app.type) ?: AppType.Watchface)
        lockerEntryDao.insertOrReplaceAndOrder(app.asEntity(orderIndex), config.value.lockerSyncLimitV2)
        permissions.setDecisions(Uuid.parse(app.uuid), granted)
    }

    override suspend fun addAppsToLocker(apps: List<io.rebble.libpebblecommon.web.LockerEntry>) {
        lockerEntryDao.insertOrReplaceAndOrder(apps.map { app ->
            val orderIndex = orderIndexForInsert(AppType.fromString(app.type) ?: AppType.Watchface)
            app.asEntity(orderIndex)
        }, config.value.lockerSyncLimitV2)
    }

    suspend fun getApp(uuid: Uuid): LockerEntry? = lockerEntryDao.getEntry(uuid)

    suspend fun update(locker: LockerModelWrapper) {
        logger.d("update: ${locker.locker.applications.size}")
        val existingApps = lockerEntryDao.getAll().associateBy { it.id }.toMutableMap()
        val toInsert = locker.locker.applications.mapNotNull { new ->
            val newEntity = new.asEntity(orderIndexForInsert(AppType.fromString(new.type) ?: AppType.Watchface))
            // Preferred source for this UUID failed this sync; don't downgrade existing entry
            // to a fallback source's (potentially older) entry. Leave the DB row alone.
            if (newEntity.id in locker.failedToFetchUuids) return@mapNotNull null
            val existing = existingApps.remove(newEntity.id)
            if (existing == null) {
                newEntity
            } else {
                val newWithExistingLocalProps = newEntity.copy(
                    orderIndex = existing.orderIndex,
                    active = existing.active,
                    grantedPermissions = existing.grantedPermissions,
                    microphoneGranted = existing.microphoneGranted,
                )
                if (newWithExistingLocalProps != existing && !existing.sideloaded) {
                    newWithExistingLocalProps
                } else {
                    null
                }
            }
        }
        logger.d { "inserting: ${toInsert.map { "${it.id} / ${it.title}" }}" }
        lockerEntryDao.insertOrReplaceAndOrder(toInsert, config.value.lockerSyncLimitV2)
        logger.v { "Failed to fetch: ${locker.failedToFetchUuids}" }
        val toDelete = existingApps.mapNotNull {
            when {
                it.value.sideloaded -> null
                it.value.systemApp -> null
                // Don't delete from locker if we just failed to fetch updated version
                it.key in locker.failedToFetchUuids -> null
                else -> it.key
            }
        }
        logger.d { "deleting: $toDelete" }
        lockerEntryDao.markAllForDeletion(toDelete)
        timelinePinDao.markAllForDeletionByParentIdsWithReminders(toDelete, timelineReminderDao)
        deletePluginData(toDelete)
        performCacheCleanup()
    }

    private suspend fun deletePluginData(ids: List<Uuid>) = ids.forEach { id ->
        try {
            pluginSecureStorage(appContext, id).destroy()
            pluginFileAccess.destroy(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Couldn't delete secrets for $id" }
        }
    }

    /**
     * Sideload an app to the watch.
     * This will insert the app into the locker database and optionally install it/launch it on the watch.
     * @param pbwApp The app to sideload.
     * @param loadOnWatch Whether to fully install the app on the watch (launch it). Defaults to true.
     */
    suspend fun sideloadApp(pbwApp: PbwApp, loadOnWatch: Boolean, granted: Map<String, Boolean?> = emptyMap()): Boolean {
        logger.d { "Sideloading app ${pbwApp.info.longName}" }
        val type = when {
            pbwApp.info.loadablePlugin != null && !pbwApp.hasWatchappBuild() -> AppType.Plugin
            pbwApp.info.watchapp.watchface -> AppType.Watchface
            else -> AppType.Watchapp
        }
        val lockerEntry = pbwApp.toLockerEntry(clock.now(), orderIndexForInsert(type))
        lockerPBWCache.deleteApp(lockerEntry.id)  // Clear old version(s) if re-sideloading
        pbwApp.source().buffered().use {
            lockerPBWCache.addPBWFileForApp(lockerEntry.id, pbwApp.info.versionLabel, it)
        }
        val tasks = if (loadOnWatch) {
            watchManager.watches.value.filterIsInstance<ConnectedPebbleDevice>().map {
                libPebbleCoroutineScope.async {
                    val entryStatus = lockerEntryDao.existsOnWatch(
                        it.identifier.asString,
                        lockerEntry.id
                    ).drop(1).first()
                    if (entryStatus) {
                        logger.d { "App synced, launching" }
                        it.launchApp(lockerEntry.id)
                    }
                }
            }
        } else {
            null
        }
        lockerEntryDao.insertOrReplaceAndOrder(lockerEntry, config.value.lockerSyncLimitV2)
        // After the insert: requests are only shown for installed apps.
        permissions.recordRequested(lockerEntry.id, pbwApp.requestedPermissions().all)
        permissions.setDecisions(lockerEntry.id, granted)
        performCacheCleanup()
        return try {
            withTimeout(40.seconds) {
                tasks?.awaitAll()
                true
            }
        } catch (e: TimeoutCancellationException) {
            logger.w { "Timeout while waiting for app to sync+launch on watches" }
            false
        }
    }

    private suspend fun performCacheCleanup() {
        val entries = lockerEntryDao.getAllBasicInfo()
        lockerPBWCache.cleanupCache(entries)
    }

    /**
     * For apps whose current version isn't cached but a previous version is, download the current
     * version's pbw now so it can be pushed to the watch offline. Runs after a locker sync; a web
     * version bump otherwise only reveals itself as a failed fetch the next time the app is launched
     * without a connection.
     */
    suspend fun prefetchStaleAppUpdates() {
        if (!config.value.proactivelyDownloadAppUpdates) return
        if (config.value.appUpdateDownloadWifiOnly) {
            val connection = phoneNetworkMonitor.connection.first()
            if (connection != Connections.WIFI) {
                logger.d { "Skipping app-update prefetch, not on Wi-Fi (connection=$connection)" }
                return
            }
        }
        val cachedByUuid = lockerPBWCache.allCachedVersions()
        val statesById = lockerEntryDao.getAll().associate {
            it.id to AppVersionState(it.id, it.version, it.sideloaded, cachedByUuid[it.id.toString()].orEmpty())
        }
        val toPrefetch = appsNeedingUpdatePrefetch(statesById.values.toList())
        if (toPrefetch.isEmpty()) return
        logger.d { "Prefetching ${toPrefetch.size} updated app pbw(s)" }
        toPrefetch.forEach { id ->
            val version = statesById.getValue(id).currentVersion
            try {
                lockerPBWCache.getPBWFileForApp(id, version, this)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.d { "Prefetch failed for $id v$version: ${e.message}" }
            }
        }
        performCacheCleanup()
    }

    fun init(libPebble: LibPebble) {
        coroutineScope.launch {
            lockerPBWCache.init()
            val needToInsertAllSystemApps =
                !settings.getBoolean(PREF_KEY_HAVE_INSERTED_SYSTEM_APPS_AT_CORRECT_POSITION, false)
            settings[PREF_KEY_HAVE_INSERTED_SYSTEM_APPS_AT_CORRECT_POSITION] = true
            insertSystemApps(force = needToInsertAllSystemApps)
            // Only at startup, so that removing and reinstalling an app keeps its decisions.
            appPermissionDao.deleteForRemovedApps()
        }
        if (!settings.getBoolean(PREF_KEY_HAVE_BACKFILLED_DECLARED_PERMISSIONS, false)) {
            coroutineScope.launch {
                config.flow.first { it.watchConfig.enablePlugins }
                backfillDeclaredPermissions()
                settings[PREF_KEY_HAVE_BACKFILLED_DECLARED_PERMISSIONS] = true
            }
        }
        coroutineScope.launch {
            libPebble.watches.map { watches -> watches.filterIsInstance<ConnectedPebbleDevice>().firstOrNull() }
                .flatMapLatest { connectedWatch ->
                    connectedWatch?.runningApp ?: flowOf(null)
                }
                .distinctUntilChanged()
                .collect {
                    if (it != null) {
                        maybeSetActiveWatchface(it, onlyIfNotAlreadySet = false)
                    }
                }
        }
    }

    private suspend fun insertSystemApps(force: Boolean) {
        val lockerApps = getAllLockerBasicInfo()
            .first()
            .map { it.id }
        val systemAppsToInsert =
            SystemApps.entries.filter { force || !lockerApps.contains(it.uuid) }
                .map { systemApp ->
                    LockerEntry(
                        id = systemApp.uuid,
                        version = "",
                        title = systemApp.name,
                        type = systemApp.type.code,
                        developerName = "",
                        configurable = false,
                        "",
                        platforms = systemApp.compatiblePlatforms.map {
                            LockerEntryPlatform(
                                lockerEntryId = systemApp.uuid,
                                sdkVersion = "",
                                processInfoFlags = 0,
                                name = it.codename,
                                pbwIconResourceId = 0,
                            )
                        },
                        systemApp = true,
                        orderIndex = systemApp.defaultOrder,
                        capabilities = emptyList(),
                    )
                }

        if (systemAppsToInsert.isNotEmpty()) {
            lockerEntryDao.insertOrReplaceAndOrder(systemAppsToInsert, config.value.lockerSyncLimitV2)
        }
    }

    override fun restoreSystemAppOrder() {
        libPebbleCoroutineScope.launch {
            insertSystemApps(force = true)
        }
    }

    override val activeWatchface: StateFlow<LockerWrapper?> =
        lockerEntryDao.getActiveWatchface().map { it?.wrap(config) }.stateIn(libPebbleCoroutineScope, SharingStarted.Eagerly, null)

    fun maybeSetActiveWatchface(uuid: Uuid, onlyIfNotAlreadySet: Boolean) {
        libPebbleCoroutineScope.launch {
            val currentActive = activeWatchface.value
            if (currentActive != null && onlyIfNotAlreadySet) {
                return@launch
            }
            val entry = getApp(uuid)
            if (entry?.type == AppType.Watchface.code && currentActive?.properties?.id != uuid) {
                lockerEntryDao.setActive(uuid)
                if (config.value.orderWatchfacesByLastUsed) {
                    setAppOrder(uuid, 0)
                }
            }
        }
    }
}

fun SystemApps.wrap(order: Int): LockerWrapper.SystemApp = LockerWrapper.SystemApp(
    properties = AppProperties(
        id = uuid,
        type = type,
        title = displayName,
        developerName = "Pebble",
        platforms = compatiblePlatforms.map {
            AppPlatform(
                watchType = it,
                screenshotImageUrl = null,
                listImageUrl = null,
                iconImageUrl = null,
            )
        },
        version = null,
        hearts = null,
        category = null,
        iosCompanion = null,
        androidCompanion = null,
        order = order,
        developerId = null,
        storeId = null,
        sourceLink = null,
        capabilities = emptyList(),
    ),
    systemApp = this,
)

fun LockerEntry.wrap(config: WatchConfigFlow): LockerWrapper? {
    if (systemApp) {
        return findSystemApp(id)?.wrap(orderIndex)
    }
    val type = AppType.fromString(type) ?: return null
    return LockerWrapper.NormalApp(
        properties = AppProperties(
            id = id,
            type = type,
            title = title,
            developerName = developerName,
            platforms = platforms.mapNotNull platforms@{
                val platform = WatchType.fromCodename(it.name) ?: return@platforms null
                AppPlatform(
                    watchType = platform,
                    screenshotImageUrl = it.screenshotImageUrl,
                    listImageUrl = it.listImageUrl,
                    iconImageUrl = it.iconImageUrl,
                    description = it.description,
                )
            },
            version = version,
            hearts = appstoreData?.hearts,
            category = category,
            iosCompanion = iosCompanion,
            androidCompanion = androidCompanion,
            order = orderIndex,
            legacy = isLegacy(manifestVersion),
            developerId = appstoreData?.developerId,
            storeId = appstoreData?.storeId,
            sourceLink = appstoreData?.sourceLink,
            capabilities = AppCapability.fromString(capabilities),
            pbwUrl = appstoreData?.pbwLink,
        ),
        sideloaded = sideloaded,
        configurable = configurable,
        sync = orderIndex < config.value.lockerSyncLimitV2,
    )
}

fun findSystemApp(uuid: Uuid): SystemApps? = SystemApps.entries.find { it.uuid == uuid }

fun io.rebble.libpebblecommon.web.LockerEntry.asEntity(orderIndex: Int): LockerEntry {
    val uuid = Uuid.parse(uuid)
    return LockerEntry(
        id = uuid,
        version = version ?: "", // FIXME
        title = title,
        type = type,
        developerName = developer.name,
        configurable = isConfigurable,
        pbwVersionCode = pbw?.releaseId ?: "", // FIXME
        category = category,
        sideloaded = false,
        appstoreData = LockerEntryAppstoreData(
            hearts = hearts,
            developerId = developer.id,
            timelineEnabled = isTimelineEnabled ?: false,
            removeLink = links.remove,
            shareLink = links.share,
            pbwLink = pbw?.file ?: "", // FIXME
            userToken = userToken,
            sourceLink = source,
            storeId = id,
        ),
        platforms = hardwarePlatforms.map { platform ->
            LockerEntryPlatform(
                lockerEntryId = uuid,
                sdkVersion = platform.sdkVersion,
                processInfoFlags = platform.pebbleProcessInfoFlags,
                name = platform.name,
                screenshotImageUrl = platform.images.screenshot,
                listImageUrl = platform.images.list,
                iconImageUrl = platform.images.icon,
                pbwIconResourceId = pbw?.iconResourceId ?: 0,
                description = platform.description,
            )
        },
        iosCompanion = companions.ios?.let {
            CompanionApp(
                id = it.id,
                icon = it.icon,
                name = it.name,
                url = it.url,
                required = it.required,
                pebblekitVersion = it.pebblekitVersion,
            )
        },
        androidCompanion = companions.android?.let {
            CompanionApp(
                id = it.id,
                icon = it.icon,
                name = it.name,
                url = it.url,
                required = it.required,
                pebblekitVersion = it.pebblekitVersion,
            )
        },
        orderIndex = orderIndex,
        capabilities = buildList {
            capabilities?.let { addAll(it) }
            if (isTimelineEnabled == true) { add(AppCapability.Timeline.code) }
        },
        pluginManifest = loadablePluginManifest(),
        configPage = configPage,
        manifestVersion = manifestVersion,
    )
}

expect fun getLockerPBWCacheDirectory(context: AppContext): Path
expect fun getLockerPBWCacheLegacyDirectory(context: AppContext): Path?

class StaticLockerPBWCache(
    context: AppContext,
    private val httpClient: HttpClient,
    private val errorTracker: ErrorTracker,
) : LockerPBWCache(context) {
    private val logger = Logger.withTag("StaticLockerPBWCache")

    override suspend fun handleCacheMiss(appId: Uuid, version: String, locker: Locker): Path? {
        logger.d { "handleCacheMiss: $appId" }
        val pbwPath = pathForApp(appId, version)
        val pbwUrl = locker.getApp(appId)?.appstoreData?.pbwLink ?: return null
        return try {
            withTimeout(20.seconds) {
                logger.d { "get: $pbwUrl" }
                val response = try {
                    httpClient.get(pbwUrl)
                } catch (e: IOException) {
                    logger.w(e) { "Error fetching pbw: ${e.message}" }
                    errorTracker.reportError(UserFacingError.FailedToDownloadPbw("Error fetching pbw: ${e.message}"))
                    return@withTimeout null
                }
                if (!response.status.isSuccess()) {
                    logger.i("http call failed: $response")
                    errorTracker.reportError(UserFacingError.FailedToDownloadPbw("Error fetching pbw: ${response.status.description}"))
                    return@withTimeout null
                }
                SystemFileSystem.sink(pbwPath).use { sink ->
                    response.bodyAsChannel().readRemaining().transferTo(sink)
                }
                logger.d { "Successfully fetched pbw to: $pbwPath" }
                pbwPath
            }
        } catch (_: TimeoutCancellationException) {
            logger.w { "Timeout fetching pbw" }
            null
        }
    }
}

abstract class LockerPBWCache(private val context: AppContext) {
    private val cacheDir = getLockerPBWCacheDirectory(context)
    private val pkjsCacheDir = Path(cacheDir, "pkjs")

    fun init() {
        migrateFromLegacyIfNeeded(context)
    }

    companion object {
        const val DEFAULT_STORAGE_LIMIT_BYTES = 50L * 1024 * 1024 // 50 MB
    }

    private fun migrateFromLegacyIfNeeded(context: AppContext) {
        val legacyDir = getLockerPBWCacheLegacyDirectory(context) ?: return
        migrateFiles(from = legacyDir, to = cacheDir)
        migrateFiles(from = Path(legacyDir, "pkjs"), to = pkjsCacheDir)
        runCatching { SystemFileSystem.delete(legacyDir) }
    }

    private fun migrateFiles(from: Path, to: Path) {
        if (!SystemFileSystem.exists(from)) return
        SystemFileSystem.createDirectories(to, false)
        SystemFileSystem.list(from).forEach { file ->
            if (SystemFileSystem.metadataOrNull(file)?.isRegularFile == true) {
                val dest = Path(to, file.name)
                if (!SystemFileSystem.exists(dest)) {
                    moveFile(file, dest)
                }
                runCatching { SystemFileSystem.delete(file) }
            }
        }
        runCatching { SystemFileSystem.delete(from) }
    }

    private fun moveFile(src: Path, dest: Path) {
        try {
            SystemFileSystem.atomicMove(src, dest)
        } catch (_: Exception) {
            SystemFileSystem.sink(dest).buffered().use { sink ->
                SystemFileSystem.source(src).buffered().use { source ->
                    source.transferTo(sink)
                }
            }
            SystemFileSystem.delete(src)
        }
    }

    /**
     * Removes cached PBW files for store apps to stay within the storage limit.
     * Sideloaded apps are NEVER deleted (they can't be re-fetched).
     * [allEntries] should include all locker entries.
     * [storageLimitBytes] total size limit for the pbw cache directory.
     */
    fun cleanupCache(allEntries: List<LockerEntryRealDao.DbAppBasicProperties>, storageLimitBytes: Long = DEFAULT_STORAGE_LIMIT_BYTES) {
        if (!SystemFileSystem.exists(cacheDir)) return
        val allFiles = SystemFileSystem.list(cacheDir)
            .filter { SystemFileSystem.metadataOrNull(it)?.isRegularFile == true }
        val totalSize = allFiles.sumOf { SystemFileSystem.metadataOrNull(it)?.size ?: 0L }
        if (totalSize <= storageLimitBytes) return
        val deletable = allEntries
            .filter { !it.sideloaded }
            .sortedByDescending { it.orderIndex }
            .map { it.id.toString() }
        var bytesFreed = 0L
        val needed = totalSize - storageLimitBytes
        for (appIdStr in deletable) {
            if (bytesFreed >= needed) break
            allFiles.forEach { file ->
                if (file.name.startsWith(appIdStr)) {
                    bytesFreed += SystemFileSystem.metadataOrNull(file)?.size ?: 0L
                    SystemFileSystem.delete(file)
                    val pkjsFile = Path(pkjsCacheDir, "$appIdStr.js")
                    if (SystemFileSystem.exists(pkjsFile)) {
                        SystemFileSystem.delete(pkjsFile)
                    }
                }
            }
        }
    }

    protected fun pathForApp(appId: Uuid, version: String): Path {
        SystemFileSystem.createDirectories(cacheDir, false)
        return Path(cacheDir, "${appId}_${version}.pbw")
    }

    protected fun pkjsPathForApp(appId: Uuid): Path {
        SystemFileSystem.createDirectories(pkjsCacheDir, false)
        return Path(pkjsCacheDir, "$appId.js")
    }

    protected abstract suspend fun handleCacheMiss(appId: Uuid, version: String, locker: Locker): Path?

    suspend fun getPBWFileForApp(appId: Uuid, version: String, locker: Locker): Path {
        val pbwPath = pathForApp(appId, version)
        return if (SystemFileSystem.exists(pbwPath)) {
            pbwPath
        } else {
            // Delete any other cached versions for this app
            deleteApp(appId)
            val fetched = handleCacheMiss(appId, version, locker) ?: error("Failed to find PBW file for app $appId")
            try {
                locker.recordDeclaredPermissions(appId, fetched)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "Couldn't record what $appId declares" }
            }
            fetched
        }
    }

    fun cachedPBWFileForApp(appId: Uuid, version: String): Path? =
        pathForApp(appId, version).takeIf { SystemFileSystem.exists(it) }

    /** Every cached pbw version on disk, grouped by app-uuid string, parsed from `${uuid}_${version}.pbw`. */
    fun allCachedVersions(): Map<String, List<String>> {
        if (!SystemFileSystem.exists(cacheDir)) return emptyMap()
        return SystemFileSystem.list(cacheDir).mapNotNull {
            val name = it.name
            if (!name.endsWith(".pbw")) return@mapNotNull null
            // The uuid has no underscores, so the first one separates it from the version.
            val sep = name.indexOf('_')
            if (sep <= 0) return@mapNotNull null
            name.substring(0, sep) to name.substring(sep + 1).removeSuffix(".pbw")
        }.groupBy({ it.first }, { it.second })
    }

    fun addPBWFileForApp(appId: Uuid, version: String, source: Source) {
        val targetPath = pathForApp(appId, version)
        SystemFileSystem.sink(targetPath).use { sink ->
            source.transferTo(sink)
        }
    }

    private fun sanitizeJS(js: String): String {
        // Replace non-breaking spaces with regular spaces
        return js.replace("\u00a0", " ")
    }

    fun getPKJSFileForApp(appId: Uuid, version: String): Path {
        val pkjsPath = pkjsPathForApp(appId)
        val appPath = pathForApp(appId, version)
        return when {
            SystemFileSystem.exists(pkjsPath) -> pkjsPath
            SystemFileSystem.exists(appPath) -> {
                SystemFileSystem.createDirectories(pkjsCacheDir, false)
                val pbwApp = PbwApp(pathForApp(appId, version))
                pbwApp.getPKJSFile().use { source ->
                    val js = sanitizeJS(source.readString())
                    SystemFileSystem.sink(pkjsPath).buffered().use { sink ->
                        sink.writeString(js)
                    }
                }
                pkjsPath
            }

            else -> error("Failed to find PBW file for app $appId while extracting JS")
        }
    }

    fun clearPKJSFileForApp(appId: Uuid) {
        val pkjsPath = pkjsPathForApp(appId)
        if (SystemFileSystem.exists(pkjsPath)) {
            SystemFileSystem.delete(pkjsPath)
        }
    }

    fun deleteApp(appId: Uuid) {
        clearPKJSFileForApp(appId)
        if (SystemFileSystem.exists(cacheDir)) {
            SystemFileSystem.list(cacheDir).forEach {
                // Delete all pbws for this uuid
                if (it.name.startsWith(appId.toString())) {
                    SystemFileSystem.delete(it)
                }
            }
        }
    }
}

fun orderIndexForInsert(type: AppType) = when (type) {
    AppType.Watchface -> -1
    AppType.Watchapp -> SystemApps.entries.size
    AppType.Plugin -> 0
}

private val storePluginJson = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

/** As a pbw's `loadablePlugin`, re-encoded the same way; a manifest the app can't read is dropped. */
private fun io.rebble.libpebblecommon.web.LockerEntry.loadablePluginManifest(): String? {
    val raw = pluginManifest
    if (raw == null || isLegacy(manifestVersion)) return null
    val manifest = runCatching { storePluginJson.decodeFromJsonElement(PluginManifest.serializer(), raw) }
        .onFailure { Logger.w(it) { "unreadable plugin manifest for $uuid" } }
        .getOrNull() ?: return null
    return storePluginJson.encodeToString(PluginManifest.serializer(), manifest)
}
