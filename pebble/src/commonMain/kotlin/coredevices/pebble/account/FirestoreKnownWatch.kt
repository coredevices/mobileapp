package coredevices.pebble.account

import kotlinx.serialization.Serializable

@Serializable
data class FirestoreKnownWatch(
    val serial: String,
    val lastConnectedMs: Long,
    val runningFwVersion: String,
    val connectGoal: Boolean,
    val watchType: String,
    val color: String?,
    val nickname: String?,
)
