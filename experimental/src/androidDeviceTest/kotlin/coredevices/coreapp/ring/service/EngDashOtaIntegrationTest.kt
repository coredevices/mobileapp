package coredevices.coreapp.ring.service

import androidx.test.platform.app.InstrumentationRegistry
import coredevices.api.EngDashOtaApiImpl
import coredevices.ring.service.RingSync
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.Closeable
import kotlin.random.Random
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises `/ota/latest` against an eng-dash on the LAN running in its local mode
 * (`OTA_DEV_INSECURE_AUTH=1`, see eng-dash `docs/local-ota-dev.md`) with a ring release uploaded.
 * Each test signs in to the dev admin and puts a fresh uid on the `internal` track, so no other
 * server setup is needed. Skipped unless the server URL is supplied:
 *
 *   ./gradlew :experimental:connectedAndroidDeviceTest \
 *     -Pandroid.testInstrumentationRunnerArguments.otaBaseUrl=http://192.168.1.174:3006/api \
 *     -Pandroid.testInstrumentationRunnerArguments.class=coredevices.coreapp.ring.service.EngDashOtaIntegrationTest
 */
class EngDashOtaIntegrationTest {

    private val baseUrl = InstrumentationRegistry.getArguments().getString("otaBaseUrl")?.trimEnd('/')

    private val hwVersion = RingSync.SATELLITE_HW_VER
    private val hwVersionFmt = "index01_${hwVersion.first}-${hwVersion.second}"
    private val uid = "coreapp-test-${hex(6)}"
    private val serial = "CATEST${hex(4).uppercase()}"
    private val mac = "AABBCC${hex(3).uppercase()}"

    @Volatile
    private var bearer: String? = uid
    private val engine = OkHttp.create {
        addInterceptor { chain ->
            val request = chain.request().newBuilder().removeHeader("Authorization")
            bearer?.let { request.header("Authorization", "Bearer $it") }
            chain.proceed(request.build())
        }
    }

    private lateinit var api: EngDashOtaApiImpl
    private lateinit var admin: LocalAdmin

    @Before
    fun setUp() {
        Assume.assumeTrue(
            "Pass -Pandroid.testInstrumentationRunnerArguments.otaBaseUrl=http://<lan ip>:3006/api",
            baseUrl != null,
        )
        stopKoin()
        startKoin {
            modules(module { factory<HttpClientEngine> { engine } })
        }
        api = EngDashOtaApiImpl(baseUrl!!)
        admin = LocalAdmin(baseUrl!!)
        runBlocking { admin.setUserTrack(uid, "internal") }
    }

    @After
    fun tearDown() {
        stopKoin()
        if (::admin.isInitialized) admin.close()
    }

    private suspend fun latest(
        currentVersion: String? = "v0.1",
        serial: String = this.serial,
        hardwareVersion: String = hwVersionFmt,
    ) = api.getLatestFirmware(
        deviceSerial = serial,
        hardwareVersion = hardwareVersion,
        currentVersion = currentVersion,
        deviceMac = mac,
    )

    @Test
    fun outdatedDeviceIsOfferedTheDeployedRelease() = runBlocking {
        val result = assertNotNull(latest(currentVersion = "v3.70.0"), "no update for $hwVersionFmt; is a ring release uploaded?")

        assertTrue(RING_VERSION.matches(result.version), result.version)
        assertFalse(result.isDowngrade)
        val url = result.artifacts.single().url
        assertTrue(url.startsWith("http://") || url.startsWith("https://"), url)
    }

    @Test
    fun recoveryModeIsOfferedTheSameRelease() = runBlocking {
        val outdated = assertNotNull(latest(currentVersion = "v0.1"))
        val recovery = assertNotNull(latest(currentVersion = null))

        assertEquals(outdated.version, recovery.version)
    }

    @Test
    fun deviceOnServedVersionGetsNoContent() = runBlocking {
        val served = assertNotNull(latest()).version

        assertNull(latest(currentVersion = served))
    }

    @Test
    fun unknownHardwareVersionGetsNoContent() = runBlocking {
        assertNull(latest(hardwareVersion = "index01_99-99"))
    }

    @Test
    fun missingBearerIsUnauthorized() = runBlocking {
        bearer = null

        val e = assertFailsWith<Exception> { latest() }
        assertContains(e.message.orEmpty(), "401")
    }

    @Test
    fun malformedSerialIsBadRequest() = runBlocking {
        val e = assertFailsWith<Exception> { latest(serial = "bad.serial") }
        assertContains(e.message.orEmpty(), "400")
    }

