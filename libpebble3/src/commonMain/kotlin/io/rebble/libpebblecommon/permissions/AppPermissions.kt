package io.rebble.libpebblecommon.permissions

import io.rebble.libpebblecommon.WatchConfig
import io.rebble.libpebblecommon.WatchConfigFlow
import io.rebble.libpebblecommon.database.dao.LockerAppPermissionDao
import io.rebble.libpebblecommon.database.dao.LockerEntryRealDao
import io.rebble.libpebblecommon.database.entity.LockerAppPermission
import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@Serializable
enum class PermissionPolicy {
    /** Asks for everything except reads by an app that can't send data off the phone. */
    AskIfExfiltrating,
    /** Asks for everything. */
    AlwaysAsk,
    /** Grants requests automatically. */
    NeverAsk,
}

data class PermissionSubject(
    val appUuid: Uuid,
    val legacy: Boolean,
    val hasAndroidCompanion: Boolean = false,
    val hasCompanionApp: Boolean = false,
)

enum class PermissionStatus {
    Granted,
    Denied,
    /** Undecided, and the policy allows it. */
    AllowedAutomatically,
    /** Undecided, and the policy needs the user to answer. */
    Waiting,
}

data class AppPermissionStatus(val permission: String, val status: PermissionStatus, val lastUsedAtMs: Long? = null)

enum class PermissionDecision {
    Granted,
    Denied,
    NeedsApproval,
}

interface InstalledApps {
    fun subjectsOf(appUuids: Set<Uuid>): Flow<Map<Uuid, PermissionSubject>>
    fun pluginPermissions(): Flow<Map<String, PluginDefinedPermission>>
    suspend fun syncMicrophoneGrants(grants: Set<Uuid>) {}
}

class LockerInstalledApps(private val lockerEntryDao: LockerEntryRealDao) : InstalledApps {
    override fun subjectsOf(appUuids: Set<Uuid>) = lockerEntryDao.getEntriesFlow(appUuids.toList())
        .map { entries -> entries.associate { it.id to it.permissionSubject() } }

    override fun pluginPermissions() = lockerEntryDao.getPluginEntriesFlow().map(::pluginDefinedPermissions)

    override suspend fun syncMicrophoneGrants(grants: Set<Uuid>) = lockerEntryDao.syncMicrophoneGrants(grants)
}

private fun LockerEntry.permissionSubject() = PermissionSubject(
    appUuid = id,
    legacy = isLegacy(manifestVersion),
    hasAndroidCompanion = androidCompanion != null,
    hasCompanionApp = androidCompanion != null || iosCompanion != null,
)

interface AppPermissionsApi {
    /** Undecided permissions the policy needs the user to answer, from apps running now (see [RunningApps]). */
    fun activePermissionRequests(): Flow<List<LockerAppPermission>>
    val appsWithPendingPermissionRequests: StateFlow<Set<Uuid>>
    suspend fun setPermissionDecisions(appUuid: Uuid, decisions: Map<String, Boolean>)
    /** Hides [permissions] from the locker's request cards while leaving them undecided. */
    suspend fun markPermissionRequestsSeen(appUuid: Uuid, permissions: Collection<String>)
    fun pluginPermissions(): Flow<Map<String, PluginDefinedPermission>>
}

