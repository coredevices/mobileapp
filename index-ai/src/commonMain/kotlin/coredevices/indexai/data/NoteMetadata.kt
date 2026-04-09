package coredevices.indexai.data

import kotlinx.serialization.Serializable

@Serializable
data class NoteMetadata(
    val locationText: String?,
    val locationCoordinates: Pair<Double, Double>?,
)