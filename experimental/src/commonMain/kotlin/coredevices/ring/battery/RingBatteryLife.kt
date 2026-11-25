package coredevices.ring.battery

import CommonApiConfig
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.ring.data.entity.firestore.FirestoreKnownRing
import coredevices.ring.data.entity.room.battery.RingBatteryHistory
import coredevices.ring.database.room.dao.RingBatteryHistoryDao
import coredevices.ring.database.Preferences
import coredevices.ring.database.firestore.dao.FirestoreKnownRingsDao
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

sealed interface RingBatteryLifeState {
    /** No Index 01 known for this pairing (Index also runs with just a Pebble): hide the section. */
    data object NoRing : RingBatteryLifeState

    data object NoData : RingBatteryLifeState

    data class Learning(val daysCollected: Int, val daysNeeded: Int) : RingBatteryLifeState

    data class Estimated(
        val daysLeft: Int,
        val remainingPct: Int,
        val replaceSoon: Boolean,
        val collectionsPerDay: Double,
    ) : RingBatteryLifeState
}

/** Pairing address and ring-reported MAC differ in the first byte (EA… vs E9…), failsafe in the last. */
internal fun sameRingAddress(a: String, b: String): Boolean {
    fun middle(x: String) = x.replace(":", "").uppercase()
        .takeIf { it.length == 12 && it.all { c -> c in '0'..'9' || c in 'A'..'F' } }
        ?.substring(2, 10)
    return middle(a)?.let { it == middle(b) } ?: false
}

/** The paired ring's serial from known_rings; null unless exactly one serial matches. */
internal fun knownRingSerial(rings: Collection<FirestoreKnownRing>, pairedId: String): String? =
    rings.filter { sameRingAddress(it.mac, pairedId) }
        .mapNotNull { it.serial?.takeIf(String::isNotBlank) }
        .distinct()
        .singleOrNull()

/**
 * On-phone usage history per ring, fed by RingSync whether or not analytics is on. One DB row per
 * serial; only the pointer to the currently paired ring lives in [settings].
 */
