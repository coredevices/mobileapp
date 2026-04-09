package coredevices.util

import android.content.Context
import org.koin.mp.KoinPlatform

internal actual fun loadModelBlob(): ByteArray {
    KoinPlatform.getKoin().get<Context>().assets.open("krisp-bvc-o-pro-v3.kef").use {
        return it.readBytes()
    }
}