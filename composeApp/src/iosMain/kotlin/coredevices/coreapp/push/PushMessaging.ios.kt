package coredevices.coreapp.push

import platform.UIKit.UIDevice
import PlatformContext

actual fun PlatformContext.getDeviceId(): String {
    return UIDevice.currentDevice.identifierForVendor?.UUIDString ?: error("Could not get device identifier")
}

