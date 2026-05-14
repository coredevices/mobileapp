package coredevices.ring.data.entity.firestore

import kotlinx.serialization.Serializable

@Serializable
data class FirestoreKnownRing(
    val mac: String,
    val serial: String?,
    val runningFwVersion: String,
)