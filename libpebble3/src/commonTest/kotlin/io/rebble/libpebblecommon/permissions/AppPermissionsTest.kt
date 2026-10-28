package io.rebble.libpebblecommon.permissions

import io.rebble.libpebblecommon.LibPebbleConfig
import io.rebble.libpebblecommon.WatchConfig
import io.rebble.libpebblecommon.WatchConfigFlow
import io.rebble.libpebblecommon.asFlow
import io.rebble.libpebblecommon.connection.PebbleConnectionEvent
import io.rebble.libpebblecommon.connection.PebbleDevice
import io.rebble.libpebblecommon.connection.Watches
import io.rebble.libpebblecommon.database.dao.LockerAppPermissionDao
import io.rebble.libpebblecommon.database.entity.LockerAppPermission
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import io.rebble.libpebblecommon.permissions.PermissionDecision.Denied
import io.rebble.libpebblecommon.permissions.PermissionDecision.Granted
import io.rebble.libpebblecommon.permissions.PermissionDecision.NeedsApproval
import io.rebble.libpebblecommon.plugin.PluginNetworkVerdict
import io.rebble.libpebblecommon.plugin.PluginPermission
import io.rebble.libpebblecommon.plugin.PluginPermissions.CALENDAR
import io.rebble.libpebblecommon.plugin.PluginPermissions.GLOBAL_INTERNET
import io.rebble.libpebblecommon.plugin.PluginPermissions.INTERNET
import io.rebble.libpebblecommon.plugin.PluginPermissions.LOCAL_NETWORK
import io.rebble.libpebblecommon.plugin.PluginPermissions.LOCATION
import io.rebble.libpebblecommon.plugin.PluginPermissions.WATCH_MICROPHONE
import io.rebble.libpebblecommon.plugin.PluginPermissions.LEGACY_PEBBLEKIT
import io.rebble.libpebblecommon.plugin.PluginPermissions.NOTIFICATIONS
import io.rebble.libpebblecommon.plugin.PluginPermissions.PARAM_PACKAGES
import io.rebble.libpebblecommon.plugin.PluginPermissions.READ_FILES
import io.rebble.libpebblecommon.plugin.PluginPermissions.READ_WATCH_SETTINGS
import io.rebble.libpebblecommon.plugin.PluginPermissions.TIMELINE
import io.rebble.libpebblecommon.plugin.PluginPermissions.WRITE_FILES
import io.rebble.libpebblecommon.plugin.PluginPermissions.WRITE_WATCH_SETTINGS
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal val APP = Uuid.parse("00000000-0000-0000-0000-00000000a001")

internal fun subject(
    legacy: Boolean = false,
    hasAndroidCompanion: Boolean = false,
    hasCompanionApp: Boolean = false,
) = PermissionSubject(APP, legacy, hasAndroidCompanion, hasCompanionApp)

internal fun installedAs(
    subject: PermissionSubject?,
    plugins: Flow<Map<String, PluginDefinedPermission>> = flowOf(emptyMap()),
) = object : InstalledApps {
    override fun subjectsOf(appUuids: Set<Uuid>) = flowOf(listOfNotNull(subject).associateBy { it.appUuid })
    override fun pluginPermissions() = plugins
}

private fun row(permission: String, granted: Boolean? = null) =
    LockerAppPermission(APP, permission, granted, requestedAtMs = 1, decidedAtMs = granted?.let { 1 })

private fun decide(
    permission: String,
    subject: PermissionSubject = subject(),
    rows: List<LockerAppPermission> = emptyList(),
    config: WatchConfig = WatchConfig(enablePlugins = true),
    pluginDefined: Map<String, PermissionKind> = emptyMap(),
) = decidePermission(
    permission,
    subject,
    rows,
    config,
    pluginDefined.mapValues { (key, kind) -> PluginDefinedPermission(key, APP, "Plugin", key, kind) },
)

class DecidePermissionTest {
    @Test
    fun microphoneFollowsTheReadPermissionPolicy() {
        assertEquals(Granted, decide(WATCH_MICROPHONE))
        assertEquals(NeedsApproval, decide(WATCH_MICROPHONE, rows = listOf(row(INTERNET))))
        assertEquals(NeedsApproval, decide(WATCH_MICROPHONE, subject(hasCompanionApp = true)))
        assertEquals(NeedsApproval, decide(WATCH_MICROPHONE, config = WatchConfig(enablePlugins = true, permissionPolicy = PermissionPolicy.AlwaysAsk)))
    }

    @Test
    fun legacyMicrophoneAccessIsNotImplicit() {
        assertEquals(NeedsApproval, decide(WATCH_MICROPHONE, subject(legacy = true), listOf(row(GLOBAL_INTERNET))))
    }

    @Test
    fun microphoneRespectsExplicitAnswersEvenUnderNeverAsk() {
        assertEquals(Granted, decide(WATCH_MICROPHONE, rows = listOf(row(INTERNET), row(WATCH_MICROPHONE, true))))
        assertEquals(Denied, decide(WATCH_MICROPHONE, rows = listOf(row(WATCH_MICROPHONE, false))))
        val config = WatchConfig(enablePlugins = true, permissionPolicy = PermissionPolicy.NeverAsk)
        assertEquals(Granted, decide(WATCH_MICROPHONE, rows = listOf(row(INTERNET)), config = config))
        assertEquals(Denied, decide(WATCH_MICROPHONE, rows = listOf(row(WATCH_MICROPHONE, false)), config = config))
    }

    @Test
    fun theUsersDecisionWins() {
        assertEquals(Granted, decide(CALENDAR, subject(hasCompanionApp = true), listOf(row(CALENDAR, true))))
        assertEquals(Denied, decide(CALENDAR, rows = listOf(row(CALENDAR, false))))
    }

    @Test
    fun unknownPermissionIsDenied() {
        assertEquals(Denied, decide("Teleport"))
    }

    @Test
    fun aFileIsOnlyGrantedByBeingPicked() {
        val key = readFilesKey("content://docs/tree/notes")
        assertEquals(Denied, decide(key))
        assertEquals(Denied, decide(key, config = WatchConfig(enablePlugins = true, permissionPolicy = PermissionPolicy.NeverAsk)))
        assertEquals(Denied, decide(key, rows = listOf(row(READ_FILES, true))))
        assertEquals(Granted, decide(key, rows = listOf(row(key, true))))
        assertEquals(Denied, decide(writeFilesKey("content://docs/tree/notes"), rows = listOf(row(key, true))))
        assertEquals(NeedsApproval, decide(WRITE_FILES))
    }

    @Test
    fun aWriteFilesDecisionCoversReadFiles() {
        val scope = "content://docs/tree/notes"
        assertEquals(Granted, decide(READ_FILES, rows = listOf(row(WRITE_FILES, true))))
        assertEquals(Denied, decide(READ_FILES, rows = listOf(row(WRITE_FILES, false))))
        assertEquals(Denied, decide(READ_FILES, rows = listOf(row(READ_FILES, false), row(WRITE_FILES, true))))
        assertEquals(Granted, decide(readFilesKey(scope), rows = listOf(row(writeFilesKey(scope), true))))
        assertEquals(Denied, decide(readFilesKey(scope), rows = listOf(row(writeFilesKey(scope), false))))
        assertEquals(Denied, decide(readFilesKey(scope), rows = listOf(row(WRITE_FILES, true))))
    }

