@file:OptIn(ExperimentalTime::class)

package coredevices.ring.reminders

import co.touchlab.kermit.Logger
import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.ring.agent.builtin_servlets.reminders.BuiltInReminderIntegration
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.reminders.LocalReminderData
import coredevices.ring.database.room.dao.LocalReminderDao
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.util.PermissionRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Keeps this device's OS alarms in step with the synced reminder feed items. A reminder is
 * identified by its feed item's global id, so one created on another device or the web app arrives
 * here as a plain item; this arms an alarm for it. When an item is completed or deleted elsewhere
 * (a pull, which bypasses [ItemRepository.setItem]'s local hooks), this cancels the stale alarm.
 * Local create/complete already act instantly via those hooks — reconciling is the cross-device
 * backstop, and idempotent, so overlap is a no-op. The single flow collector serializes runs.
 */
class ReminderReconciler(
    private val itemRepository: ItemRepository,
    private val localReminderDao: LocalReminderDao,
    private val reminders: BuiltInReminderIntegration,
    private val permissionRequester: PermissionRequester,
    private val scope: RecordingBackgroundScope,
) {
    @OptIn(FlowPreview::class)
    fun start() {
        // Reconcile on the initial emission (startup), every later Room change, and whenever the
        // set of missing permissions changes — a just-granted alarm/notification permission lets a
        // previously-skipped reminder finally arm.
        merge(
            itemRepository.getAllForSyncFlow().map { },
            permissionRequester.missingPermissions.map { },  // StateFlow already emits only on change
        )
            .debounce(500)
            .onEach { reconcile() }
            .flowOn(Dispatchers.IO)
            .catch { log.e(it) { "reminder reconcile observer error" } }
            .launchIn(scope)
    }

    private var purgedLegacy = false

    suspend fun reconcile() {
        if (!purgedLegacy) {
            runCatching { reminders.purgeLegacySchedules() }
                .onFailure { log.e(it) { "legacy reminder purge failed" } }
            purgedLegacy = true
        }
        val all = itemRepository.getAllIncludingDeleted()
        val views = all.mapNotNull { it.toReminderView() }
        val activeItemIds = all.filterNot { it.deleted }.mapTo(mutableSetOf()) { it.firestoreId }
        val rows = localReminderDao.getAllReminders()
        var armed = 0
        var disarmed = 0
        for (action in computeActions(views, activeItemIds, rows, Clock.System.now())) {
            when (action) {
                is ReminderAction.Arm ->
                    runCatching { reminders.syncReminder(action.itemId, action.message, action.time, action.notifyBeforeMillis?.milliseconds) }
                        .onSuccess { armed++ }
                        .onFailure { log.e(it) { "Failed to arm reminder ${action.itemId}" } }
                is ReminderAction.Disarm ->
                    runCatching { reminders.cancelReminder(action.itemId) }
                        .onSuccess { disarmed++ }
                        .onFailure { log.e(it) { "Failed to disarm reminder ${action.itemId}" } }
            }
        }
        log.i { "reconcile: ${views.size} reminder items, ${rows.size} rows; armed=$armed disarmed=$disarmed" }
    }

    private fun CachedItem.toReminderView(): ReminderView? {
        val meta = metadata as? ItemMetadata.Reminder ?: return null
        return ReminderView(firestoreId, title, dueAt, meta.notifyBeforeMillis, done, deleted)
    }

    companion object {
        private val log = Logger.withTag("ReminderReconciler")
    }
}

internal data class ReminderView(
    val itemId: String,
    val message: String,
    val dueAt: Instant?,
    val notifyBeforeMillis: Long?,
    val done: Boolean,
    val deleted: Boolean,
)

internal sealed interface ReminderAction {
    /** (Re)schedule the alarm for [itemId] to [time]; a null or past [time] cancels it. */
    data class Arm(val itemId: String, val message: String, val time: Instant?, val notifyBeforeMillis: Long?) : ReminderAction
    /** Cancel the alarm and drop the ledger row (the reminder was completed, deleted, or removed). */
    data class Disarm(val itemId: String) : ReminderAction
}

/**
 * Diffs synced reminder items against this device's alarm ledger.
 *
 * - An active reminder whose ledger row doesn't match its due time / lead / message is (re)synced —
 *   [BuiltInReminderIntegration.syncReminder] schedules a future time and cancels a null/past one.
 *   A row that already matches is left untouched, so a spent alarm's showing notification survives.
 * - A row is disarmed when its reminder is completed/deleted, or its item is gone entirely.
 *   [activeItemIds] (all non-deleted item ids, any type) lets a list note that carries a local alarm
 *   keep it: the row's item still exists, so it isn't treated as "gone".
 */
internal fun computeActions(
    reminders: List<ReminderView>,
    activeItemIds: Set<String>,
    rows: List<LocalReminderData>,
    now: Instant,
): List<ReminderAction> {
    val reminderById = reminders.associateBy { it.itemId }
    val rowById = rows.associateBy { it.itemId }
    val actions = mutableListOf<ReminderAction>()

    for (r in reminders) {
        if (r.done || r.deleted) continue
        val row = rowById[r.itemId]
        val matches = row != null && row.time == r.dueAt &&
            row.notifyBeforeMillis == r.notifyBeforeMillis && row.message == r.message
        if (matches) continue
        val future = r.dueAt != null && r.dueAt > now
        // Act only when there's something to schedule (future) or a stale row to cancel/update.
        if (future || row != null) {
            actions += ReminderAction.Arm(r.itemId, r.message, r.dueAt, r.notifyBeforeMillis)
        }
    }
    for (row in rows) {
        val r = reminderById[row.itemId]
        val gone = row.itemId !in activeItemIds
        val finished = r != null && (r.done || r.deleted)
        if (gone || finished) actions += ReminderAction.Disarm(row.itemId)
    }
    return actions
}
