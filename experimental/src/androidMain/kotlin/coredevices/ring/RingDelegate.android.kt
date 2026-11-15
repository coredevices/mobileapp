package coredevices.ring

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.glance.appwidget.updateAll
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.HackyPermissionRequesterProvider
import coredevices.ring.database.firestore.FirestoreKnownRingsSync
import coredevices.ring.database.firestore.dao.FirestoreRecordingsDao
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.glance.RemindersWidget
import coredevices.ring.glance.RemindersWidgetReceiver
import coredevices.ring.glance.VoiceWidgetReceiver
import coredevices.ring.service.RingBackgroundDiagnostics
import coredevices.ring.service.RingPresenceObserver
import coredevices.util.CoreConfigHolder
import coredevices.util.Permission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

actual class RingDelegate(
    private val context: Context,
    private val permissionRequester: HackyPermissionRequesterProvider,
    private val coreConfigHolder: CoreConfigHolder,
    private val recordingsDao: FirestoreRecordingsDao,
    private val settings: Settings,
    private val firestoreKnownRingsSync: FirestoreKnownRingsSync,
    private val ringPresenceObserver: RingPresenceObserver,
    private val itemRepository: ItemRepository,
) {
    private val logger = Logger.withTag("RingDelegate")
    private val backgroundDiagnostics = RingBackgroundDiagnostics(context)

    actual fun appInit() {
        backgroundDiagnostics.start()
        ringPresenceObserver.start()
        updateRemindersWidget()
    }

    actual fun requiredRuntimePermissions(): Set<Permission> = buildSet {
        addAll(setOf(
            Permission.RecordAudio,
            Permission.PostNotifications,
            Permission.Bluetooth,
            Permission.ExternalStorage,
            Permission.SetAlarms,
        ))
        if (isBeeperAvailable()) {
            add(Permission.Beeper)
        }
    }

    /**
     * Called by activity onCreate / didFinishLaunching to initialize the Ring module.
     */
    actual suspend fun init() {
        listenForUserPresent(recordingsDao, coreConfigHolder, settings)
        firestoreKnownRingsSync.init()
        monitorIndexComponents()
        //enableWidget(context)
    }

    actual fun onBackgroundSync() {
        // No-op: ring scanning runs continuously in a foreground service on Android.
    }

    actual fun restartPreemptiveTransfer() {
        // No-op: the pre-emptive transfer loop is iOS-only behaviour.
    }

    /** Keeps the Index share-sheet targets and widget (disabled by default in the manifest) in sync
     *  with CoreConfig.enableIndex so they only show when Index is enabled. */
    private fun monitorIndexComponents() {
        val components = listOf(
            ShareToIndexNoteActivity::class.java,
            ShareToIndexReminderActivity::class.java,
            RemindersWidgetReceiver::class.java,
        )
        coreConfigHolder.config
            .map { it.enableIndex }
            .distinctUntilChanged()
            .onEach { enabled ->
                logger.d { "Setting Index components enabled=$enabled" }
                val state = if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                components.forEach {
                    context.packageManager.setComponentEnabledSetting(
                        ComponentName(context, it),
                        state,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
            .launchIn(GlobalScope)
    }

    // Glance only re-reads the flow while its session is alive; this restarts it after.
    private fun updateRemindersWidget() {
        RemindersWidget.reminders(itemRepository)
            .distinctUntilChanged()
            .onEach {
                try {
                    RemindersWidget().updateAll(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(e) { "Reminders widget update failed" }
                }
            }
            .launchIn(GlobalScope)
    }
}

fun enableWidget(context: Context) {
    val componentName = ComponentName(
        context,
        VoiceWidgetReceiver::class.java
    ) // Replace YourWidgetReceiver::class.java with your actual receiver class
    context.packageManager.setComponentEnabledSetting(
        componentName,
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP
    )
}