    @Test
    fun denyingWriteFilesLeavesADeclaredReadFilesItsOwnChoice() {
        val scope = "content://docs/tree/notes"
        val declaredBoth = listOf(row(READ_FILES), row(WRITE_FILES, false))
        assertEquals(Granted, decide(READ_FILES, rows = declaredBoth))
        val alwaysAsk = WatchConfig(enablePlugins = true, permissionPolicy = PermissionPolicy.AlwaysAsk)
        assertEquals(NeedsApproval, decide(READ_FILES, rows = declaredBoth, config = alwaysAsk))
        // So its picks stay, and a read-write pick drops to read-only rather than off.
        assertEquals(Granted, decide(readFilesKey(scope), rows = declaredBoth + row(readFilesKey(scope), true)))
        assertEquals(Granted, decide(readFilesKey(scope), rows = declaredBoth + row(writeFilesKey(scope), true)))
        assertEquals(Denied, decide(writeFilesKey(scope), rows = declaredBoth + row(writeFilesKey(scope), true)))
        // Read's own decision still wins either way.
        assertEquals(Denied, decide(READ_FILES, rows = listOf(row(READ_FILES, false), row(WRITE_FILES, false))))
        assertEquals(Granted, decide(READ_FILES, rows = listOf(row(READ_FILES, true), row(WRITE_FILES, false))))
    }

    @Test
    fun denyingTheBareFilePermissionTurnsOffEveryEntryPickedUnderIt() {
        val scope = "content://docs/tree/notes"
        assertEquals(Denied, decide(readFilesKey(scope), rows = listOf(row(READ_FILES, false), row(readFilesKey(scope), true))))
        assertEquals(Denied, decide(writeFilesKey(scope), rows = listOf(row(WRITE_FILES, false), row(writeFilesKey(scope), true))))
        // Without a ReadFiles decision of its own, denying WriteFiles denies reading too.
        assertEquals(Denied, decide(readFilesKey(scope), rows = listOf(row(WRITE_FILES, false), row(writeFilesKey(scope), true))))
        // Denying only writing leaves a read-write pick readable.
        val readOnly = listOf(row(READ_FILES, true), row(WRITE_FILES, false), row(writeFilesKey(scope), true))
        assertEquals(Granted, decide(readFilesKey(scope), rows = readOnly))
        assertEquals(Denied, decide(writeFilesKey(scope), rows = readOnly))
        // Allowing it again brings the pick back.
        assertEquals(Granted, decide(readFilesKey(scope), rows = listOf(row(READ_FILES, true), row(readFilesKey(scope), true))))
    }

    @Test
    fun appThatCannotSendDataAnywhereReadsWithoutAsking() {
        assertEquals(Granted, decide(CALENDAR, rows = listOf(row(LOCATION), row(CALENDAR))))
    }

    @Test
    fun alwaysAskPolicyAsksEvenWithoutExfiltration() {
        assertEquals(NeedsApproval, decide(CALENDAR, config = WatchConfig(enablePlugins = true, permissionPolicy = PermissionPolicy.AlwaysAsk)))
    }

    @Test
    fun neverAskPolicyGrantsWhatWouldBeAskedButKeepsDenials() {
        val config = WatchConfig(enablePlugins = true, permissionPolicy = PermissionPolicy.NeverAsk)
        assertEquals(Granted, decide(CALENDAR, subject(hasCompanionApp = true), config = config))
        assertEquals(Granted, decide(INTERNET, rows = listOf(row(INTERNET)), config = config))
        assertEquals(Denied, decide(CALENDAR, rows = listOf(row(CALENDAR, false)), config = config))
        assertEquals(Denied, decide(GLOBAL_INTERNET, config = config))
    }

    @Test
    fun exfiltratingPermissionIsAlwaysAsked() {
        assertEquals(NeedsApproval, decide(INTERNET, rows = listOf(row(INTERNET))))
    }

    @Test
    fun anUndecidedNetworkPermissionMakesTheAppExfiltratingUntilDenied() {
        assertEquals(NeedsApproval, decide(CALENDAR, rows = listOf(row(INTERNET))))
        assertEquals(NeedsApproval, decide(CALENDAR, rows = listOf(row(INTERNET, true))))
        assertEquals(Granted, decide(CALENDAR, rows = listOf(row(INTERNET, false))))
    }

    @Test
    fun companionAppIsAlwaysExfiltrating() {
        assertEquals(NeedsApproval, decide(CALENDAR, subject(hasCompanionApp = true)))
    }

    @Test
    fun legacyAppWithPkjsIsExfiltratingUnlessGlobalInternetDenied() {
        val legacy = subject(legacy = true)
        assertEquals(NeedsApproval, decide(CALENDAR, legacy, listOf(row(GLOBAL_INTERNET))))
        assertEquals(Granted, decide(CALENDAR, legacy, listOf(row(GLOBAL_INTERNET, false))))
    }

    @Test
    fun legacyAppWithoutPkjsCannotSendDataAnywhere() {
        assertEquals(Granted, decide(CALENDAR, subject(legacy = true)))
    }

    @Test
    fun globalInternetIsImplicitForLegacyAppsUnlessTheUserWantsToBeAsked() {
        val legacy = subject(legacy = true)
        assertEquals(Granted, decide(GLOBAL_INTERNET, legacy))
        assertEquals(NeedsApproval, decide(GLOBAL_INTERNET, legacy, config = WatchConfig(enablePlugins = true, askLegacyAppsForPermissions = true)))
    }

    @Test
    fun legacyLocationIsImplicitUnlessTheUserWantsToBeAsked() {
        val legacy = subject(legacy = true)
        val rows = listOf(row(GLOBAL_INTERNET), row(LOCATION))
        assertEquals(Granted, decide(LOCATION, legacy, rows))
        assertEquals(NeedsApproval, decide(LOCATION, legacy, rows, WatchConfig(enablePlugins = true, askLegacyAppsForPermissions = true)))
    }

    @Test
    fun legacyAppUsingAnUndeclaredCapabilityFollowsThePolicy() {
        assertEquals(NeedsApproval, decide(CALENDAR, subject(legacy = true), listOf(row(GLOBAL_INTERNET), row(CALENDAR))))
    }

    @Test
    fun globalInternetIsNeverForAppsThatDeclarePermissions() {
        assertEquals(Denied, decide(GLOBAL_INTERNET))
    }

    @Test
    fun withPluginsDisabledEverythingIsAllowedEvenWhatTheUserDenied() {
        val config = WatchConfig(enablePlugins = false)
        assertEquals(Granted, decide(CALENDAR, subject(legacy = true), listOf(row(GLOBAL_INTERNET)), config))
        assertEquals(Granted, decide(INTERNET, config = config))
        assertEquals(Granted, decide(LOCATION, rows = listOf(row(LOCATION, false)), config = config))
    }

