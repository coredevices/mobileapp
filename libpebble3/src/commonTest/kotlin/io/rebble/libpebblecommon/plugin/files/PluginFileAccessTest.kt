package io.rebble.libpebblecommon.plugin.files

import io.rebble.libpebblecommon.WatchConfig
import io.rebble.libpebblecommon.permissions.APP
import io.rebble.libpebblecommon.permissions.FakePermissionDao
import io.rebble.libpebblecommon.permissions.PermissionDecision
import io.rebble.libpebblecommon.permissions.PermissionPolicy
import io.rebble.libpebblecommon.permissions.permissions
import io.rebble.libpebblecommon.permissions.readFilesKey
import io.rebble.libpebblecommon.permissions.writeFilesKey
import io.rebble.libpebblecommon.plugin.PluginPermissions
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class PluginFileAccessTest {
    private val other = Uuid.parse("00000000-0000-0000-0000-00000000a002")
    private val dao = FakePermissionDao()
    private val fileSystem = FakePluginFileSystem()
    private val permissions = permissions(dao)
    private val access = PluginFileAccess(permissions, dao, fileSystem)

    @Test
    fun aPickBecomesScopedPermissionRows() = runTest {
        fileSystem.dir("content://docs/tree/Vault")
        val handle = access.grantPluginFile(APP, "content://docs/tree/Vault", FileAccessMode.ReadWrite, "bookmark")
        assertEquals(FileHandleInfo("content://docs/tree/Vault", FileEntryKind.Directory, "Vault", FileAccessMode.ReadWrite), handle)
        // Write covers read, so a read-write pick is one row the user can withdraw.
        assertNull(dao.row(readFilesKey("content://docs/tree/Vault")))
        assertEquals(true, dao.row(writeFilesKey("content://docs/tree/Vault"))?.granted)
        assertEquals("bookmark", fileSystem.retained["content://docs/tree/Vault"])
        assertTrue(access.check(APP, "content://docs/tree/Vault", FileAccessMode.ReadWrite))
        assertTrue(access.check(APP, "content://docs/tree/Vault", FileAccessMode.Read))
    }

    @Test
    fun aReadPickGrantsNoWrite() = runTest {
        fileSystem.file("a.txt")
        access.grantPluginFile(APP, "a.txt", FileAccessMode.Read, "a.txt")
        assertNull(dao.row(writeFilesKey("a.txt")))
        assertTrue(access.check(APP, "a.txt", FileAccessMode.Read))
        assertFalse(access.check(APP, "a.txt", FileAccessMode.ReadWrite))
        assertEquals(FileAccessMode.Read, access.handleInfo(APP, "a.txt")?.mode)
    }

    @Test
    fun anUnreachablePickIsNotKept() = runTest {
        assertNull(access.grantPluginFile(APP, "gone", FileAccessMode.Read, "gone"))
        assertNull(dao.row(readFilesKey("gone")))
        assertEquals(listOf("gone"), fileSystem.released)
    }

    @Test
    fun handlesAreListedPerAppWithTheirMode() = runTest {
        fileSystem.dir("vault")
        fileSystem.file("a.txt")
        access.grantPluginFile(APP, "vault", FileAccessMode.ReadWrite, "vault")
        access.grantPluginFile(APP, "a.txt", FileAccessMode.Read, "a.txt")
        access.grantPluginFile(other, "a.txt", FileAccessMode.Read, "a.txt")
        assertEquals(
            listOf(
                FileHandleInfo("vault", FileEntryKind.Directory, "vault", FileAccessMode.ReadWrite),
                FileHandleInfo("a.txt", FileEntryKind.File, "a.txt", FileAccessMode.Read),
            ),
            access.pluginFileHandles(APP),
        )
        assertEquals(1, access.pluginFileHandles(other).size)
    }

    @Test
    fun theUsersDenialHidesTheHandle() = runTest {
        fileSystem.file("a.txt")
        access.grantPluginFile(APP, "a.txt", FileAccessMode.Read, "a.txt")
        permissions.setDecisions(APP, mapOf(readFilesKey("a.txt") to false))
        assertNull(access.handleInfo(APP, "a.txt"))
        assertEquals(emptyList(), access.pluginFileHandles(APP))
    }

    @Test
    fun denyingTheBarePermissionHidesEveryHandleUntilItIsAllowedAgain() = runTest {
        fileSystem.dir("vault")
        permissions.setDecisions(APP, mapOf(PluginPermissions.READ_FILES to true, PluginPermissions.WRITE_FILES to true))
        access.grantPluginFile(APP, "vault", FileAccessMode.ReadWrite, "vault")

        permissions.setDecisions(APP, mapOf(PluginPermissions.WRITE_FILES to false))
        assertEquals(FileAccessMode.Read, access.handleInfo(APP, "vault")?.mode)
        assertEquals(FileAccessMode.Read, access.pluginFileHandles(APP).single().mode)
        assertFalse(access.check(APP, "vault", FileAccessMode.ReadWrite))

        permissions.setDecisions(APP, mapOf(PluginPermissions.READ_FILES to false))
        assertNull(access.handleInfo(APP, "vault"))
        assertEquals(emptyList(), access.pluginFileHandles(APP))

        permissions.setDecisions(APP, mapOf(PluginPermissions.READ_FILES to true, PluginPermissions.WRITE_FILES to true))
        assertEquals(FileAccessMode.ReadWrite, access.handleInfo(APP, "vault")?.mode)
    }

    @Test
    fun revokeReleasesAScopeNobodyElseHolds() = runTest {
        fileSystem.dir("vault")
        access.grantPluginFile(APP, "vault", FileAccessMode.Read, "vault")
        access.grantPluginFile(other, "vault", FileAccessMode.Read, "vault")
        access.revokePluginFile(APP, "vault")
        assertNull(dao.row(readFilesKey("vault")))
        assertEquals(emptyList(), fileSystem.released)
        access.revokePluginFile(other, "vault")
        assertEquals(listOf("vault"), fileSystem.released)
    }

    @Test
    fun destroyDropsEveryFileRowAndReleasesWhatIsLeftUnreferenced() = runTest {
        fileSystem.dir("vault")
        fileSystem.file("a.txt")
        access.grantPluginFile(APP, "vault", FileAccessMode.ReadWrite, "vault")
        access.grantPluginFile(APP, "a.txt", FileAccessMode.Read, "a.txt")
        access.grantPluginFile(other, "a.txt", FileAccessMode.Read, "a.txt")
        permissions.setDecisions(APP, mapOf(PluginPermissions.LOCATION to true))
        access.destroy(APP)
        assertEquals(listOf(PluginPermissions.LOCATION), dao.getByAppUuid(APP).map { it.permission })
        assertEquals(listOf("vault"), fileSystem.released)
        assertEquals(1, access.pluginFileHandles(other).size)
    }

    @Test
    fun aPickNeedsTheBarePermissionAlreadyGrantedAndNeverAsks() = runTest {
        // Reading never leaves the phone, so the default policy grants it without asking.
        assertEquals(PermissionDecision.Granted, access.checkFilePermission(APP, FileAccessMode.Read))
        assertEquals(PermissionDecision.NeedsApproval, access.checkFilePermission(APP, FileAccessMode.ReadWrite))
        assertNull(dao.row(PluginPermissions.READ_FILES))
        assertNull(dao.row(PluginPermissions.WRITE_FILES))

        permissions.setDecisions(APP, mapOf(PluginPermissions.WRITE_FILES to true))
        assertEquals(PermissionDecision.Granted, access.checkFilePermission(APP, FileAccessMode.ReadWrite))
        assertEquals(PermissionDecision.Granted, access.checkFilePermission(APP, FileAccessMode.Read))

        val neverAsk = PluginFileAccess(permissions(dao, config = WatchConfig(enablePlugins = true, permissionPolicy = PermissionPolicy.NeverAsk)), dao, fileSystem)
        permissions.setDecisions(APP, mapOf(PluginPermissions.WRITE_FILES to false))
        assertEquals(PermissionDecision.Denied, neverAsk.checkFilePermission(APP, FileAccessMode.ReadWrite))
        assertEquals(PermissionDecision.Denied, neverAsk.checkFilePermission(APP, FileAccessMode.Read))
    }
}