class RingBatteryLifeStore(
    private val dao: RingBatteryHistoryDao,
    private val settings: Settings,
    private val clock: Clock = Clock.System,
) {
    data class Samples(
        val firstAt: Instant,
        val firstCount: Int,
        val lastAt: Instant,
        val lastCount: Int,
    )

    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    /** [pairedId] null = not the paired ring: its history is kept but it never becomes the shown ring. */
    suspend fun recordCollections(serial: String, count: Int, pairedId: String?) = guarded {
        val now = clock.now()
        val row = dao.get(serial) ?: RingBatteryHistory(serial)
        val prev = row.samples()
        // A lower count means a different/reset ring under the same serial key: start over.
        val next = if (prev == null || count < prev.lastCount) {
            row.copy(firstAt = now, firstCount = count, lastAt = now, lastCount = count)
        } else {
            row.copy(lastAt = now, lastCount = count)
        }
        dao.upsert(next)
        if (pairedId != null) setPairedRing(serial, pairedId)
        _changes.value++
    }

    fun rememberPairedRing(serial: String, pairedId: String) {
        if (connectedSerial(pairedId) == serial) return
        setPairedRing(serial, pairedId)
        _changes.value++
    }

    /** A transfer without a reading keeps the last one, so a low-battery warning never vanishes. */
    suspend fun recordVoltage(serial: String, voltageMv: Int?) = guarded {
        val mv = voltageMv?.takeIf { it > 0 } ?: return@guarded
        dao.upsert((dao.get(serial) ?: RingBatteryHistory(serial)).copy(voltageMv = mv))
        _changes.value++
    }

    /** Serial of the ring last seen over the current pairing; null if that's a different (old) ring. */
    fun connectedSerial(pairedId: String?): String? =
        settings.getStringOrNull(KEY_LAST_SERIAL)
            ?.takeIf { pairedId != null && settings.getStringOrNull(KEY_LAST_PAIRED_ID) == pairedId }

    suspend fun localState(serial: String?): RingBatteryLifeState {
        if (serial == null) return RingBatteryLifeState.NoData
        val row = dao.get(serial)
        val voltageMv = row?.voltageMv
        val s = row?.samples()
            ?: return if (RingBatteryEstimator.isPastCliff(voltageMv)) replaceSoon() else RingBatteryLifeState.NoData
        // Rate only over observed time: no transfer since lastAt is not proof of no use.
        val observed = s.lastAt - s.firstAt
        val ageDays = (clock.now() - s.firstAt).inWholeMinutes / MINUTES_PER_DAY
        if (observed < LEARNING_PERIOD) {
            val e = RingBatteryEstimator.estimate(s.lastCount, ageDays, 0.0, voltageMv)
            if (e.replaceSoon) return replaceSoon()
            return RingBatteryLifeState.Learning(observed.inWholeDays.toInt(), LEARNING_PERIOD.inWholeDays.toInt())
        }
        val rate = (s.lastCount - s.firstCount) / (observed.inWholeMinutes / MINUTES_PER_DAY)
        // ponytail: age starts at the first sample on this phone, so earlier idle drain (≤3 mAh/year) is missed.
        val e = RingBatteryEstimator.estimate(s.lastCount, ageDays, rate, voltageMv)
        return RingBatteryLifeState.Estimated(e.daysLeft, e.remainingPct, e.replaceSoon, rate)
    }

    private fun RingBatteryHistory.samples(): Samples? {
        return Samples(firstAt ?: return null, firstCount ?: return null, lastAt ?: return null, lastCount ?: return null)
    }

    private fun replaceSoon() = RingBatteryLifeState.Estimated(0, 0, true, 0.0)

    private fun setPairedRing(serial: String, pairedId: String) {
        settings.putString(KEY_LAST_SERIAL, serial)
        settings.putString(KEY_LAST_PAIRED_ID, pairedId)
    }

    /** Battery history is optional: a storage failure must never break the transfer that fed it. */
    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Failed to record battery sample" }
        }
    }

    companion object {
        /** A week covers the weekday/weekend usage cycle. */
        val LEARNING_PERIOD = 7.days
        private const val MINUTES_PER_DAY = 24.0 * 60
        private const val KEY_LAST_SERIAL = "ring_battery_last_serial"
        private const val KEY_LAST_PAIRED_ID = "ring_battery_last_paired_id"
        private val logger = Logger.withTag("RingBatteryLifeStore")
    }
}

@Serializable
internal data class ServerRing(
    val serial: String,
    @SerialName("collections_per_day") val collectionsPerDay: Double,
    @SerialName("days_left") val daysLeft: Int,
    @SerialName("remaining_pct") val remainingPct: Int,
    val status: String,
    @SerialName("computed_at") val computedAt: String,
)

/** A usable server estimate for [serial] as of [now], or null if it's for another ring, stale or malformed. */
internal fun ServerRing.toEstimate(serial: String, now: Instant, maxAge: Duration): RingBatteryLifeState.Estimated? {
    val computed = runCatching { Instant.parse(computedAt) }.getOrNull() ?: return null
    val valid = this.serial == serial &&
        status in setOf("ok", "replace_soon") &&
        daysLeft >= 0 && remainingPct in 0..100 &&
        collectionsPerDay.isFinite() && collectionsPerDay >= 0 &&
        computed <= now + 1.hours && now - computed <= maxAge
    if (!valid) return null
    return RingBatteryLifeState.Estimated(daysLeft, remainingPct, status == "replace_soon", collectionsPerDay)
}

/**
 * Battery estimate for the paired ring only. With analytics on, the daily eng-dash estimate
 * (full heartbeat history) wins; any miss or failure falls back to the on-phone estimate.
 */