    @Test
    fun theMostSpecificDomainDecisionWins() {
        val api = internetKey("api.example.com")
        assertEquals(Granted, decide(api, rows = listOf(row(internetKey("example.com"), true))))
        assertEquals(Granted, decide(api, rows = listOf(row(INTERNET, true))))
        assertEquals(Denied, decide(api, rows = listOf(row(INTERNET, true), row(api, false))))
        assertEquals(Denied, decide(api, rows = listOf(row(api), row(internetKey("example.com"), false))))
        assertEquals(NeedsApproval, decide(api, rows = listOf(row(internetKey("pi.example.com"), true))))
    }
}

class PebbleKitTest {
    private val companion = pebbleKit2Key("com.example.companion")

    @Test
    fun eachPebbleKit2PackageIsAskedForOnItsOwn() {
        val requested = RequestedPermissions(declared = emptyList(), hasPkjs = false, pebbleKit2Packages = listOf("com.example.companion"))
        assertEquals(listOf(companion), requested.all)
        assertEquals(null, domainOf(companion))
        assertEquals("com.example.companion", packageOf(companion))
    }

    @Test
    fun aLegacyAppKeepsItsPebbleKit2CompanionButADeclaringAppIsAsked() {
        assertEquals(Granted, decide(companion, subject(legacy = true)))
        assertEquals(NeedsApproval, decide(companion))
    }

    @Test
    fun aPebbleKit2CompanionMakesTheAppExfiltratingUntilDenied() {
        assertEquals(NeedsApproval, decide(CALENDAR, rows = listOf(row(companion))))
        assertEquals(Granted, decide(CALENDAR, rows = listOf(row(companion, false))))
    }

    @Test
    fun legacyPebbleKitSwitchedOffIsDeniedHiddenAndNoWayOut() {
        val off = WatchConfig(enablePlugins = true, enableLegacyPebbleKit = false)
        val rows = listOf(row(LEGACY_PEBBLEKIT, true))
        assertEquals(Denied, decide(LEGACY_PEBBLEKIT, rows = rows, config = off))
        assertEquals(Granted, decide(CALENDAR, rows = rows, config = off))
        assertEquals(emptyList(), permissionStatuses(subject(), rows, off, emptyMap()))
    }
}

class LegacyTimelineTest {
    @Test
    fun everyLegacyAppKeepsLocationWhateverItsCapabilitiesSay() {
        val exfiltrating = listOf(row(GLOBAL_INTERNET))
        assertEquals(Granted, decide(LOCATION, subject(legacy = true), exfiltrating))
        assertEquals(NeedsApproval, decide(LOCATION, subject(), exfiltrating))
    }

    @Test
    fun everyLegacyAppKeepsTheTimelineButADeclaringAppIsAsked() {
        assertEquals(Granted, decide(TIMELINE, subject(legacy = true)))
        assertEquals(NeedsApproval, decide(TIMELINE, subject(legacy = true), config = WatchConfig(enablePlugins = true, askLegacyAppsForPermissions = true)))
        assertEquals(NeedsApproval, decide(TIMELINE))
    }
}

class PluginDefinedPermissionDecisionTest {
    private val hue = Uuid.parse("00000000-0000-0000-0002-000000000001")
    private val homeControl = pluginPermissionKey(hue, "HomeControl")

    @Test
    fun aPermissionNoInstalledPluginDefinesIsDenied() {
        assertEquals(Denied, decide(homeControl))
        assertEquals(Denied, decide(homeControl, rows = listOf(row(homeControl, true))))
    }

    @Test
    fun theUserAlwaysDecidesAWriteAPluginDefined() {
        val defined = mapOf(homeControl to PermissionKind.Write)
        assertEquals(NeedsApproval, decide(homeControl, pluginDefined = defined))
        assertEquals(Granted, decide(homeControl, rows = listOf(row(homeControl, true)), pluginDefined = defined))
        assertEquals(Denied, decide(homeControl, rows = listOf(row(homeControl, false)), pluginDefined = defined))
        assertEquals(
            Granted,
            decide(homeControl, config = WatchConfig(enablePlugins = true, permissionPolicy = PermissionPolicy.NeverAsk), pluginDefined = defined),
        )
    }
}

class PermissionKindTest {
    private val plugin = Uuid.parse("00000000-0000-0000-0002-000000000001")

    @Test
    fun aWriteAsksEvenAnAppThatCantSendAnythingAnywhere() {
        assertEquals(Granted, decide(READ_WATCH_SETTINGS))
        assertEquals(NeedsApproval, decide(WRITE_WATCH_SETTINGS))
    }

    @Test
    fun aPluginDefinedReadIsAllowedLikeABuiltInOne() {
        val notes = pluginPermissionKey(plugin, "ReadNotes")
        assertEquals(Granted, decide(notes, pluginDefined = mapOf(notes to PermissionKind.Read)))
    }

    @Test
    fun holdingAPluginDefinedExfiltratingPermissionMakesTheAppExfiltrating() {
        val post = pluginPermissionKey(plugin, "PostMessages")
        val defined = mapOf(post to PermissionKind.Exfiltrating)
        assertEquals(NeedsApproval, decide(post, pluginDefined = defined))
        assertEquals(NeedsApproval, decide(CALENDAR, rows = listOf(row(post)), pluginDefined = defined))
        assertEquals(Granted, decide(CALENDAR, rows = listOf(row(post, false)), pluginDefined = defined))
    }
}

class PermissionStatusesTest {
    private fun statuses(subject: PermissionSubject?, vararg rows: LockerAppPermission, config: WatchConfig = WatchConfig(enablePlugins = true)) =
        permissionStatuses(subject, rows.toList(), config, emptyMap()).associate { it.permission to it.status }

    @Test
    fun eachRowIsTheUsersDecisionOrWhatThePolicyGivesNow() {
        val api = internetKey("api.example.com")
        assertEquals(
            mapOf(
                api to PermissionStatus.Granted,
                LOCATION to PermissionStatus.Denied,
                CALENDAR to PermissionStatus.Waiting,
                LOCAL_NETWORK to PermissionStatus.Waiting,
            ),
            statuses(subject(), row(api, true), row(LOCATION, false), row(CALENDAR), row(LOCAL_NETWORK)),
        )
    }

    @Test
    fun anAppThatCantSendDataAnywhereIsAllowedWhatItDeclaredUntilThePolicyChanges() {
        assertEquals(mapOf(CALENDAR to PermissionStatus.AllowedAutomatically), statuses(subject(), row(CALENDAR)))
        assertEquals(
            mapOf(CALENDAR to PermissionStatus.Waiting),
            statuses(subject(), row(CALENDAR), config = WatchConfig(enablePlugins = true, permissionPolicy = PermissionPolicy.AlwaysAsk)),
        )
    }

    @Test
    fun withoutItsLockerEntryOnlyDecisionsAreKnown() {
        assertEquals(mapOf(CALENDAR to PermissionStatus.Denied), statuses(null, row(CALENDAR, false), row(LOCATION)))
    }

