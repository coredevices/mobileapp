package coredevices.util.models

import coredevices.util.Platform
import platform.CoreML.MLAllComputeDevices
import platform.CoreML.MLComputeDeviceProtocolProtocol
import platform.CoreML.MLNeuralEngineComputeDevice

actual fun Platform.supportsNPU(): Boolean {
    return MLAllComputeDevices().firstOrNull { it is MLNeuralEngineComputeDevice } != null
}

actual fun Platform.supportsHeavyCPU(): Boolean {
    return false
}