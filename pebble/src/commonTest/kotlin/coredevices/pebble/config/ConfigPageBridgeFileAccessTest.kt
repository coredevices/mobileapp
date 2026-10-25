package coredevices.pebble.config

import io.rebble.libpebblecommon.plugin.PluginErrors
import io.rebble.libpebblecommon.plugin.PluginNativeException
import io.rebble.libpebblecommon.plugin.files.FileAccessMode
import io.rebble.libpebblecommon.plugin.files.FileEntryKind
import io.rebble.libpebblecommon.plugin.files.FileHandleInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigPageBridgeFileAccessTest {
    private class FakeFileAccess : ConfigPageFileAccess {
        var picked: FileHandleInfo? = null
        var refuse = false
        var lastRequest: FilePickRequest? = null
        val held = mutableListOf<FileHandleInfo>()
        val revoked = mutableListOf<String>()

        override suspend fun pick(request: FilePickRequest): FileHandleInfo? {
            lastRequest = request
            if (refuse) throw PluginNativeException(PluginErrors.PERMISSION_DENIED, "not allowed")
            return picked
        }

        override suspend fun handles() = held.toList()
        override suspend fun revoke(scope: String) { revoked += scope }
    }

    private fun bridgeTest(block: suspend (bridge: ConfigPageBridge, access: FakeFileAccess, replies: List<String>) -> Unit) =
        runTest {
            val replies = mutableListOf<String>()
            val bridge = ConfigPageBridge(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
            bridge.attach { replies += it }
            val access = FakeFileAccess()
            bridge.fileAccess = access
            block(bridge, access, replies)
        }

    private fun reply(id: Int, json: String) = "window.__pebbleConfig.reply($id, ${quote(json)})"

    private fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    @Test
    fun aPickedEntryComesBackAsAHandle() = bridgeTest { bridge, access, replies ->
        access.picked = FileHandleInfo("content://docs/tree/Vault", FileEntryKind.Directory, "Vault", FileAccessMode.ReadWrite)
        bridge.onCall("""{"id":1,"method":"showFilePicker","message":{"kind":"directory","mode":"readwrite","accept":[]}}""")
        assertTrue(replies.last().contains(reply(1, """{"id":"content://docs/tree/Vault","kind":"directory","name":"Vault","mode":"readwrite"}""")))
        assertEquals(FilePickRequest(FileEntryKind.Directory, FileAccessMode.ReadWrite), access.lastRequest)
    }

    @Test
    fun cancellingResolvesNull() = bridgeTest { bridge, access, replies ->
        bridge.onCall("""{"id":1,"method":"showFilePicker","message":{"kind":"file","accept":["text/markdown"]}}""")
        assertTrue(replies.last().contains(reply(1, "null")))
        assertEquals(FilePickRequest(FileEntryKind.File, FileAccessMode.Read, listOf("text/markdown")), access.lastRequest)
    }

    @Test
    fun aRefusedPermissionCarriesItsCode() = bridgeTest { bridge, access, replies ->
        access.refuse = true
        bridge.onCall("""{"id":1,"method":"showFilePicker","message":{"kind":"file"}}""")
        assertTrue(replies.last().contains(reply(1, """{"error":"not allowed","code":"PERMISSION_DENIED"}""")))
    }

    @Test
    fun aMalformedRequestIsAnError() = bridgeTest { bridge, access, replies ->
        bridge.onCall("""{"id":1,"method":"showFilePicker","message":{"kind":"socket"}}""")
        assertTrue(replies.last().contains("showFilePicker needs"))
        assertNull(access.lastRequest)
    }

    @Test
    fun withoutAPluginBehindThePageFileAccessIsRefused() = bridgeTest { bridge, _, replies ->
        bridge.fileAccess = null
        bridge.onCall("""{"id":1,"method":"showFilePicker","message":{"kind":"file"}}""")
        bridge.onCall("""{"id":2,"method":"fileGrants"}""")
        bridge.onCall("""{"id":3,"method":"revokeFileGrant","message":{"id":"a.txt"}}""")
        assertEquals(3, replies.size)
        replies.forEach { assertTrue(it.contains("not available")) }
    }

    @Test
    fun handlesAreListedAndRevoked() = bridgeTest { bridge, access, replies ->
        access.held += FileHandleInfo("a.txt", FileEntryKind.File, "a.txt", FileAccessMode.Read)
        bridge.onCall("""{"id":1,"method":"fileGrants"}""")
        assertTrue(replies.last().contains(reply(1, """[{"id":"a.txt","kind":"file","name":"a.txt","mode":"read"}]""")))
        bridge.onCall("""{"id":2,"method":"revokeFileGrant","message":{"id":"a.txt"}}""")
        assertTrue(replies.last().contains(reply(2, "null")))
        assertEquals(listOf("a.txt"), access.revoked)
        bridge.onCall("""{"id":3,"method":"revokeFileGrant","message":{}}""")
        assertTrue(replies.last().contains("revokeFileGrant needs"))
    }
}