    @Test
    fun aPickedEntryReadsDeniedWhileItsBarePermissionIs() {
        val vault = writeFilesKey("content://docs/tree/notes")
        assertEquals(
            mapOf(WRITE_FILES to PermissionStatus.Denied, vault to PermissionStatus.Denied),
            statuses(subject(), row(WRITE_FILES, false), row(vault, true)),
        )
        assertEquals(
            mapOf(WRITE_FILES to PermissionStatus.Granted, vault to PermissionStatus.Granted),
            statuses(subject(), row(WRITE_FILES, true), row(vault, true)),
        )
    }

    @Test
    fun aDeclaredReadFilesStaysListedWhileWriteFilesIsDenied() {
        // Turning write off must leave read on the page, so it can be allowed on its own.
        assertEquals(
            mapOf(READ_FILES to PermissionStatus.AllowedAutomatically, WRITE_FILES to PermissionStatus.Denied),
            statuses(subject(), row(READ_FILES), row(WRITE_FILES, false)),
        )
        assertEquals(
            mapOf(READ_FILES to PermissionStatus.Waiting, WRITE_FILES to PermissionStatus.Denied),
            statuses(subject(), row(READ_FILES), row(WRITE_FILES, false), config = WatchConfig(enablePlugins = true, permissionPolicy = PermissionPolicy.AlwaysAsk)),
        )
        assertEquals(
            mapOf(READ_FILES to PermissionStatus.AllowedAutomatically, WRITE_FILES to PermissionStatus.Granted),
            statuses(subject(), row(READ_FILES), row(WRITE_FILES, true)),
        )
    }
}

class CanExfiltrateTest {
    private val dao = FakePermissionDao()

    @Test
    fun aPluginCanExfiltrateUntilItsNetworkIsDenied() = runTest {
        val permissions = permissions(dao)
        assertEquals(false, permissions.canExfiltrate(APP))
        dao.insertOrReplace(row(LOCAL_NETWORK))
        assertEquals(true, permissions.canExfiltrate(APP))
        dao.insertOrReplace(row(LOCAL_NETWORK, false))
        assertEquals(false, permissions.canExfiltrate(APP))
    }

    @Test
    fun aBuiltInCant() = runTest {
        dao.insertOrReplace(row(LOCAL_NETWORK))
        assertEquals(false, permissions(dao, installed = installedAs(null)).canExfiltrate(APP))
    }
}

internal class FakePermissionDao : LockerAppPermissionDao {
    val rows = MutableStateFlow<Map<Pair<Uuid, String>, LockerAppPermission>>(emptyMap())

    override suspend fun insertOrReplace(permission: LockerAppPermission) {
        rows.value += (permission.appUuid to permission.permission) to permission
    }

    override suspend fun insertOrReplaceAll(permissions: List<LockerAppPermission>) =
        permissions.forEach { insertOrReplace(it) }

    override suspend fun insertIfAbsent(permissions: List<LockerAppPermission>) =
        permissions.filter { (it.appUuid to it.permission) !in rows.value }.forEach { insertOrReplace(it) }

    override suspend fun deleteForRemovedApps() {}

    override suspend fun markUsed(appUuid: Uuid, permission: String, atMs: Long) {
        rows.value[appUuid to permission]?.let { insertOrReplace(it.copy(lastUsedAtMs = atMs)) }
    }

    override suspend fun markSeen(appUuid: Uuid, permissions: List<String>, atMs: Long) {
        permissions.forEach { permission ->
            rows.value[appUuid to permission]?.takeIf { it.seenAtMs == null }?.let { insertOrReplace(it.copy(seenAtMs = atMs)) }
        }
    }

    override suspend fun deleteByAppUuid(appUuid: Uuid) {
        rows.value = rows.value.filterKeys { it.first != appUuid }
    }

    override suspend fun deleteByAppUuidAndPermissions(appUuid: Uuid, permissions: List<String>) {
        rows.value = rows.value.filterKeys { it.first != appUuid || it.second !in permissions }
    }

    override suspend fun getByAppUuid(appUuid: Uuid) = rows.value.values.filter { it.appUuid == appUuid }

    override fun flowByAppUuid(appUuid: Uuid): Flow<List<LockerAppPermission>> =
        rows.map { all -> all.values.filter { it.appUuid == appUuid } }

    override fun allFlow(): Flow<List<LockerAppPermission>> = rows.map { it.values.sortedBy { row -> row.requestedAtMs } }

    fun row(permission: String) = rows.value[APP to permission]
}

internal class FixedClock(var ms: Long) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(ms)
}

internal val testScope = LibPebbleCoroutineScope(EmptyCoroutineContext)

internal object NoWatches : Watches {
    override val watches = MutableStateFlow(emptyList<PebbleDevice>())
    override val connectionEvents = emptyFlow<PebbleConnectionEvent>()
    override fun watchesDebugState() = ""
}

internal fun permissions(
    dao: LockerAppPermissionDao,
    config: WatchConfig = WatchConfig(enablePlugins = true),
    clock: Clock = FixedClock(1_000),
    running: RunningApps = RunningApps(NoWatches),
    installed: InstalledApps = installedAs(subject()),
    scope: LibPebbleCoroutineScope = testScope,
) = AppPermissions(dao, installed, config.asFlow(), clock, running, scope)

class AppPermissionsTest {
    private val dao = FakePermissionDao()
    private val clock = FixedClock(1_000)
    private val permissions = permissions(dao, clock = clock)

    @Test
    fun usingAGrantedPermissionRecordsWhenToTheMinute() = runTest {
        permissions.request(subject(), CALENDAR)
        assertEquals(1_000, dao.row(CALENDAR)!!.lastUsedAtMs)
        clock.ms = 30_000
        permissions.request(subject(), CALENDAR)
        assertEquals(1_000, dao.row(CALENDAR)!!.lastUsedAtMs)
        clock.ms = 61_000
        permissions.check(subject(), CALENDAR)
        assertEquals(61_000, dao.row(CALENDAR)!!.lastUsedAtMs)
    }

    @Test
    fun aRefusedUseIsNotAUse() = runTest {
        permissions.setDecisions(APP, mapOf(LOCATION to false))
        permissions.request(subject(), LOCATION)
        assertEquals(null, dao.row(LOCATION)!!.lastUsedAtMs)
        permissions.request(subject(hasCompanionApp = true), CALENDAR)
        assertEquals(null, dao.row(CALENDAR)!!.lastUsedAtMs)
    }

    @Test
    fun aDomainsUseIsRecordedOnTheGrantCoveringIt() = runTest {
        permissions.setDecisions(APP, mapOf(internetKey("example.com") to true))
        permissions.request(subject(), internetKey("api.example.com"))
        assertEquals(1_000, dao.row(internetKey("example.com"))!!.lastUsedAtMs)
    }

    @Test
    fun decidingKeepsWhenItWasLastUsed() = runTest {
        permissions.request(subject(), CALENDAR)
        clock.ms = 5_000
        permissions.setDecisions(APP, mapOf(CALENDAR to false))
        assertEquals(1_000, dao.row(CALENDAR)!!.lastUsedAtMs)
    }

