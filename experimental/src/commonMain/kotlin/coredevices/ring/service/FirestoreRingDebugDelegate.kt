package coredevices.ring.service

import co.touchlab.kermit.Logger
import coredevices.haversine.KMPHaversineDebugDelegate
import coredevices.haversine.KMPHaversineDebugInfo
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

class FirestoreRingDebugDelegate: KMPHaversineDebugDelegate {
    companion object {
        private val logger = Logger.withTag("FirestoreRingDebugDelegate")
    }
    private val pendingUploads = mutableListOf<KMPHaversineDebugInfo>()
    private var waitForAuthJob: Job? = null

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