/** Undecided rows are decided by the policy on every check, so policy changes apply immediately. */
class AppPermissions(
    private val dao: LockerAppPermissionDao,
    private val installed: InstalledApps,
    private val watchConfig: WatchConfigFlow,
    private val clock: Clock,
    private val runningApps: RunningApps,
    private val scope: LibPebbleCoroutineScope,
) : AppPermissionsApi {
    fun init() {
        localMicrophoneGrants().onEach { installed.syncMicrophoneGrants(it) }.launchIn(scope)
    }

    /** Controls the permission system. */
    val enforcing: Boolean get() = watchConfig.value.enablePlugins

    suspend fun subjectOf(appUuid: Uuid): PermissionSubject? = installed.subjectsOf(setOf(appUuid)).first()[appUuid]

    suspend fun check(subject: PermissionSubject, permission: String): PermissionDecision {
        val config = watchConfig.value
        val rows = dao.getByAppUuid(subject.appUuid)
        val decision = decidePermission(permission, subject, rows, config, pluginDefinitionsFor(rows, permission))
        if (config.enablePlugins && decision == PermissionDecision.Granted) {
            rows.coveringRow(permission)?.let { markUsed(it) }
        }
        return decision
    }

    /** As [check], but records the use. [wait] waits for the user's answer: never from a blocking callback. */
    suspend fun request(subject: PermissionSubject, permission: String, wait: Boolean = false): PermissionDecision {
        val config = watchConfig.value
        val rows = dao.getByAppUuid(subject.appUuid)
        val pluginDefined = pluginDefinitionsFor(rows, permission)
        val decision = decidePermission(permission, subject, rows, config, pluginDefined)
        if (config.enablePlugins) recordUse(subject.appUuid, rows, permission, decision)
        return if (wait && decision == PermissionDecision.NeedsApproval) {
            awaitAnswer(subject, permission, pluginDefined)
        } else {
            decision
        }
    }

    private suspend fun recordUse(
        appUuid: Uuid,
        rows: List<LockerAppPermission>,
        permission: String,
        decision: PermissionDecision,
    ) {
        if (decision == PermissionDecision.Denied) return
        val granted = decision == PermissionDecision.Granted
        val row = rows.coveringRow(permission)
        if (row == null) {
            val now = clock.now().toEpochMilliseconds()
            val newRow = LockerAppPermission(appUuid, permission, granted = null, requestedAtMs = now, lastUsedAtMs = if (granted) now else null)
            dao.insertIfAbsent(listOf(newRow))
        } else if (granted) {
            markUsed(row)
        }
    }

    private suspend fun awaitAnswer(
        subject: PermissionSubject,
        permission: String,
        pluginDefined: Map<String, PluginDefinedPermission>,
    ): PermissionDecision = withTimeoutOrNull(ANSWER_TIMEOUT) {
        dao.flowByAppUuid(subject.appUuid)
            .map { decidePermission(permission, subject, it, watchConfig.value, pluginDefined) }
            .first { it != PermissionDecision.NeedsApproval }
    } ?: PermissionDecision.NeedsApproval

    suspend fun request(appUuid: Uuid, permission: String, wait: Boolean = false): PermissionDecision =
        subjectOf(appUuid)?.let { request(it, permission, wait) } ?: PermissionDecision.Denied

    suspend fun check(appUuid: Uuid, permission: String): PermissionDecision =
        subjectOf(appUuid)?.let { check(it, permission) } ?: PermissionDecision.Denied

    /** What the app declared under [permission] (`Notifications/<package>`), else [permission] itself. */
    suspend fun callerKeys(appUuid: Uuid, permission: String): List<String> {
        if (permissionOf(permission) != permission) return listOf(permission)
        return dao.getByAppUuid(appUuid).map { it.permission }.filter { permissionOf(it) == permission }
            .ifEmpty { listOf(permission) }
    }

    suspend fun requestAll(
        subject: PermissionSubject,
        permissions: Collection<String>,
        wait: Boolean = false,
    ): Map<String, PermissionDecision> = coroutineScope {
        permissions.map { permission -> async { permission to request(subject, permission, wait) } }
            .awaitAll()
            .toMap()
            .filterValues { it != PermissionDecision.Granted }
    }

    fun grantedKeys(subject: PermissionSubject, permissions: Collection<String>): Flow<Set<String>> =
        combine(dao.flowByAppUuid(subject.appUuid), watchConfig.flow, installed.pluginPermissions()) { rows, config, defined ->
            permissions.filter {
                decidePermission(it, subject, rows, config.watchConfig, defined) == PermissionDecision.Granted
            }.toSet()
        }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun localMicrophoneGrants(): Flow<Set<Uuid>> =
        combine(dao.allFlow(), watchConfig.flow, installed.pluginPermissions()) { rows, config, defined ->
            Triple(rows, config.watchConfig, defined)
        }.flatMapLatest { (rows, config, defined) ->
            if (!config.enablePlugins) return@flatMapLatest flowOf(emptySet())
            val appRows = rows.groupBy { it.appUuid }
                .filterValues { permissions -> permissions.any { it.permission == PluginPermissions.WATCH_MICROPHONE } }
            installed.subjectsOf(appRows.keys).map { subjects ->
                subjects.filterKeys { it in appRows }.values.filter { subject ->
                    decidePermission(
                        PluginPermissions.WATCH_MICROPHONE, subject, appRows.getValue(subject.appUuid), config, defined,
                    ) == PermissionDecision.Granted
                }.map { it.appUuid }.toSet()
            }
        }

    /** A version that declares its permissions loses what a legacy version was given implicitly. */
    suspend fun recordRequested(appUuid: Uuid, requested: RequestedPermissions) {
        val all = requested.all
        if (requested.declared != null) {
            val undeclared = legacyRequested(hasPkjs = true, hasAndroidCompanion = true) - all.toSet()
            dao.deleteByAppUuidAndPermissions(appUuid, undeclared)
        }
        recordRequested(appUuid, all)
    }

    suspend fun recordRequested(appUuid: Uuid, permissions: Collection<String>) {
        val now = clock.now().toEpochMilliseconds()
        dao.insertIfAbsent(permissions.map { LockerAppPermission(appUuid, it, granted = null, requestedAtMs = now) })
    }

    /** Lists [permission] for the user to allow, without asking. */
    suspend fun recordDenied(appUuid: Uuid, permission: String) {
        val now = clock.now().toEpochMilliseconds()
        dao.insertIfAbsent(listOf(LockerAppPermission(appUuid, permission, granted = false, requestedAtMs = now, decidedAtMs = now)))
    }

    /** A null decision records that the user saw the permission without deciding. */
    suspend fun setDecisions(appUuid: Uuid, decisions: Map<String, Boolean?>) {
        val now = clock.now().toEpochMilliseconds()
        val existing = dao.getByAppUuid(appUuid).associateBy { it.permission }
        dao.insertOrReplaceAll(
            decisions.map { (permission, granted) ->
                LockerAppPermission(
                    appUuid = appUuid,
                    permission = permission,
                    granted = granted,
                    requestedAtMs = existing[permission]?.requestedAtMs ?: now,
                    decidedAtMs = granted?.let { now },
                    lastUsedAtMs = existing[permission]?.lastUsedAtMs,
                    seenAtMs = now,
                )
            }
        )
    }

    suspend fun toReview(appUuid: Uuid, requested: RequestedPermissions): List<String> {
        val existing = dao.getByAppUuid(appUuid)
        val rows = existing + (requested.all - existing.map { it.permission }.toSet())
            .map { LockerAppPermission(appUuid, it, granted = null, requestedAtMs = 0) }
        val subject = requested.subject(appUuid)
        val pluginDefined = pluginDefinitionsFor(rows, *requested.all.toTypedArray())
        return requested.all.filter {
            decidePermission(it, subject, rows, watchConfig.value, pluginDefined) == PermissionDecision.NeedsApproval &&
                !isUnneededInvokeAction(it, pluginDefined)
        }
    }

    /** Only asked of callers while the plugin can exfiltrate (see `ActionDispatcher`). */
    private suspend fun isUnneededInvokeAction(key: String, pluginDefined: Map<String, PluginDefinedPermission>): Boolean {
        val plugin = pluginDefined[key]?.pluginUuid ?: return false
        return key == invokeActionKey(plugin) && !canExfiltrate(plugin)
    }

    fun statuses(appUuid: Uuid): Flow<List<AppPermissionStatus>> = combine(
        dao.flowByAppUuid(appUuid),
        watchConfig.flow,
        installed.pluginPermissions(),
        installed.subjectsOf(setOf(appUuid)),
    ) { rows, config, defined, subjects ->
        permissionStatuses(subjects[appUuid], rows, config.watchConfig, defined)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val waiting: Flow<List<LockerAppPermission>> by lazy {
        combine(dao.allFlow(), watchConfig.flow, installed.pluginPermissions(), ::Triple)
            .flatMapLatest { (rows, config, defined) ->
                installed.subjectsOf(rows.map { it.appUuid }.toSet())
                    .map { subjects -> waitingRows(rows, subjects, config.watchConfig, defined) }
            }
            .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)
    }

    internal fun pendingPermissionRequests(): Flow<List<LockerAppPermission>> = waiting

    override fun activePermissionRequests(): Flow<List<LockerAppPermission>> =
        combine(waiting, runningApps.running) { pending, running -> pending.filter { it.appUuid in running } }

    override val appsWithPendingPermissionRequests: StateFlow<Set<Uuid>> by lazy {
        waiting
            .map { pending -> pending.map { it.appUuid }.toSet() }
            .stateIn(scope, SharingStarted.WhileSubscribed(), emptySet())
    }

    override suspend fun setPermissionDecisions(appUuid: Uuid, decisions: Map<String, Boolean>) =
        setDecisions(appUuid, decisions)

    override suspend fun markPermissionRequestsSeen(appUuid: Uuid, permissions: Collection<String>) =
        dao.markSeen(appUuid, permissions.toList(), clock.now().toEpochMilliseconds())

    override fun pluginPermissions(): Flow<Map<String, PluginDefinedPermission>> = installed.pluginPermissions()

    suspend fun canExfiltrate(appUuid: Uuid): Boolean {
        val subject = subjectOf(appUuid) ?: return false
        val config = watchConfig.value
        val rows = dao.getByAppUuid(appUuid).filterNot { config.disables(it.permission) }
        return isExfiltrating(subject, rows, pluginDefinitionsFor(rows))
    }

    private suspend fun pluginDefinitionsFor(
        rows: List<LockerAppPermission>,
        vararg keys: String,
    ): Map<String, PluginDefinedPermission> =
        if ((rows.map { it.permission } + keys).all { PermissionRegistry[permissionOf(it)] != null }) {
            emptyMap()
        } else {
            installed.pluginPermissions().first()
        }

    /** Throttled: called on every request. */
    private suspend fun markUsed(row: LockerAppPermission) {
        val now = clock.now().toEpochMilliseconds()
        if (row.lastUsedAtMs != null && now - row.lastUsedAtMs < USED_RESOLUTION.inWholeMilliseconds) return
        dao.markUsed(row.appUuid, row.permission, now)
    }

    companion object {
        val ANSWER_TIMEOUT = 10.seconds
        private val USED_RESOLUTION = 1.minutes
    }
}

