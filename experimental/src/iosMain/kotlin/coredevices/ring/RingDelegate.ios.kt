package coredevices.ring

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.ring.agent.builtin_servlets.reminders.IOSBuiltInReminderIntegration
import coredevices.ring.database.firestore.FirestoreKnownRingsSync
import coredevices.ring.database.firestore.dao.FirestoreRecordingsDao
import coredevices.ring.service.BackgroundRingService
import coredevices.util.CoreConfigHolder
import coredevices.util.Permission

actual class RingDelegate(
    private val backgroundRingService: BackgroundRingService,
    private val coreConfigHolder: CoreConfigHolder,
    private val recordingsDao: FirestoreRecordingsDao,
    private val settings: Settings,
    private val firestoreKnownRingsSync: FirestoreKnownRingsSync,
) {
    companion object {
        private val logger = Logger.withTag("RingDelegate")
    }
    /**
     * Called by activity onCreate / didFinishLaunching to initialize the Ring module.
     */
    actual suspend fun init() {
        try {
            IOSBuiltInReminderIntegration.ensureCategoryRegistered()
        } catch (e: Exception) {
            logger.e(e) { "Failed to register notification category for reminders" }
        }
        listenForUserPresent(recordingsDao, coreConfigHolder, settings)
        if (!backgroundRingService.isRunning.value) {
            backgroundRingService.startRingSyncJob()
        }
        firestoreKnownRingsSync.init()
    }

    actual fun requiredRuntimePermissions(): Set<Permission> {
        return setOf(Permission.Reminders)
    }

    actual fun onBackgroundSync() {
        // The pre-emptive transfer loop's coroutines were frozen while the app was suspended;
        // restart it so its launch phase makes an immediate transfer attempt.
        restartPreemptiveTransfer()
    }

    actual fun restartPreemptiveTransfer() {
        backgroundRingService.restartPreemptiveTransfer()
    }
}