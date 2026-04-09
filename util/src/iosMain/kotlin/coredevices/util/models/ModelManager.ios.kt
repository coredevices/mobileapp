package coredevices.util.models

import coredevices.util.Platform
import kotlinx.cinterop.useContents
import platform.CoreML.MLAllComputeDevices
import platform.CoreML.MLComputeDeviceProtocolProtocol
import platform.CoreML.MLNeuralEngineComputeDevice
import platform.Foundation.NSProcessInfo

actual fun Platform.supportsNPU(): Boolean {
    if (NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion } < 16) return false
    return MLAllComputeDevices().firstOrNull { it is MLNeuralEngineComputeDevice } != null
}

actual fun Platform.supportsHeavyCPU(): Boolean {
    return false
}