fun refusalMessage(appName: String, refused: Map<String, PermissionDecision>): String {
    val (pending, denied) = refused.keys.sorted().partition { refused[it] == PermissionDecision.NeedsApproval }
    return listOfNotNull(
        denied.takeIf { it.isNotEmpty() }?.let { "$appName was not allowed ${it.joinToString()}" },
        pending.takeIf { it.isNotEmpty() }?.let { "$appName is waiting for approval of ${it.joinToString()}" },
    ).joinToString("; ")
}

internal fun permissionStatuses(
    subject: PermissionSubject?,
    rows: List<LockerAppPermission>,
    config: WatchConfig,
    pluginDefined: Map<String, PluginDefinedPermission>,
): List<AppPermissionStatus> = rows.filterNot { config.disables(it.permission) }.mapNotNull { row ->
    val status = when {
        rows.deniesBareFilePermissionOf(row.permission) -> PermissionStatus.Denied
        row.granted == true -> PermissionStatus.Granted
        row.granted == false -> PermissionStatus.Denied
        subject == null -> return@mapNotNull null
        else -> when (decidePermission(row.permission, subject, rows, config, pluginDefined)) {
            PermissionDecision.Granted -> PermissionStatus.AllowedAutomatically
            PermissionDecision.NeedsApproval -> PermissionStatus.Waiting
            PermissionDecision.Denied -> return@mapNotNull null
        }
    }
    AppPermissionStatus(row.permission, status, row.lastUsedAtMs)
}

