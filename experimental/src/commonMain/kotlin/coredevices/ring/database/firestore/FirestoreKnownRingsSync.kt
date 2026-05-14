package coredevices.ring.database.firestore

import co.touchlab.kermit.Logger
import coredevices.libindex.LibIndex
import coredevices.libindex.device.InterviewedIndexDevice
import coredevices.libindex.device.KnownIndexDevice
import coredevices.ring.data.entity.firestore.FirestoreKnownRing
import coredevices.ring.database.firestore.dao.FirestoreKnownRingsDao
import coredevices.ring.database.firestore.dao.documentId
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

interface FirestoreKnownRingsSync {
    fun init()
}

class FirestoreKnownRingsSyncImpl(
    private val dao: FirestoreKnownRingsDao,
    private val libIndex: LibIndex,
) : FirestoreKnownRingsSync {
    companion object {
        private val logger = Logger.withTag("FirestoreKnownRingsSync")
    }
    private val lastSynced = mutableMapOf<String, FirestoreKnownRing>()
    override fun init() {
        GlobalScope.launch {
            Firebase.auth.authStateChanged.collectLatest { user ->
                if (user == null) {
                    lastSynced.clear()
                    return@collectLatest
                }
                try {
                    lastSynced.clear()
                    lastSynced.putAll(dao.getAll())
                    logger.d { "Loaded ${lastSynced.size} existing watches from Firestore" }
                } catch (e: Throwable) {
                    logger.w(e) { "failed to load existing watches from Firestore" }
                }

                libIndex.rings
                    .catch { e -> logger.w(e) { "error in rings flow" } }
                    .collect { rings ->
                        val snapshot = rings
                            .filterIsInstance<InterviewedIndexDevice>()
                            .associate { ring ->
                                val doc = FirestoreKnownRing(
                                    mac = ring.mac,
                                    serial = ring.serialNumber,
                                    runningFwVersion = ring.firmwareVersion,
                                )
                                doc.documentId() to doc
                            }

                        for ((id, new) in snapshot) {
                            if (lastSynced[id] == new) continue
                            try {
                                dao.set(new)
                                lastSynced[id] = new
                            } catch (e: Throwable) {
                                logger.w(e) { "failed to write known ring ${new.documentId()}" }
                            }
                        }
                    }
            }
        }
    }
}