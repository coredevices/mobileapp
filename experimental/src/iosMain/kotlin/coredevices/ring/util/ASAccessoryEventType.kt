package coredevices.ring.util

import platform.AccessorySetupKit.ASAccessoryEventTypeAccessoryAdded
import platform.AccessorySetupKit.ASAccessoryEventTypeAccessoryChanged
import platform.AccessorySetupKit.ASAccessoryEventTypeAccessoryRemoved
import platform.AccessorySetupKit.ASAccessoryEventTypeActivated
import platform.AccessorySetupKit.ASAccessoryEventTypeInvalidated
import platform.AccessorySetupKit.ASAccessoryEventTypeMigrationComplete
import platform.AccessorySetupKit.ASAccessoryEventTypePickerDidDismiss
import platform.AccessorySetupKit.ASAccessoryEventTypePickerDidPresent
import platform.AccessorySetupKit.ASAccessoryEventTypePickerSetupBridging
import platform.AccessorySetupKit.ASAccessoryEventTypePickerSetupFailed
import platform.AccessorySetupKit.ASAccessoryEventTypePickerSetupPairing
import platform.AccessorySetupKit.ASAccessoryEventTypePickerSetupRename
import platform.AccessorySetupKit.ASAccessoryEventTypeUnknown

enum class ASAccessoryEventType(val value: Long) {
    Activated(ASAccessoryEventTypeActivated),
    Invalidated(ASAccessoryEventTypeInvalidated),
    AccessoryAdded(ASAccessoryEventTypeAccessoryAdded),
    AccessoryChanged(ASAccessoryEventTypeAccessoryChanged),
    AccessoryRemoved(ASAccessoryEventTypeAccessoryRemoved),
    PickerDidDismiss(ASAccessoryEventTypePickerDidDismiss),
    PickerDidPresent(ASAccessoryEventTypePickerDidPresent),
    PickerSetupBridging(ASAccessoryEventTypePickerSetupBridging),
    PickerSetupFailed(ASAccessoryEventTypePickerSetupFailed),
    PickerSetupPairing(ASAccessoryEventTypePickerSetupPairing),
    PickerSetupRename(ASAccessoryEventTypePickerSetupRename),
    MigrationComplete(ASAccessoryEventTypeMigrationComplete),
    Unknown(ASAccessoryEventTypeUnknown);

    companion object {
        fun fromValue(value: Long): ASAccessoryEventType {
            return entries.firstOrNull { it.value == value } ?: Unknown
        }
    }
}