internal fun waitingRows(
    rows: List<LockerAppPermission>,
    subjects: Map<Uuid, PermissionSubject>,
    config: WatchConfig,
    pluginDefined: Map<String, PluginDefinedPermission>,
): List<LockerAppPermission> = rows.groupBy { it.appUuid }
    .flatMap { (appUuid, appRows) ->
        val subject = subjects[appUuid] ?: return@flatMap emptyList()
        appRows.filter {
            it.granted == null &&
                decidePermission(it.permission, subject, appRows, config, pluginDefined) == PermissionDecision.NeedsApproval
        }
    }
    .sortedBy { it.requestedAtMs }

internal fun decidePermission(
    permission: String,
    subject: PermissionSubject,
    rows: List<LockerAppPermission>,
    config: WatchConfig,
    pluginDefined: Map<String, PluginDefinedPermission>,
): PermissionDecision {
    if (config.disables(permission)) return PermissionDecision.Denied
    if (!config.enablePlugins) return PermissionDecision.Granted
    val rows = rows.filterNot { config.disables(it.permission) }
    val decision = decideAsking(permission, subject, rows, config, pluginDefined)
    return if (decision == PermissionDecision.NeedsApproval && config.permissionPolicy == PermissionPolicy.NeverAsk) {
        PermissionDecision.Granted
    } else {
        decision
    }
}

