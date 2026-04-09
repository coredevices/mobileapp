package coredevices.ring.util

import coredevices.haversine.KMPHaversineSatellite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/*
private sealed class ASAccessoryEvent {
    sealed class PickerEvent : ASAccessoryEvent()
    data class PickedAccessory(val accessory: ASAccessory) : PickerEvent()
    data object PickerCanceled : PickerEvent()

    data object AccessoryChanged : ASAccessoryEvent()
}

actual class RingCompanionDeviceManager actual constructor(private val scope: CoroutineScope){
    private val logger = Logger.withTag("RingCompanionDeviceManager")
    private val session = ASAccessorySession()
    private val sessionEvents = callbackFlow {
        var pickedAccessory: ASAccessory? = null
        session.activateWithQueue(dispatch_get_main_queue()) {
            val type = it?.eventType?.let { ASAccessoryEventType.fromValue(it) }
            when (type) {
                ASAccessoryEventType.Activated -> {
                    logger.d { "ASKit Session Activated" }
                    trySend(ASAccessoryEvent.AccessoryChanged)
                }
                ASAccessoryEventType.Invalidated -> {
                    logger.d { "ASKit Session Invalidated" }
                }
                ASAccessoryEventType.AccessoryChanged -> {
                    logger.d { "Accessory Changed" }
                    trySend(ASAccessoryEvent.AccessoryChanged)
                }
                ASAccessoryEventType.AccessoryAdded -> {
                    it.accessory?.let { accessory ->
                        logger.d { "Accessory Added: ${accessory.displayName}" }
                        pickedAccessory = accessory
                        trySend(ASAccessoryEvent.AccessoryChanged)
                    } ?: run {
                        logger.w { "Accessory Added event with no accessory" }
                    }
                }
                ASAccessoryEventType.AccessoryRemoved -> {
                    it.accessory?.let { accessory ->
                        logger.d { "Accessory Removed: ${accessory.displayName}" }
                    } ?: run {
                        logger.w { "Accessory Removed event with no accessory" }
                    }
                    trySend(ASAccessoryEvent.AccessoryChanged)
                }
                ASAccessoryEventType.PickerDidDismiss -> {
                    logger.d { "Picker Dismissed" }
                    pickedAccessory?.let { accessory ->
                        logger.i { "Picked Accessory: ${accessory.displayName}" }
                        trySend(ASAccessoryEvent.PickedAccessory(accessory))
                    } ?: run {
                        logger.i { "Picker dismissed without selecting an accessory" }
                        trySend(ASAccessoryEvent.PickerCanceled)
                    }
                    pickedAccessory = null
                }
                else -> {
                    logger.w { "Unhandled ASKit Event: $type" }
                }
            }
        }
        awaitClose {
            logger.d { "Closing ASKit Session" }
            session.invalidate()
        }
    }.shareIn(scope, SharingStarted.Lazily)

    init {
        scope.launch {
            sessionEvents.filterIsInstance<ASAccessoryEvent.AccessoryChanged>().collect {
                logger.d { "Refreshing associations due to accessory change" }
                refreshAssociations()
            }
        }
    }

    private fun refreshAssociations() {
        _associations.value = session.accessories.mapNotNull {
            val acc = it as ASAccessory
            acc.bluetoothIdentifier?.UUIDString?.let { DeviceAssociation(it, acc.displayName ?: "Unknown") }
        }
    }

    actual suspend fun unregister(satellite: KMPHaversineSatellite) {
        val acc = session.accessories.map { it as ASAccessory }.firstOrNull {
            it.bluetoothIdentifier?.UUIDString == satellite.id
        }
        suspendCancellableCoroutine { cont ->
            acc?.let {
                session.removeAccessory(it) { error ->
                    if (error != null) {
                        cont.resumeWithException(Exception(error.localizedDescription))
                    } else {
                        logger.i { "Unregistered accessory: ${it.displayName}" }
                        cont.resume(Unit)
                    }
                }
            }
        }
        refreshAssociations()
    }

    actual suspend fun unregisterAll() {
        session.accessories.forEach { accessory ->
            suspendCancellableCoroutine { cont ->
                session.removeAccessory(accessory as ASAccessory) { error ->
                    if (error != null) {
                        cont.resumeWithException(Exception(error.localizedDescription))
                    } else {
                        logger.i { "Unregistered accessory: ${accessory.displayName}" }
                        cont.resume(Unit)
                    }
                }
            }
        }
        refreshAssociations()
    }

    actual suspend fun openPairingPicker(): CompanionRegisterResult {
        val uuid = CBUUID.UUIDWithString(ringServiceUuid16)
        val descriptor = ASDiscoveryDescriptor().apply {
            bluetoothServiceUUID = uuid as objcnames.classes.CBUUID
            bluetoothNameSubstring = "CoreRing"
        }
        val ringDisplayItem = ASPickerDisplayItem(
            "Core Ring",
            UIImage.systemImageNamed("circle") ?: UIImage(),
            descriptor
        )
        val pickedDeferred = scope.async {
            sessionEvents.filterIsInstance<ASAccessoryEvent.PickerEvent>().first()
        }
        suspendCancellableCoroutine { cont ->
            session.showPickerForDisplayItems(listOf(ringDisplayItem)) { e ->
                e?.let {
                    cont.resumeWithException(Exception(it.localizedDescription))
                } ?: cont.resume(Unit)
            }
        }
        return when (val event = pickedDeferred.await()) {
            is ASAccessoryEvent.PickedAccessory -> {
                CompanionRegisterResult.Success(event.accessory.bluetoothIdentifier!!.UUIDString)
            }
            ASAccessoryEvent.PickerCanceled -> {
                CompanionRegisterResult.Failure("Picker was canceled")
            }
        }
    }

    private val _associations = MutableStateFlow<List<DeviceAssociation>>(emptyList())
    actual val associations: StateFlow<List<DeviceAssociation>> = _associations.asStateFlow()
}*/


actual class RingCompanionDeviceManager actual constructor(private val scope: CoroutineScope) {
    // This is a placeholder implementation for iOS, as the actual functionality is not implemented yet.
    // The real implementation would involve using Apple's ASKit to manage companion devices.

    actual suspend fun unregister(satellite: KMPHaversineSatellite) {

    }

    actual suspend fun unregisterAll() {

    }

    actual suspend fun openPairingPicker(): CompanionRegisterResult {
        TODO()
    }

    actual val associations: StateFlow<List<DeviceAssociation>> = MutableStateFlow(emptyList())
}