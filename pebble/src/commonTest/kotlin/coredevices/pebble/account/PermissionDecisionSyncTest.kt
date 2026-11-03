package coredevices.pebble.account

import io.rebble.libpebblecommon.database.entity.LockerAppPermission
import io.rebble.libpebblecommon.permissions.PermissionDecisionRecord
import io.rebble.libpebblecommon.permissions.readFilesKey
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class PermissionDecisionSyncTest {
    private val app = Uuid.random()

    private fun row(permission: String, granted: Boolean?, decidedAtMs: Long?) =
        LockerAppPermission(app, permission, granted, requestedAtMs = 0, decidedAtMs = decidedAtMs)

    @Test
    fun onlyDecisionsTheEntryLacksOrHoldsOlderAreUploaded() {
        val local = listOf(
            row(PluginPermissions.CALENDAR, granted = true, decidedAtMs = 2_000),
            row(PluginPermissions.LOCATION, granted = false, decidedAtMs = 2_000),
            row(PluginPermissions.TIMELINE, granted = true, decidedAtMs = 1_000),
            row(PluginPermissions.CONTACTS, granted = null, decidedAtMs = null),
        )
        val remote = mapOf(
            PluginPermissions.LOCATION to PermissionDecisionRecord(granted = true, decidedAtMs = 1_000),
            PluginPermissions.TIMELINE to PermissionDecisionRecord(granted = false, decidedAtMs = 3_000),
        )
        assertEquals(
            mapOf(
                PluginPermissions.CALENDAR to PermissionDecisionRecord(true, 2_000),
                PluginPermissions.LOCATION to PermissionDecisionRecord(false, 2_000),
            ),
            decisionsToUpload(local, remote),
        )
    }

    @Test
    fun deviceSpecificGrantsStayOnTheDevice() {
        val local = listOf(
            row(readFilesKey("content://tree/notes"), granted = true, decidedAtMs = 1_000),
            row(PluginPermissions.LEGACY_PEBBLEKIT, granted = false, decidedAtMs = 1_000),
        )
        assertEquals(emptyMap(), decisionsToUpload(local, remote = null))
    }
}

class NewerDecisionsTest {
    private val app = Uuid.random()

    private fun held(permission: String, decidedAtMs: Long) =
        LockerAppPermission(app, permission, granted = true, requestedAtMs = 0, decidedAtMs = decidedAtMs)

    @Test
    fun ourOwnUploadEchoedBackIsntNewer() {
        val remote = mapOf(app to mapOf(PluginPermissions.CALENDAR to PermissionDecisionRecord(true, 2_000)))
        assertEquals(false, hasNewerDecisions(remote, listOf(held(PluginPermissions.CALENDAR, 2_000))))
    }

    @Test
    fun aLaterOrMissingDecisionFromElsewhereIs() {
        val later = mapOf(app to mapOf(PluginPermissions.CALENDAR to PermissionDecisionRecord(false, 3_000)))
        assertEquals(true, hasNewerDecisions(later, listOf(held(PluginPermissions.CALENDAR, 2_000))))
        val missing = mapOf(app to mapOf(PluginPermissions.LOCATION to PermissionDecisionRecord(true, 1_000)))
        assertEquals(true, hasNewerDecisions(missing, listOf(held(PluginPermissions.CALENDAR, 2_000))))
    }
}
