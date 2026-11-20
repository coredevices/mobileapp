package coredevices.ring.reminders

import co.touchlab.kermit.Logger
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.util.CoreConfigHolder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

@Serializable
internal data class RemindersWidgetSnapshot(val enabled: Boolean, val reminders: List<Row>) {
    @Serializable
    data class Row(
        val id: String,
        val title: String,
        val dueAtMillis: Long?,
        val done: Boolean,
        val deepLink: String,
    )
}

/** Feeds the iOS reminders widget: Swift writes each snapshot to the App Group and reloads WidgetKit. */
class RemindersWidgetPublisher(
    private val itemRepository: ItemRepository,
    private val coreConfigHolder: CoreConfigHolder,
    private val reminderCompleter: ReminderCompleter,
) {
    private val logger = Logger.withTag("RemindersWidgetPublisher")
    private val lingeringIds = MutableStateFlow(emptySet<String>())
    private val published = MutableStateFlow<RemindersWidgetSnapshot?>(null)

    fun start() {
        combine(
            itemRepository.getAllFlow(),
            lingeringIds,
            coreConfigHolder.config.map { it.enableIndex }.distinctUntilChanged(),
        ) { items, lingering, enabled ->
            RemindersWidgetSnapshot(
                enabled = enabled,
                reminders = if (!enabled) emptyList() else upcomingReminders(items, lingering).map {
                    RemindersWidgetSnapshot.Row(
                        id = it.firestoreId,
                        title = it.title,
                        dueAtMillis = it.dueAt?.toEpochMilliseconds(),
                        done = it.done,
                        deepLink = RingRoutes.objectDeepLink(it.firestoreId),
                    )
                },
            )
        }
            .distinctUntilChanged()
            .onEach { snapshot ->
                RemindersWidgetBridge.publish?.invoke(Json.encodeToString(snapshot))
                published.value = snapshot
            }
            .launchIn(GlobalScope)
    }

    /**
     * Called from the widget's check button. Keeps the done row in the snapshot for [DONE_LINGER]; the
     * widget's own timeline hides it 2s after it first draws it. [onMarked] runs once the done row is
     * published, before the linger: WidgetKit only redraws after the intent returns.
     */
    suspend fun markDone(itemId: String, onMarked: () -> Unit) {
        lingeringIds.update { it + itemId }
        try {
            try {
                if (reminderCompleter.markDone(itemId)) {
                    withTimeoutOrNull(PUBLISH_TIMEOUT) {
                        published.first { snapshot -> snapshot?.reminders?.any { it.id == itemId && it.done } == true }
                    }
                } else {
                    logger.w { "Widget: reminder $itemId not completed" }
                }
            } finally {
                onMarked()
            }
            delay(DONE_LINGER)
        } finally {
            lingeringIds.update { it - itemId }
        }
    }

    companion object {
        // Longer than WidgetKit's post-intent redraw delay (~3s), so the redraw still sees the done row.
        private val DONE_LINGER = 10.seconds
        private val PUBLISH_TIMEOUT = 2.seconds
    }
}

/** Set from Swift (via IOSDelegate) before the app initialises the Ring module. */
object RemindersWidgetBridge {
    var publish: ((String) -> Unit)? = null
}
