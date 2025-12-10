package coredevices.coreapp.ui.screens

import android.content.Context
import co.touchlab.kermit.Logger
import coredevices.coreapp.BugReportService
import org.koin.mp.KoinPlatform

actual fun startForegroundService() {
    try {
        val context = KoinPlatform.getKoin().get<Context>()
        BugReportService.startBugReport(context = context)
        true
    } catch (e: Exception) {
        Logger.e(e) { "couldn't start foreground service: ${e.message}" }
        false
    }
}

actual fun notifyState(message: String) {
    val context = KoinPlatform.getKoin().get<Context>()
    BugReportService.updateBugReport(context, message)
}

actual fun stopForegroundService() {
    val context = KoinPlatform.getKoin().get<Context>()
    BugReportService.stopBugReport(context)
}