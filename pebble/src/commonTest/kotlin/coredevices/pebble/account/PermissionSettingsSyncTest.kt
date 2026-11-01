package coredevices.pebble.account

import io.rebble.libpebblecommon.permissions.PermissionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PermissionSettingsSyncTest {
    private val default = PermissionSettings(PermissionPolicy.AskIfExfiltrating, askLegacyApps = false)
    private val strict = PermissionSettings(PermissionPolicy.AlwaysAsk, askLegacyApps = true)

    @Test
    fun aNewDeviceAdoptsTheAccountsSettingsRatherThanUploadingItsDefaults() {
        assertEquals(SyncStep.Adopt(strict), syncStep(remote = strict, local = default, synced = null))
    }

    @Test
    fun anAccountWithNoSettingsYetGetsThisDevices() {
        assertEquals(SyncStep.Upload(default), syncStep(remote = null, local = default, synced = null))
    }

    @Test
    fun aLocalChangeGoesUpAndSettlesOnceEchoedBack() {
        assertEquals(SyncStep.Upload(strict), syncStep(remote = default, local = strict, synced = default))
        assertNull(syncStep(remote = strict, local = strict, synced = strict))
    }

    @Test
    fun withPluginsOffTheAccountsSettingsAreClearedAndNotAdopted() {
        assertEquals(SyncStep.Clear, syncStep(remote = strict, local = null, synced = strict))
        assertNull(syncStep(remote = null, local = null, synced = null))
    }

    @Test
    fun turningPluginsBackOnUploadsThisDevicesSettings() {
        assertEquals(SyncStep.Upload(default), syncStep(remote = null, local = default, synced = null))
    }

    @Test
    fun aChangeFromAnotherDeviceIsAdopted() {
        assertEquals(SyncStep.Adopt(strict), syncStep(remote = strict, local = default, synced = default))
    }
}