    @Test
    fun withPluginsOffNothingIsRecorded() = runTest {
        val off = permissions(dao, config = WatchConfig(enablePlugins = false), clock = clock)
        dao.insertOrReplace(row(CALENDAR))
        off.request(subject(), CALENDAR)
        off.check(subject(), CALENDAR)
        assertEquals(null, dao.row(CALENDAR)!!.lastUsedAtMs)
    }

    @Test
    fun usingAPermissionRecordsItUndecided() = runTest {
        assertEquals(Granted, permissions.request(subject(), CALENDAR))
        assertEquals(null, dao.row(CALENDAR)!!.granted)
        clock.ms = 2_000
        permissions.request(subject(), CALENDAR)
        assertEquals(1_000, dao.row(CALENDAR)!!.requestedAtMs)
    }

    @Test
    fun somethingThePolicyRefusesOutrightIsNotRecorded() = runTest {
        assertEquals(Denied, permissions.request(subject(), GLOBAL_INTERNET))
        assertEquals(null, dao.row(GLOBAL_INTERNET))
    }

    @Test
    fun answeringKeepsWhenItWasRequested() = runTest {
        permissions.request(subject(legacy = true), CALENDAR)
        clock.ms = 5_000
        permissions.setDecisions(APP, mapOf(CALENDAR to true, LOCATION to false))
        val calendar = dao.row(CALENDAR)!!
        assertEquals(true, calendar.granted)
        assertEquals(1_000, calendar.requestedAtMs)
        assertEquals(5_000, calendar.decidedAtMs)
        assertEquals(false, dao.row(LOCATION)!!.granted)
    }

    @Test
    fun requestNeverOverwritesADecision() = runTest {
        permissions.setDecisions(APP, mapOf(CALENDAR to false))
        assertEquals(Denied, permissions.request(subject(), CALENDAR))
        assertEquals(false, dao.row(CALENDAR)!!.granted)
    }

    @Test
    fun aDomainUnderADecidedParentIsNotRecorded() = runTest {
        permissions.setDecisions(APP, mapOf(internetKey("example.com") to true))
        assertEquals(Granted, permissions.request(subject(), internetKey("api.example.com")))
        assertEquals(null, dao.row(internetKey("api.example.com")))
    }
}

class WaitingRequestsTest {
    private val dao = FakePermissionDao()
    private val running = RunningApps(NoWatches)

    @Test
    fun waitingIsUndecidedAndNeedingApprovalNow() = runTest {
        var config = WatchConfig(enablePlugins = true)
        fun permissions() = permissions(dao, config = config, running = running)
        permissions().recordRequested(APP, listOf(CALENDAR))
        assertEquals(emptyList(), permissions().pendingPermissionRequests().first())
        config = WatchConfig(enablePlugins = true, permissionPolicy = PermissionPolicy.AlwaysAsk)
        assertEquals(listOf(CALENDAR), permissions().pendingPermissionRequests().first().map { it.permission })
        permissions().setDecisions(APP, mapOf(CALENDAR to false))
        assertEquals(emptyList(), permissions().pendingPermissionRequests().first())
    }

    @Test
    fun aSeenRequestKeepsWaitingAndStaysUndecided() = runTest {
        val permissions = permissions(dao, config = WatchConfig(enablePlugins = true, permissionPolicy = PermissionPolicy.AlwaysAsk))
        permissions.recordRequested(APP, listOf(CALENDAR))
        permissions.markPermissionRequestsSeen(APP, listOf(CALENDAR))
        assertEquals(listOf(CALENDAR), permissions.pendingPermissionRequests().first().map { it.permission })
        assertEquals(NeedsApproval, permissions.check(subject(), CALENDAR))
        assertEquals(PermissionStatus.Waiting, permissions.statuses(APP).first().single().status)
    }

    @Test
    fun aVersionDeclaringPermissionsDropsWhatTheLegacyOneWasGiven() = runTest {
        val permissions = permissions(dao)
        permissions.recordRequested(APP, RequestedPermissions(declared = null, hasPkjs = true, hasAndroidCompanion = true))
        permissions.setDecisions(APP, mapOf(LOCATION to false, TIMELINE to true, LEGACY_PEBBLEKIT to false))
        permissions.recordRequested(APP, RequestedPermissions(declared = listOf(PluginPermission(LOCATION), PluginPermission(CALENDAR)), hasPkjs = true))
        assertEquals(setOf(LOCATION, CALENDAR), dao.rows.value.keys.map { it.second }.toSet())
        assertEquals(false, dao.row(LOCATION)!!.granted)
    }

    @Test
    fun aNullDecisionRecordsASeenUndecidedRow() = runTest {
        permissions(dao).setDecisions(APP, mapOf(CALENDAR to null))
        val row = dao.row(CALENDAR)!!
        assertEquals(null, row.granted)
        assertEquals(null, row.decidedAtMs)
        assertEquals(true, row.seenAtMs != null)
    }

    @Test
    fun onlyRunningAppsRequestsAreActive() = runTest {
        val permissions = permissions(dao, running = running)
        permissions.recordRequested(APP, listOf(INTERNET))
        assertEquals(emptyList(), permissions.activePermissionRequests().first())
        running.whileRunning(APP) {
            assertEquals(listOf(INTERNET), permissions.activePermissionRequests().first().map { it.permission })
        }
        assertEquals(emptyList(), permissions.activePermissionRequests().first())
    }

    @Test
    fun aPluginsPermissionStartsWaitingOnceThePluginLoads() = runTest {
        val hue = Uuid.parse("00000000-0000-0000-0002-000000000001")
        val defined = MutableStateFlow(emptyMap<String, PluginDefinedPermission>())
        val unconfined = UnconfinedTestDispatcher(testScheduler)
        val permissions = permissions(
            dao,
            installed = installedAs(subject(), plugins = defined),
            scope = LibPebbleCoroutineScope(backgroundScope.coroutineContext + unconfined),
        )
        permissions.recordRequested(APP, listOf(invokeActionKey(hue)))
        val seen = mutableListOf<List<String>>()
        backgroundScope.launch(unconfined) {
            permissions.pendingPermissionRequests().collect { pending -> seen += pending.map { it.permission } }
        }
        assertEquals(emptyList(), seen.last())
        defined.value = mapOf(
            invokeActionKey(hue) to PluginDefinedPermission(invokeActionKey(hue), hue, "Hue", "Send data", PermissionKind.Exfiltrating),
        )
        assertEquals(listOf(invokeActionKey(hue)), seen.last())
    }

    @Test
    fun anAppThatIsNoLongerInstalledHasNothingWaiting() = runTest {
        val permissions = permissions(dao, installed = installedAs(null))
        permissions.recordRequested(APP, listOf(INTERNET))
        assertEquals(emptyList(), permissions.pendingPermissionRequests().first())
    }
}

class NetworkGateTest {
    private val dao = FakePermissionDao()
    private val permissions = permissions(dao)

    private fun gate(legacy: Boolean = false) =
        NetworkGate(permissions(dao, installed = installedAs(subject(legacy = legacy))), "App", APP)

