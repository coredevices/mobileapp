package coredevices.ring.service

import android.companion.CompanionDeviceManager
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.os.Build
import androidx.annotation.RequiresApi
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Bound by the system when the paired ring comes into range; see [RingPresenceObserver]. */
@RequiresApi(Build.VERSION_CODES.S)
class RingCompanionDeviceService : CompanionDeviceService(), KoinComponent {
    private val observer: RingPresenceObserver by inject()

    @Deprecated("Deprecated in Java")
    override fun onDeviceAppeared(address: String) {
        observer.onRingAppeared(address)
    }

    @Deprecated("Deprecated in Java")
    override fun onDeviceDisappeared(address: String) {
        observer.onRingDisappeared(address)
    }

    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
        val address = associationAddress(event.associationId) ?: return
        when (event.event) {
            DevicePresenceEvent.EVENT_BLE_APPEARED -> observer.onRingAppeared(address)
            DevicePresenceEvent.EVENT_BLE_DISAPPEARED -> observer.onRingDisappeared(address)
            else -> {}
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun associationAddress(associationId: Int): String? =
        getSystemService(CompanionDeviceManager::class.java)
            ?.myAssociations
            ?.firstOrNull { it.id == associationId }
            ?.deviceMacAddress
            ?.toString()
}