    @Test
    fun eachTrackServesItsOwnDeployment() = runBlocking {
        val expected = admin.expectedVersionByTrack(hwVersionFmt)
        assertTrue(expected.values.any { it != null }, "no ring release deployed to any track: $expected")

        for ((track, version) in expected) {
            val trackUid = "$uid-$track"
            admin.setUserTrack(trackUid, track)
            bearer = trackUid
            assertEquals(version, latest(currentVersion = "v0.1")?.version, "track $track")
        }

        bearer = "$uid-untracked"
        assertEquals(expected["production"], latest(currentVersion = "v0.1")?.version, "uid with no track row")
    }

    /** Dev-insecure NextAuth session over the few admin calls the tests need. */
    private class LocalAdmin(private val baseUrl: String) : Closeable {
        private val client = HttpClient(OkHttp) { install(HttpCookies) }
        private var signedIn = false

        private suspend fun signIn() {
            if (signedIn) return
            val csrf = getJson("$baseUrl/auth/csrf").getValue("csrfToken").jsonPrimitive.content
            client.submitForm("$baseUrl/auth/callback/dev-insecure", parameters {
                append("csrfToken", csrf)
                append("email", "ota-integration-test@dev.local")
                append("json", "true")
            })
            signedIn = true
        }

        private suspend fun getJson(url: String) = Json.parseToJsonElement(client.get(url).bodyAsText()).jsonObject

        suspend fun setUserTrack(uid: String, track: String) {
            signIn()
            val resp = client.post("$baseUrl/ota/admin/user-track") {
                contentType(ContentType.Application.Json)
                setBody("""{"firebase_uid":"$uid","model":"index01","track":"$track"}""")
            }
            check(resp.status == HttpStatusCode.OK) {
                "user-track returned ${resp.status} ${resp.bodyAsText()}; is eng-dash running with OTA_DEV_INSECURE_AUTH=1?"
            }
        }

        /**
         * What the resolver should serve a device on "v0.1" for every track: a rolling track pins its
         * newest active deployment, otherwise the highest available ring release with an active plain
         * deployment. Rollback, staged and must-pass-through setups are rejected rather than modelled.
         */
        suspend fun expectedVersionByTrack(hardwareVersion: String): Map<String, String?> {
            signIn()
            val tracks = getJson("$baseUrl/ota/admin/tracks").getValue("tracks").jsonArray.map { it.jsonObject }
            val releases = getJson("$baseUrl/ota/admin/releases").getValue("releases").jsonArray
                .map { it.jsonObject }
                .filter { it.str("product") == "ring" && it.str("status") == "available" }
            val hasArtifact = { release: JsonObject ->
                release.getValue("artifacts").jsonArray.any { it.jsonObject.str("hardware_version") == hardwareVersion }
            }
            return tracks.associate { track ->
                val name = track.str("name")
                val active = releases.flatMap { release ->
                    release.getValue("deployments").jsonArray.map { it.jsonObject }
                        .filter { it.str("model") == "index01" && it.str("track") == name && it.str("state") == "active" }
                        .map { it to release }
                }
                active.forEach { (deployment, _) ->
                    check(!deployment.getValue("is_rollback").jsonPrimitive.boolean) { "track $name has a rollback" }
                    check(deployment.getValue("staged_pct").jsonPrimitive.int == 100) { "track $name has a staged rollout" }
                }
                val pinned = if (track.getValue("rolling").jsonPrimitive.boolean) {
                    active.maxByOrNull { (deployment, _) -> deployment.str("created_at") }?.second?.takeIf(hasArtifact)
                } else null
                val candidates = active.map { it.second }.distinct().filter(hasArtifact)
                candidates.forEach {
                    check(!it.getValue("must_pass_through").jsonPrimitive.boolean) { "${it.str("version")} is must-pass-through" }
                }
                name to (pinned ?: candidates.maxByOrNull { ringVersionKey(it.str("version")) })?.str("version")
            }
        }

        private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

        private fun ringVersionKey(version: String): Long =
            version.removePrefix("v").substringBefore('-').split('.').map { it.toLong() }
                .let { it + List(4 - it.size) { 0L } }
                .fold(0L) { acc, part -> acc * 100_000 + part }

        override fun close() = client.close()
    }

    private companion object {
        val RING_VERSION = Regex("""^v\d+\.\d+$""")

        fun hex(bytes: Int) = Random.nextBytes(bytes).joinToString("") { "%02x".format(it) }
    }
}
