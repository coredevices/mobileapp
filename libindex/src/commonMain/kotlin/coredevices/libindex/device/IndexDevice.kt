package coredevices.libindex.device

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

sealed interface IndexDevice {
    val identifier: IndexIdentifier
    val name: String
}

expect fun String.toPlatformAddress(): String

class IndexIdentifier(
    private val identifier: String
) : KoinComponent {
    val asString: String
        get() = identifier

    val asPlatformAddress: String = identifier.toPlatformAddress()

    companion object {
        fun fromPlatformAddress(address: String): IndexIdentifier {
            return IndexIdentifier(address.replace(":", ""))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val otherId = when (other) {
            is String -> other
            is IndexIdentifier -> other.identifier
            else -> return false
        }

        return identifier.equals(otherId, ignoreCase = true)
    }
}

interface KnownIndexDevice: IndexDevice {
    fun remove()
}

sealed interface IndexPairingState {
    object NotPaired : IndexPairingState
    object Pairing : IndexPairingState
    data class Error(val error: IndexPairingResult) : IndexPairingState
}

interface DiscoveredIndexDevice: IndexDevice {
    val rssi: Int
    val pairingState: IndexPairingState
    suspend fun pair(): IndexPairingResult
}

interface InterviewedIndexDevice: KnownIndexDevice {
    val firmwareVersion: String
    val serialNumber: String
    val mac: String
    val updating: Boolean
}