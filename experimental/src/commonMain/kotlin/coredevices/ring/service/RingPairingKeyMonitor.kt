package coredevices.ring.service

import co.touchlab.kermit.Logger
import coredevices.libindex.database.BasePreferences

/**
 * Tracks the LTK hash the paired ring advertises (firmware 3.86+). The hash is only stored after
 * an encrypted link succeeds, so it always reflects a key the phone's bond matched; a later
 * advertisement carrying a different hash means the ring has dropped its pairing.
 */
class RingPairingKeyMonitor(private val prefs: BasePreferences) {
    companion object {
        private val logger = Logger.withTag("RingPairingKeyMonitor")
        private const val MIN_FIRMWARE_MAJOR = 3
        private const val MIN_FIRMWARE_MINOR = 86

        internal fun advertisesPairingKeyHash(firmwareVersion: String): Boolean {
            val parts = firmwareVersion.split('.')
            val major = parts.getOrNull(0)?.toIntOrNull() ?: return false
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: return false
            return major > MIN_FIRMWARE_MAJOR || (major == MIN_FIRMWARE_MAJOR && minor >= MIN_FIRMWARE_MINOR)
        }
    }

    private var reportedMismatch: Int? = null

    fun onEncryptedLinkConfirmed(firmwareVersion: String, hash: Int?) {
        if (hash == null || !advertisesPairingKeyHash(firmwareVersion)) return
        reportedMismatch = null
        if (prefs.ringPairingKeyHash.value != hash) {
            logger.i { "Storing pairing key hash ${hash.toPairingKeyHex()} (firmware $firmwareVersion)" }
            prefs.setRingPairingKeyHash(hash)
        }
    }

    /** True once per run of advertisements whose [hash] contradicts the stored one. */
    fun onAdvertisement(firmwareVersion: String, hash: Int?): Boolean {
        val expected = prefs.ringPairingKeyHash.value ?: return false
        if (hash == null || !advertisesPairingKeyHash(firmwareVersion)) return false
        if (hash == expected) {
            reportedMismatch = null
            return false
        }
        if (reportedMismatch == hash) return false
        reportedMismatch = hash
        logger.w {
            "Ring advertises pairing key hash ${hash.toPairingKeyHex()} but ${expected.toPairingKeyHex()} was expected; its pairing has likely been reset"
        }
        return true
    }
}

internal fun Int.toPairingKeyHex(): String = "0x" + toString(16).padStart(4, '0')