    @Test
    fun legacyAppAnswersToGlobalInternet() = runTest {
        assertIs<PluginNetworkVerdict.Allowed>(gate(legacy = true).check("https://anywhere.example.org/", wait = false))
        permissions.setDecisions(APP, mapOf(GLOBAL_INTERNET to false))
        assertIs<PluginNetworkVerdict.Denied>(gate(legacy = true).check("https://anywhere.example.org/", wait = false))
    }

    @Test
    fun checkAndWaitOpensOnceTheUserAllowsTheHost() = runTest {
        val verdict = async { gate().check("https://config.example.com/", wait = true) }
        runCurrent()
        permissions.setDecisions(APP, mapOf(internetKey("config.example.com") to true))
        assertIs<PluginNetworkVerdict.Allowed>(verdict.await())
    }

    @Test
    fun withoutPromptingAnUndecidedHostIsRefusedUnasked() = runTest {
        val silent = NetworkGate(permissions, "App", APP, prompt = false)
        assertIs<PluginNetworkVerdict.Denied>(silent.check("https://config.example.com/", wait = true))
        assertEquals(emptyMap(), dao.rows.value)

        permissions.setDecisions(APP, mapOf(internetKey("config.example.com") to true))
        assertIs<PluginNetworkVerdict.Allowed>(silent.check("https://config.example.com/", wait = false))
    }

    @Test
    fun withPluginsOffAnyRequestableUrlIsAllowedWithoutLookingAtTheHost() = runTest {
        val off = permissions(dao, config = WatchConfig(enablePlugins = false))
        val gate = NetworkGate(off, "App", APP)
        assertIs<PluginNetworkVerdict.Allowed>(gate.check("http://0x7f000001/", wait = false))
        assertIs<PluginNetworkVerdict.Allowed>(gate.check("wss://example.com/socket", wait = false))
        assertIs<PluginNetworkVerdict.Denied>(gate.check("file:///data/libpebble3.db", wait = false))
        assertEquals(emptyMap(), dao.rows.value)
    }

    @Test
    fun evenALegacyAppCanOnlyRequestHttpAndWebSocketUrls() = runTest {
        assertIs<PluginNetworkVerdict.Denied>(gate(legacy = true).check("file:///data/libpebble3.db", wait = false))
        assertIs<PluginNetworkVerdict.Denied>(gate(legacy = true).check("relative/path", wait = false))
    }

    @Test
    fun anUnknownHostIsRecordedForItsDomain() = runTest {
        assertIs<PluginNetworkVerdict.Denied>(gate().check("https://tracker.example.org/", wait = false))
        assertEquals(null, dao.row(internetKey("tracker.example.org"))!!.granted)
    }

    @Test
    fun aGrantedDomainCoversItsSubdomainsAndNothingElse() = runTest {
        permissions.setDecisions(APP, mapOf(internetKey("example.com") to true))
        assertIs<PluginNetworkVerdict.Allowed>(gate().check("https://api.example.com/forecast", wait = false))
        assertIs<PluginNetworkVerdict.Allowed>(gate().check("https://example.com/", wait = false))
        assertIs<PluginNetworkVerdict.Denied>(gate().check("https://example.com.evil.example.org/", wait = false))
        assertIs<PluginNetworkVerdict.Denied>(gate().check("https://notexample.com/", wait = false))
    }

    @Test
    fun internetGrantedWithoutADomainIsTheWholeWebButNotTheLan() = runTest {
        permissions.setDecisions(APP, mapOf(INTERNET to true))
        assertIs<PluginNetworkVerdict.Allowed>(gate().check("https://anything.example.com/", wait = false))
        assertIs<PluginNetworkVerdict.Denied>(gate().check("http://192.168.1.4/", wait = false))
        permissions.setDecisions(APP, mapOf(LOCAL_NETWORK to true))
        assertIs<PluginNetworkVerdict.Allowed>(gate().check("http://192.168.1.4/", wait = false))
    }

    @Test
    fun aWebSocketUrlAnswersToItsHostsGrant() = runTest {
        permissions.setDecisions(APP, mapOf(internetKey("example.com") to true))
        assertIs<PluginNetworkVerdict.Allowed>(gate().check("wss://example.com/socket", wait = false))
        assertIs<PluginNetworkVerdict.Denied>(gate().check("ws://other.example.org/socket", wait = false))
    }
}

class InstallReviewTest {
    private val dao = FakePermissionDao()
    private var config = WatchConfig(enablePlugins = true)
    private val permissions get() = permissions(dao, config = config)

    private fun internet(vararg domains: String) =
        PluginPermission(INTERNET, mapOf("domains" to domains.toList()))

    private fun declares(vararg declared: PluginPermission) = RequestedPermissions(declared = declared.toList(), hasPkjs = true)

    private suspend fun reviewed(requested: RequestedPermissions) = permissions.toReview(APP, requested).toSet()

    @Test
    fun eachDeclaredDomainIsItsOwnGrant() {
        assertEquals(
            listOf(internetKey("a.example.com"), internetKey("b.example.com"), LOCAL_NETWORK),
            declares(internet("a.example.com", ".B.example.com"), PluginPermission(LOCAL_NETWORK)).all,
        )
        assertEquals(listOf(INTERNET), declares(PluginPermission(INTERNET)).all)
    }

    @Test
    fun aManifestGetsOnlyTheBareFilePermission() {
        assertEquals(listOf(READ_FILES), PluginPermission(READ_FILES, mapOf("paths" to listOf("/Documents"))).keys())
        assertEquals(listOf(READ_FILES), PluginPermission(readFilesKey("/Documents")).keys())
        assertEquals(listOf(WRITE_FILES), PluginPermission(writeFilesKey("content://docs/tree/x")).keys())
    }

    @Test
    fun readAndWriteFilesAreBothReviewed() = runTest {
        val both = declares(PluginPermission(READ_FILES), PluginPermission(WRITE_FILES))
        assertEquals(listOf(READ_FILES, WRITE_FILES), both.all)
        config = WatchConfig(enablePlugins = true, permissionPolicy = PermissionPolicy.AlwaysAsk)
        assertEquals(setOf(READ_FILES, WRITE_FILES), reviewed(both))
    }

    @Test
    fun aLegacyAppAsksForWhatItUsedToGetImplicitly() {
        val legacy = RequestedPermissions(declared = null, hasPkjs = true)
        assertEquals(setOf(GLOBAL_INTERNET, LOCATION, TIMELINE), legacy.all.toSet())
        assertEquals(emptySet(), legacy.copy(hasPkjs = false).all.toSet())
    }

    @Test
    fun aLegacyAppKeepsPebbleKitOnlyWhenTheStoreListsAnAndroidCompanion() {
        val legacy = RequestedPermissions(declared = null, hasPkjs = false)
        assertEquals(emptyList(), legacy.all)
        val withCompanion = legacy.copy(hasAndroidCompanion = true)
        assertEquals(listOf(LEGACY_PEBBLEKIT), withCompanion.all)
        assertEquals(Granted, decide(LEGACY_PEBBLEKIT, withCompanion.subject(APP)))
        assertEquals(NeedsApproval, decide(LEGACY_PEBBLEKIT, legacy.subject(APP)))
    }

