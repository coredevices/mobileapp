@file:OptIn(ExperimentalTime::class)

package coredevices.indexai.data.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.InstantComponentSerializer
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Firestore-shape document for a saved place a reminder can be armed at.
 * Stored at `places/{uid}/places/{placeId}`. Everything but [updatedAt] and [deleted] is
 * sensitive and moves into [encrypted] when encryption is on.
 */
@Serializable
data class GeofenceLocationDocument(
    @Serializable(with = InstantComponentSerializer::class)
    val updatedAt: Instant = Clock.System.now(),
    val name: String = "",
    val lat: Double = 0.0,
    val long: Double = 0.0,
    val radiusMeters: Double = 0.0,
    val address: String? = null,
    val deleted: Boolean = false,
    val encrypted: EncryptedEnvelope? = null,
)