private fun decideAsking(
    permission: String,
    subject: PermissionSubject,
    rows: List<LockerAppPermission>,
    config: WatchConfig,
    pluginDefined: Map<String, PluginDefinedPermission>,
): PermissionDecision {
    val kind = permissionKind(permission, pluginDefined) ?: return PermissionDecision.Denied
    if (rows.deniesBareFilePermissionOf(permission)) return PermissionDecision.Denied
    rows.decisionOf(permission)?.let { return if (it) PermissionDecision.Granted else PermissionDecision.Denied }
    // A file is only ever granted by the user picking it; no policy hands one out.
    if (fileScopeOf(permission) != null) return PermissionDecision.Denied
    // An app that declares its permissions gets domain-scoped networking, never global.
    if (permission == PluginPermissions.GLOBAL_INTERNET && !subject.legacy) return PermissionDecision.Denied
    if (subject.legacy && !config.askLegacyAppsForPermissions && isLegacyImplicit(permission, subject.hasAndroidCompanion)) {
        return PermissionDecision.Granted
    }
    if (kind != PermissionKind.Read) return PermissionDecision.NeedsApproval
    return when (config.permissionPolicy) {
        PermissionPolicy.AlwaysAsk, PermissionPolicy.NeverAsk -> PermissionDecision.NeedsApproval
        PermissionPolicy.AskIfExfiltrating ->
            if (isExfiltrating(subject, rows, pluginDefined)) PermissionDecision.NeedsApproval else PermissionDecision.Granted
    }
}

/** Has a companion app, or an exfiltrating permission that isn't denied. */
internal fun isExfiltrating(
    subject: PermissionSubject,
    rows: List<LockerAppPermission>,
    pluginDefined: Map<String, PluginDefinedPermission>,
): Boolean =
    subject.hasCompanionApp ||
        rows.any { permissionKind(it.permission, pluginDefined) == PermissionKind.Exfiltrating && it.granted != false }

/** While Legacy PebbleKit is switched off, it's never granted or listed. */
private fun WatchConfig.disables(permission: String) =
    permission == PluginPermissions.LEGACY_PEBBLEKIT && !enableLegacyPebbleKit

fun permissionKind(key: String, pluginDefined: Map<String, PluginDefinedPermission>): PermissionKind? =
    PermissionRegistry[permissionOf(key)]?.kind ?: pluginDefined[key]?.kind

private fun List<LockerAppPermission>.coveringRow(key: String): LockerAppPermission? =
    coveringKeys(key).firstNotNullOfOrNull { covering -> firstOrNull { it.permission == covering } }

/**
 * The key's own decision, else a covering one. Denying `WriteFiles` only denies a `ReadFiles` the
 * app never asked for on its own: a declared, undecided `ReadFiles` stays its own choice, so the
 * user can allow reading while refusing writing.
 */
private fun List<LockerAppPermission>.decisionOf(key: String): Boolean? {
    val decision = coveringKeys(key).firstNotNullOfOrNull { covering -> firstOrNull { it.permission == covering }?.granted }
    val ownChoice = permissionOf(key) == PluginPermissions.READ_FILES && any { it.permission == key && it.granted == null }
    return if (decision == false && ownChoice) null else decision
}

/** A picked entry is only as allowed as the bare `ReadFiles` / `WriteFiles` it was picked under. */
internal fun List<LockerAppPermission>.deniesBareFilePermissionOf(key: String): Boolean =
    fileScopeOf(key) != null && decisionOf(permissionOf(key)) == false