    @Test
    fun anAppThatDeclaresItsPermissionsIsAskedAboutPebbleKitWhateverTheStoreSays() {
        val declared = declares(PluginPermission(LEGACY_PEBBLEKIT)).copy(hasAndroidCompanion = true)
        assertEquals(listOf(LEGACY_PEBBLEKIT), declared.all)
        assertEquals(NeedsApproval, decide(LEGACY_PEBBLEKIT, declared.subject(APP)))
    }

    @Test
    fun theManifestVersionDecidesWhetherAnAppIsLegacy() {
        fun appInfo(extra: String) = Json { ignoreUnknownKeys = true }.decodeFromString(
            PbwAppInfo.serializer(),
            """{"uuid":"$APP","shortName":"A","versionLabel":"1.0","resources":{"media":[]}$extra}""",
        )
        assertEquals(null, appInfo("").declaredPermissions)
        assertEquals(null, appInfo(""","usesPermissions":["Location"]""").declaredPermissions)
        assertEquals(emptyList(), appInfo(""","manifestVersion":2""").declaredPermissions)
        assertEquals(
            listOf(PluginPermission(LOCATION)),
            appInfo(""","manifestVersion":2,"usesPermissions":["Location"]""").declaredPermissions,
        )
    }

    @Test
    fun invokingAPluginsActionsIsOnlyReviewedWhileThePluginCanExfiltrate() = runTest {
        val hue = Uuid.parse("00000000-0000-0000-0002-000000000001")
        val invoke = invokeActionKey(hue)
        val installed = object : InstalledApps {
            override fun subjectsOf(appUuids: Set<Uuid>) =
                flowOf(listOf(subject(), PermissionSubject(hue, legacy = false)).associateBy { it.appUuid })
            override fun pluginPermissions() =
                flowOf(mapOf(invoke to PluginDefinedPermission(invoke, hue, "Hue", "Send data", PermissionKind.Exfiltrating)))
        }
        val permissions = permissions(dao, config = config, installed = installed)
        val declared = declares(PluginPermission("InvokeAction", plugin = hue.toString()))
        assertEquals(emptyList(), permissions.toReview(APP, declared))
        dao.insertOrReplace(LockerAppPermission(hue, internetKey("hue.example.com"), true, requestedAtMs = 1))
        assertEquals(listOf(invoke), permissions.toReview(APP, declared))
    }

    @Test
    fun anAppThatCantSendDataAnywhereInstallsWithoutAReview() = runTest {
        assertEquals(emptySet(), reviewed(declares(PluginPermission(LOCATION))))
    }

    @Test
    fun anAppWithNetworkAccessIsReviewedForEverything() = runTest {
        assertEquals(
            setOf(internetKey("api.example.com"), LOCATION),
            reviewed(declares(internet("api.example.com"), PluginPermission(LOCATION))),
        )
    }

    @Test
    fun aDeclaredMicrophonePermissionIsReviewedAndRemembered() = runTest {
        val requested = declares(internet("api.example.com"), PluginPermission(WATCH_MICROPHONE))
        assertEquals(setOf(internetKey("api.example.com"), WATCH_MICROPHONE), reviewed(requested))
        permissions.setPermissionDecisions(APP, mapOf(WATCH_MICROPHONE to false))
        assertEquals(setOf(internetKey("api.example.com")), reviewed(requested))
    }

    @Test
    fun anUpdateOnlyReviewsWhatIsNew() = runTest {
        val api = internetKey("api.example.com")
        permissions.setDecisions(APP, mapOf(api to true, LOCATION to false))
        assertEquals(emptySet(), reviewed(declares(internet("api.example.com"), PluginPermission(LOCATION))))
        assertEquals(
            setOf(internetKey("tracker.example.org"), CALENDAR),
            reviewed(declares(internet("api.example.com", "tracker.example.org"), PluginPermission(LOCATION), PluginPermission(CALENDAR))),
        )
    }

    @Test
    fun aLegacyAppIsOnlyReviewedWhenTheUserAsksForThat() = runTest {
        val legacy = RequestedPermissions(declared = null, hasPkjs = true)
        assertEquals(emptySet(), reviewed(legacy))
        config = WatchConfig(enablePlugins = true, askLegacyAppsForPermissions = true)
        assertEquals(setOf(GLOBAL_INTERNET, LOCATION, TIMELINE), reviewed(legacy))
    }
}

class RuntimeRequestTest {
    private val dao = FakePermissionDao()
    private val permissions = permissions(dao)
    private val legacy = subject(legacy = true)

    @Test
    fun microphoneRequestsRecordApprovalAndReturnWithoutWaiting() = runTest {
        permissions.recordRequested(APP, listOf(GLOBAL_INTERNET))
        val before = testScheduler.currentTime
        assertEquals(NeedsApproval, permissions.request(APP, WATCH_MICROPHONE))
        assertEquals(before, testScheduler.currentTime)
        assertEquals(null, dao.row(WATCH_MICROPHONE)!!.granted)
        assertEquals(null, dao.row(WATCH_MICROPHONE)!!.lastUsedAtMs)
        permissions.setPermissionDecisions(APP, mapOf(WATCH_MICROPHONE to true))
        assertEquals(Granted, permissions.request(APP, WATCH_MICROPHONE))
        assertEquals(1_000, dao.row(WATCH_MICROPHONE)!!.lastUsedAtMs)
        permissions.setPermissionDecisions(APP, mapOf(WATCH_MICROPHONE to false))
        assertEquals(Denied, permissions.request(APP, WATCH_MICROPHONE))
    }

    @Test
    fun anAnswerInTimeDecidesTheRequest() = runTest {
        permissions.recordRequested(APP, listOf(GLOBAL_INTERNET))
        val decision = async { permissions.request(legacy, CALENDAR, wait = true) }
        runCurrent()
        assertEquals(null, dao.row(CALENDAR)!!.granted)
        permissions.setPermissionDecisions(APP, mapOf(CALENDAR to true))
        assertEquals(Granted, decision.await())
    }

    @Test
    fun aHostUnderAnUndecidedDeclaredDomainIsAskedAboutAsThatDomain() = runTest {
        permissions.recordRequested(APP, listOf(internetKey("example.com")))
        assertEquals(NeedsApproval, permissions.request(subject(), internetKey("api.example.com")))
        assertEquals(null, dao.row(internetKey("api.example.com")))
        permissions.setPermissionDecisions(APP, mapOf(internetKey("example.com") to true))
        assertEquals(Granted, permissions.request(subject(), internetKey("api.example.com")))
    }

    @Test
    fun grantedKeysFollowTheUsersAnswers() = runTest {
        permissions.recordRequested(APP, listOf(GLOBAL_INTERNET))
        permissions.setPermissionDecisions(APP, mapOf(CALENDAR to false))
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            permissions.grantedKeys(legacy, listOf(CALENDAR)).collect { seen += CALENDAR in it }
        }
        permissions.setPermissionDecisions(APP, mapOf(CALENDAR to true))
        permissions.setPermissionDecisions(APP, mapOf(CALENDAR to false))
        assertEquals(listOf(false, true, false), seen)
    }

    @Test
    fun noAnswerLeavesItUndecided() = runTest {
        permissions.recordRequested(APP, listOf(GLOBAL_INTERNET))
        assertEquals(NeedsApproval, permissions.request(legacy, CALENDAR, wait = true))
        assertEquals(null, dao.row(CALENDAR)!!.granted)
    }
}

