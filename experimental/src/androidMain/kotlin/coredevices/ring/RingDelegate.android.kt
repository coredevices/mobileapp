package coredevices.ring

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.glance.appwidget.updateAll
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.HackyPermissionRequesterProvider
import coredevices.ring.data.entity.room.indexfeed.recentNotesAndTodos
import coredevices.ring.data.entity.room.indexfeed.widgetRenderFingerprint
import coredevices.ring.database.firestore.FirestoreKnownRingsSync
import coredevices.ring.database.firestore.dao.FirestoreRecordingsDao
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.glance.IndexNotesWidget
import coredevices.ring.glance.VoiceWidgetReceiver
import coredevices.util.CoreConfigHolder
import coredevices.util.Permission
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
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
    private val itemRepo: ItemRepository,
) {
    private val logger = Logger.withTag("RingDelegate")

    init {
        // Registered at construction (Koin builds this during MainApplication.onCreate),
        // not in init(), which runs later behind the async weatherFetcher chain.
        monitorIndexNotesWidget()
    }

    /** What the widget renders; identical states don't trigger a refresh. */
    private data class WidgetRenderState(
        val fingerprint: List<Triple<String, String, String>>,
        val itemCount: Int,
        val uid: String?,
        val enableIndex: Boolean,
    )

    /** Pushes a refresh to [IndexNotesWidget] whenever its rendered content, the
     *  signed-in user, or the Index toggle changes. `updatePeriodMillis` in the
     *  provider XML is only a best-effort fallback. */
    @OptIn(FlowPreview::class)
    private fun monitorIndexNotesWidget() {
        val authUser = flow {
            emit(Firebase.auth.currentUser)
            Firebase.auth.authStateChanged.collect { emit(it) }
        }
        combine(
            itemRepo.getAllFlow().debounce(500), // coalesce write bursts; auth/config pass through
            authUser,
            coreConfigHolder.config.map { it.enableIndex }.distinctUntilChanged(),
        ) { items, user, enableIndex ->
            val rows = recentNotesAndTodos(items)
            WidgetRenderState(widgetRenderFingerprint(rows), rows.size, user?.uid, enableIndex)
        }
            .distinctUntilChanged()
            .onEach { state ->
                try {
                    IndexNotesWidget().updateAll(context)
                    logger.d { "Index widget refreshed: itemCount=${state.itemCount} enableIndex=${state.enableIndex}" }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(e) { "Index widget refresh failed" }
                }
            }
            .launchIn(GlobalScope)
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
        monitorIndexShareTargets()
        //enableWidget(context)
    }

    actual fun onBackgroundSync() {
        // No-op: ring scanning runs continuously in a foreground service on Android.
    }

    actual fun restartPreemptiveTransfer() {
        // No-op: the pre-emptive transfer loop is iOS-only behaviour.
    }

    /** Keeps the Index share-sheet targets (disabled by default in the manifest) in sync
     *  with CoreConfig.enableIndex so they only show when Index is enabled. */
    private fun monitorIndexShareTargets() {
        val shareTargets = listOf(
            ShareToIndexNoteActivity::class.java,
            ShareToIndexReminderActivity::class.java,
        )
        coreConfigHolder.config
            .map { it.enableIndex }
            .distinctUntilChanged()
            .onEach { enabled ->
                logger.d { "Setting Index share targets enabled=$enabled" }
                val state = if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                shareTargets.forEach {
                    context.packageManager.setComponentEnabledSetting(
                        ComponentName(context, it),
                        state,
                        PackageManager.DONT_KILL_APP,
                    )
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
