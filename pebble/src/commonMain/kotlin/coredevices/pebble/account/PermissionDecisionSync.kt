package coredevices.pebble.account

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.entity.LockerAppPermission
import io.rebble.libpebblecommon.permissions.PermissionDecisionRecord
import io.rebble.libpebblecommon.permissions.fileScopeOf
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Puts this device's permission decisions on the user's Firestore locker entries, so they follow
 * the app to a new device. Locker sync brings others' decisions down (`FirestoreLocker`); newest wins.
 */
class PermissionDecisionSync(
    private val firestoreLocker: FirestoreLocker,
    private val dao: FirestoreLockerDao,
    private val libPebble: LibPebble,
) {
    private val logger = Logger.withTag("PermissionDecisionSync")

    @OptIn(FlowPreview::class)
    fun init() {
        GlobalScope.launch(Dispatchers.Default) {
            // Rows change whenever a permission is used; only the decisions themselves matter here.
            val decisions = libPebble.permissionDecisions()
                .map { rows -> rows.map { it.copy(requestedAtMs = 0, lastUsedAtMs = null, seenAtMs = null) } }
                .distinctUntilChanged()
            combine(decisions, firestoreLocker.locker.filterNotNull()) { rows, entries -> rows to entries }
                .debounce(2.seconds)
                .collect { (rows, entries) ->
                    val uid = Firebase.auth.currentUser?.uid ?: return@collect
                    val byApp = rows.groupBy { it.appUuid }
                    entries.forEach { entry ->
                        val upload = decisionsToUpload(byApp[entry.uuid].orEmpty(), entry.permissions)
                        if (upload.isEmpty()) return@forEach
                        try {
                            dao.mergePermissionsForUser(uid, entry, upload)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logger.w(e) { "Couldn't sync permissions for ${entry.uuid}" }
                        }
                    }
                }
        }
    }
}

/** File grants point at one device's files; Legacy PebbleKit is Android-only. */
internal fun isSynced(key: String) = fileScopeOf(key) == null && key != PluginPermissions.LEGACY_PEBBLEKIT

/** This device's decisions that the locker entry doesn't have yet, or has an older one of. */
internal fun decisionsToUpload(
    local: List<LockerAppPermission>,
    remote: Map<String, PermissionDecisionRecord>?,
): Map<String, PermissionDecisionRecord> = local
    .filter { isSynced(it.permission) }
    .mapNotNull { row ->
        val granted = row.granted ?: return@mapNotNull null
        val decidedAt = row.decidedAtMs ?: return@mapNotNull null
        val held = remote?.get(row.permission)
        if (held != null && held.decidedAtMs >= decidedAt) null
        else row.permission to PermissionDecisionRecord(granted, decidedAt)
    }
    .toMap()
