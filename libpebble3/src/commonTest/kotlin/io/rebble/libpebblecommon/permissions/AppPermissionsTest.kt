package io.rebble.libpebblecommon.permissions

import io.rebble.libpebblecommon.WatchConfig
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
import io.rebble.libpebblecommon.plugin.PluginManifest
import io.rebble.libpebblecommon.plugin.PluginNetworkVerdict
import io.rebble.libpebblecommon.plugin.PluginPermission
import io.rebble.libpebblecommon.plugin.PluginPermissions.CALENDAR
import io.rebble.libpebblecommon.plugin.PluginPermissions.GLOBAL_INTERNET
import io.rebble.libpebblecommon.plugin.PluginPermissions.INTERNET
import io.rebble.libpebblecommon.plugin.PluginPermissions.LOCAL_NETWORK
import io.rebble.libpebblecommon.plugin.PluginPermissions.LOCATION
import io.rebble.libpebblecommon.plugin.PluginPermissions.LEGACY_PEBBLEKIT
import io.rebble.libpebblecommon.plugin.PluginPermissions.READ_WATCH_SETTINGS
import io.rebble.libpebblecommon.plugin.PluginPermissions.TIMELINE
import io.rebble.libpebblecommon.plugin.PluginPermissions.WRITE_WATCH_SETTINGS
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
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

private val APP = Uuid.parse("00000000-0000-0000-0000-00000000a001")

private fun subject(
    legacy: Boolean = false,
    hasAndroidCompanion: Boolean = false,
    hasCompanionApp: Boolean = false,
) = PermissionSubject(APP, legacy, hasAndroidCompanion, hasCompanionApp)

private fun installedAs(
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
    fun theUsersDecisionWins() {
        assertEquals(Granted, decide(CALENDAR, subject(hasCompanionApp = true), listOf(row(CALENDAR, true))))
        assertEquals(Denied, decide(CALENDAR, rows = listOf(row(CALENDAR, false))))
    }

    @Test
    fun unknownPermissionIsDenied() {
        assertEquals(Denied, decide("Teleport"))
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

private class FakePermissionDao : LockerAppPermissionDao {
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

    override suspend fun getByAppUuid(appUuid: Uuid) = rows.value.values.filter { it.appUuid == appUuid }

    override fun flowByAppUuid(appUuid: Uuid): Flow<List<LockerAppPermission>> =
        rows.map { all -> all.values.filter { it.appUuid == appUuid } }

    override fun allFlow(): Flow<List<LockerAppPermission>> = rows.map { it.values.sortedBy { row -> row.requestedAtMs } }

    fun row(permission: String) = rows.value[APP to permission]
}

private class FixedClock(var ms: Long) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(ms)
}

private val testScope = LibPebbleCoroutineScope(EmptyCoroutineContext)

private object NoWatches : Watches {
    override val watches = MutableStateFlow(emptyList<PebbleDevice>())
    override val connectionEvents = emptyFlow<PebbleConnectionEvent>()
    override fun watchesDebugState() = ""
}

private fun permissions(
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
