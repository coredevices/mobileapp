package coredevices.ring.service

import android.content.Context
import kotlinx.io.files.Path
import org.koin.mp.KoinPlatform

actual fun getCachedFirmwareDirectory(): Path {
    val context = KoinPlatform.getKoin().get<Context>()
    val dir = context.noBackupFilesDir.resolve("ring_firmware")
    dir.mkdirs()
    return Path(dir.absolutePath)
}