class LocalMicrophoneGrantsTest {
    private val dao = FakePermissionDao()
    private val permissions = permissions(dao)

    @Test
    fun localAndStreamingCaptureFollowTheSamePermissionPolicy() = runTest {
        val permissions = permissions(dao, config = WatchConfig(enablePlugins = true, permissionPolicy = PermissionPolicy.AlwaysAsk))
        assertEquals(NeedsApproval, permissions.request(APP, WATCH_MICROPHONE))
        assertEquals(emptySet(), permissions.localMicrophoneGrants().first())
        permissions.setPermissionDecisions(APP, mapOf(WATCH_MICROPHONE to true))
        assertEquals(setOf(APP), permissions.localMicrophoneGrants().first())
        assertEquals(Granted, permissions.request(APP, WATCH_MICROPHONE))
        permissions.setPermissionDecisions(APP, mapOf(WATCH_MICROPHONE to false))
        assertEquals(emptySet(), permissions.localMicrophoneGrants().first())
    }

    @Test
    fun neverAskGrantsLocalAndStreamingCaptureWithoutOverridingADenial() = runTest {
        val permissions = permissions(dao, config = WatchConfig(enablePlugins = true, permissionPolicy = PermissionPolicy.NeverAsk))
        permissions.recordRequested(APP, listOf(WATCH_MICROPHONE, INTERNET))
        assertEquals(Granted, permissions.request(APP, WATCH_MICROPHONE))
        assertEquals(setOf(APP), permissions.localMicrophoneGrants().first())
        permissions.setPermissionDecisions(APP, mapOf(WATCH_MICROPHONE to false))
        assertEquals(Denied, permissions.request(APP, WATCH_MICROPHONE))
        assertEquals(emptySet(), permissions.localMicrophoneGrants().first())
    }

    @Test
    fun localGrantsFollowPolicyChanges() = runTest {
        val config = MutableStateFlow(LibPebbleConfig(
            watchConfig = WatchConfig(enablePlugins = true, permissionPolicy = PermissionPolicy.AlwaysAsk)))
        val permissions = AppPermissions(dao, installedAs(subject()),
            WatchConfigFlow(config),
            FixedClock(1_000), RunningApps(NoWatches), testScope)
        permissions.recordRequested(APP, listOf(WATCH_MICROPHONE, INTERNET))
        val seen = mutableListOf<Set<Uuid>>()
        val streaming = mutableListOf<Set<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            permissions.grantedKeys(subject(), listOf(WATCH_MICROPHONE)).collect { streaming += it }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            permissions.localMicrophoneGrants().collect { seen += it }
        }
        runCurrent()
        config.value = config.value.copy(watchConfig = config.value.watchConfig.copy(permissionPolicy = PermissionPolicy.NeverAsk))
        runCurrent()
        config.value = config.value.copy(watchConfig = config.value.watchConfig.copy(permissionPolicy = PermissionPolicy.AlwaysAsk))
        runCurrent()
        assertEquals(listOf(emptySet(), setOf(APP), emptySet()), seen)
        assertEquals(listOf(emptySet(), setOf(WATCH_MICROPHONE), emptySet()), streaming)
    }

    @Test
    fun disablingEnforcementSyncsNoGrantsToTheWatchAndRemovedAppsHaveNoGrant() = runTest {
        permissions.setPermissionDecisions(APP, mapOf(WATCH_MICROPHONE to true))
        val disabled = permissions(dao, config = WatchConfig(enablePlugins = false))
        assertEquals(emptySet(), disabled.localMicrophoneGrants().first())
        assertEquals(Granted, disabled.request(APP, WATCH_MICROPHONE))
        permissions.setPermissionDecisions(APP, mapOf(WATCH_MICROPHONE to false))
        assertEquals(emptySet(), disabled.localMicrophoneGrants().first())
        assertEquals(Granted, disabled.request(APP, WATCH_MICROPHONE))
        val removed = permissions(dao, installed = installedAs(null))
        assertEquals(emptySet(), removed.localMicrophoneGrants().first())
    }

    @Test
    fun lockerProjectionFollowsDecisionsAndRefreshesAfterMetadataChanges() = runTest {
        val subjects = MutableSharedFlow<Map<Uuid, PermissionSubject>>(replay = 1)
        val app = subject()
        subjects.emit(mapOf(APP to app))
        val seen = mutableListOf<Set<Uuid>>()
        val installed = object : InstalledApps {
            override fun subjectsOf(appUuids: Set<Uuid>) = subjects.map { all -> all.filterKeys { it in appUuids } }
            override fun pluginPermissions() = flowOf(emptyMap<String, PluginDefinedPermission>())
            override suspend fun syncMicrophoneGrants(grants: Set<Uuid>) { seen += grants }
        }
        val permissions = permissions(dao, installed = installed,
            scope = LibPebbleCoroutineScope(backgroundScope.coroutineContext))
        permissions.init()
        runCurrent()
        permissions.setPermissionDecisions(APP, mapOf(WATCH_MICROPHONE to true))
        runCurrent()
        subjects.emit(mapOf(APP to app))
        runCurrent()
        permissions.setPermissionDecisions(APP, mapOf(WATCH_MICROPHONE to false))
        runCurrent()
        assertEquals(listOf(emptySet(), setOf(APP), setOf(APP), emptySet()), seen)
    }

    @Test
    fun unrelatedGrantsNeverAllowLocalCapture() = runTest {
        permissions.setPermissionDecisions(APP, mapOf(INTERNET to true, LOCATION to true))
        assertEquals(emptySet(), permissions.localMicrophoneGrants().first())
    }
}

class NotificationsScopeTest {
    private val maps = notificationsKey("com.google.android.apps.maps")

    @Test
    fun eachDeclaredPackageIsItsOwnKey() {
        val declared = PluginPermission(NOTIFICATIONS, mapOf(PARAM_PACKAGES to listOf("com.google.android.apps.maps")))
        assertEquals(listOf(maps), declared.keys())
        assertEquals(listOf(NOTIFICATIONS), PluginPermission(NOTIFICATIONS).keys())
        assertEquals("com.google.android.apps.maps", packageOf(maps))
    }

    @Test
    fun allNotificationsCoversEachPackage() {
        val exfiltrating = listOf(row(GLOBAL_INTERNET, true))
        assertEquals(Granted, decide(maps, rows = exfiltrating + row(NOTIFICATIONS, true)))
        assertEquals(Denied, decide(maps, rows = exfiltrating + row(NOTIFICATIONS, false)))
        assertEquals(Denied, decide(maps, rows = exfiltrating + row(NOTIFICATIONS, true) + row(maps, false)))
        assertEquals(NeedsApproval, decide(maps, rows = exfiltrating + row(notificationsKey("com.example"), true)))
    }
}
