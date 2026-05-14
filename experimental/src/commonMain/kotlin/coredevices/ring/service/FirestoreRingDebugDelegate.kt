package coredevices.ring.service

import co.touchlab.kermit.Logger
import coredevices.analytics.CoreAnalytics
import coredevices.haversine.KMPHaversineDebugDelegate
import coredevices.haversine.KMPHaversineDebugInfo
import coredevices.haversine.KMPHaversineSatellite
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.fromMilliseconds
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class FirestoreRingDebugDelegate(
    private val analytics: CoreAnalytics
): KMPHaversineDebugDelegate {
    companion object {
        private val logger = Logger.withTag("FirestoreRingDebugDelegate")
        private val RX_RSSI_INTERVAL = 2.hours
    }
    private val pendingUploads = mutableListOf<KMPHaversineDebugInfo>()
    private var waitForAuthJob: Job? = null
    private var lastRxRssiReadTime: TimeMark? = null

    private fun waitForAuth() {
        if (Firebase.auth.currentUser != null) return
        if (waitForAuthJob != null && waitForAuthJob?.isActive == true) return
        waitForAuthJob = GlobalScope.launch {
            val authFlow = Firebase.auth.authStateChanged.filterNotNull().first()
            logger.i { "Authenticated user detected, uploading pending debug info (${pendingUploads.size} items)." }
            val uploads = pendingUploads.toList()
            pendingUploads.clear()
            uploads.forEach { info ->
                handleHaversineDebugInfo(info)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun handleHaversineDebugInfo(info: KMPHaversineDebugInfo) {
        if (Firebase.auth.currentUser == null) {
            logger.w { "No authenticated user, adding to pending uploads." }
            pendingUploads.add(info)
            waitForAuth()
            return
        }
        val userId = Firebase.auth.currentUser?.uid ?: return
        val debugCollection = Firebase.firestore.collection("index_dumps")
            .document(userId)
            .collection("index_01")
        GlobalScope.launch {
            try {
                val doc = info.toJson()
                val docRef = debugCollection.add(doc)
                logger.i { "Uploaded Haversine debug info to Firestore: ${docRef.id}" }
            } catch (e: Exception) {
                logger.e(e) { "Failed to upload Haversine debug info to Firestore." }
            }
        }
    }

    override fun shouldReadRxRSSI(satellite: KMPHaversineSatellite): Boolean {
        val elapsed = lastRxRssiReadTime?.elapsedNow()
        if (elapsed == null || elapsed >= RX_RSSI_INTERVAL) {
            lastRxRssiReadTime = TimeSource.Monotonic.markNow()
            return true
        } else {
            return false
        }
    }

    override fun handleRxRSSI(rssi: Float, satellite: KMPHaversineSatellite) {
        val txRssi = satellite.lastAdvertisement?.rssi
        val diff = txRssi?.let { (it - rssi).absoluteValue }
        logger.i { "Received Rx RSSI: $rssi for satellite ${satellite.name} (${satellite.id}), tx = $txRssi, diff = $diff" }
        val state = satellite.state.value
        analytics.logEvent(
            "ring.rssi_measurement",
            mapOf(
                "ring_serial" to (state?.programmedSerialNumber ?: state?.serialNumber ?: "<none>"),
                "ring_rssi" to rssi,
                "phone_rssi" to (txRssi ?: "<none>"),
                "rssi_diff" to (diff ?: "<none>")
            )
        )
    }

    private fun KMPHaversineDebugInfo.toJson(): FirestoreHaversineDebugInfo = FirestoreHaversineDebugInfo(
        timestamp = Timestamp.fromMilliseconds(timestamp.toEpochMilliseconds().toDouble()),
        satelliteId = satelliteId,
        satelliteName = satelliteName,
        satelliteVersion = satelliteVersion,
        satelliteSerial = satelliteSerial,
        dump = FirestoreHaversineDebugDump(
            coreDump = dump.coreDump?.let { Base64.encode(it) },
            rebootReasons = dump.rebootReasons.map { reason ->
                FirestoreHaversineDebugRebootReason(
                    code = reason.code,
                    context = reason.context,
                    description = reason.description
                )
            }
        )
    )
}

@Serializable
private data class FirestoreHaversineDebugInfo(
    val timestamp: Timestamp,
    val satelliteId: String,
    val satelliteName: String,
    val satelliteVersion: String,
    val satelliteSerial: String,
    val dump: FirestoreHaversineDebugDump
)

@Serializable
private data class FirestoreHaversineDebugDump(
    val coreDump: String?,
    val rebootReasons: List<FirestoreHaversineDebugRebootReason>
)

@Serializable
private data class FirestoreHaversineDebugRebootReason(
    val code: UInt,
    val context: UInt,
    val description: String?
)