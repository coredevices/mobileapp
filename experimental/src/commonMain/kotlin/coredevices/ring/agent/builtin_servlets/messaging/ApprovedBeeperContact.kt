package coredevices.ring.agent.builtin_servlets.messaging

import kotlinx.serialization.Serializable

@Serializable
data class ApprovedBeeperContact(
    val roomId: String,
    val name: String,
    val nickname: String? = null
)