class RingBatteryLifeRepository(
    private val store: RingBatteryLifeStore,
    private val preferences: Preferences,
    private val settings: Settings,
    private val httpClient: HttpClient,
    private val apiConfig: CommonApiConfig,
    private val knownRings: FirestoreKnownRingsDao,
    private val clock: Clock = Clock.System,
) {
    @Serializable
    private data class ServerResponse(val ring: ServerRing? = null)

    /** Server answer: [state] null = no estimate for this ring (cached like a hit, so misses don't refetch). */
    private class ServerHit(val key: String, val fetchedAt: Instant, val state: RingBatteryLifeState.Estimated?)
    private var lastServer: ServerHit? = null
    private var lastKnownSerial: Pair<String, String?>? = null

    /** Restarts (via null = loading) on re-pair or account change; reloads in place on each new sample. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<RingBatteryLifeState?> =
        combine(preferences.ringPaired, Firebase.auth.authStateChanged.map { it?.uid }) { paired, uid -> paired to uid }
            .distinctUntilChanged()
            .flatMapLatest {
                flow {
                    emit(null)
                    emitAll(store.changes.mapLatest { load() })
                }
            }

    suspend fun load(): RingBatteryLifeState {
        val pairedId = preferences.ringPaired.value ?: return RingBatteryLifeState.NoRing
        val serial = store.connectedSerial(pairedId)
            ?: knownSerial(pairedId)?.also { store.rememberPairedRing(it, pairedId) }
            ?: return RingBatteryLifeState.NoRing
        val local = store.localState(serial)
        if (!settings.getBoolean(KEY_ENABLE_MIXPANEL_UPLOADS, true)) return local
        // The phone sees voltage first-hand; a server row can lag it by a day.
        if (local is RingBatteryLifeState.Estimated && local.replaceSoon) return local
        return serverState(serial) ?: local
    }

    private suspend fun serverState(serial: String): RingBatteryLifeState.Estimated? {
        val key = "${Firebase.auth.currentUser?.uid}/$serial"
        val now = clock.now()
        val cached = lastServer?.takeIf { it.key == key }
        if (cached != null && now - cached.fetchedAt < SERVER_REFRESH) return cached.state
        return when (val result = fetchServer(serial)) {
            is FetchResult.Answer -> {
                lastServer = ServerHit(key, now, result.state)
                result.state
            }
            // Transient: keep the last answer, but never past the server's own freshness limit.
            FetchResult.Failed -> cached?.takeIf { now - it.fetchedAt < SERVER_MAX_AGE }?.state
        }
    }

    private suspend fun knownSerial(pairedId: String): String? {
        lastKnownSerial?.takeIf { it.first == pairedId }?.let { return it.second }
        return try {
            // Settings can open before Firebase restores the session; the DAO reads nothing until then.
            awaitSignIn() ?: return null
            knownRingSerial(knownRings.getAll().values, pairedId).also { lastKnownSerial = pairedId to it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Known rings lookup failed" }
            null
        }
    }

    private suspend fun awaitSignIn() =
        withTimeoutOrNull(SIGN_IN_WAIT) { Firebase.auth.authStateChanged.filterNotNull().first() }

    private sealed interface FetchResult {
        data class Answer(val state: RingBatteryLifeState.Estimated?) : FetchResult
        data object Failed : FetchResult
    }

    private suspend fun fetchServer(serial: String): FetchResult {
        val baseUrl = apiConfig.bugUrl?.takeIf { it.isNotBlank() } ?: return FetchResult.Answer(null)
        return try {
            val token = awaitSignIn()?.getIdToken(false) ?: return FetchResult.Answer(null)
            val response = httpClient.get("${baseUrl.trimEnd('/')}/ring/battery") {
                parameter("serial", serial)
                header("x-google-id-token", token)
            }
            when {
                response.status.isSuccess() -> FetchResult.Answer(
                    json.decodeFromString<ServerResponse>(response.bodyAsText()).ring
                        ?.toEstimate(serial, clock.now(), SERVER_MAX_AGE)
                )
                response.status == HttpStatusCode.TooManyRequests || response.status.value >= 500 -> {
                    logger.w { "Battery estimate request failed: ${response.status}" }
                    FetchResult.Failed
                }
                else -> FetchResult.Answer(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Battery estimate request failed; using local estimate" }
            FetchResult.Failed
        }
    }

    private companion object {
        /** = pebble's SettingsKeys.KEY_ENABLE_MIXPANEL_UPLOADS (that module isn't visible from here). */
        const val KEY_ENABLE_MIXPANEL_UPLOADS = "enable_mixpanel_uploads"
        /** The cron runs daily and skips rings with no heartbeat in 30 days, so an older row is stale. */
        val SERVER_MAX_AGE = 3.days
        val SERVER_REFRESH = 1.hours
        val SIGN_IN_WAIT = 10.seconds
        val logger = Logger.withTag("RingBatteryLifeRepository")
        val json = Json { ignoreUnknownKeys = true }
    }
}
