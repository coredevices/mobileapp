package io.rebble.libpebblecommon.plugin.files

import io.rebble.libpebblecommon.permissions.APP
import io.rebble.libpebblecommon.permissions.FakePermissionDao
import io.rebble.libpebblecommon.permissions.PermissionDecision
import io.rebble.libpebblecommon.permissions.permissions
import io.rebble.libpebblecommon.permissions.readFilesKey
import io.rebble.libpebblecommon.permissions.writeFilesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalEncodingApi::class)
class PluginFileSystemJsBridgeTest {
    private val otherPlugin = Uuid.parse("5cf21b8e-2f4e-4d34-9b0d-1c5bb2cf0002")
    private val fileSystem = FakePluginFileSystem()
    private val dao = FakePermissionDao()
    private val permissions = permissions(dao)
    private val access = PluginFileAccess(permissions, dao, fileSystem)

    private class Reply(val raw: String) {
        private val payload = Json.parseToJsonElement(raw.removePrefix(PREFIX).removeSuffix(")").substringAfter(","))
        val result: JsonElement get() = payload.jsonObject["result"] ?: JsonNull
        val error: String? get() = payload.jsonObject["error"]?.jsonObject?.get("code")?.jsonPrimitive?.content

        companion object {
            const val PREFIX = "globalThis.__pluginFileSystemReply("
        }
    }

    private fun bridgeTest(block: suspend (request: (String) -> Reply) -> Unit) = runTest {
        val replies = mutableListOf<String>()
        val bridge = PluginFileSystemJsBridge(
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            { replies += it },
            APP,
            access,
            fileSystem,
        )
        var nextId = 0
        block { json ->
            bridge.dispatch("request", listOf(++nextId, json))
            Reply(replies.last()).also { assertTrue(it.raw.startsWith("${Reply.PREFIX}$nextId,")) }
        }
    }

    private suspend fun vault(mode: FileAccessMode = FileAccessMode.ReadWrite, app: Uuid = APP): String {
        fileSystem.dir(VAULT) {
            file("notes.md", "# hello")
            dir("sub") { file("deep.txt", "deep") }
            dir("empty")
        }
        assertNotNull(access.grantPluginFile(app, VAULT, mode, VAULT))
        return VAULT
    }

    @Test
    fun getHandleDescribesTheEntry() = bridgeTest { request ->
        val scope = vault(FileAccessMode.Read)
        val result = request("""{"operation":"getHandle","id":"$scope"}""").result.jsonObject
        assertEquals(scope, result["id"]?.jsonPrimitive?.content)
        assertEquals("directory", result["kind"]?.jsonPrimitive?.content)
        assertEquals("Vault", result["name"]?.jsonPrimitive?.content)
        assertEquals("read", result["mode"]?.jsonPrimitive?.content)
    }

    @Test
    fun anEntryThisPluginWasNotGivenIsRefused() = bridgeTest { request ->
        vault(app = otherPlugin)
        assertEquals("PERMISSION_DENIED", request("""{"operation":"getHandle","id":"$VAULT"}""").error)
        assertEquals("PERMISSION_DENIED", request("""{"operation":"stat","id":"$VAULT","path":[]}""").error)
        assertEquals("PERMISSION_DENIED", request("""{"operation":"getHandle","id":"content://nope"}""").error)
    }

    @Test
    fun aRevokedEntryIsRefused() = bridgeTest { request ->
        val scope = vault()
        permissions.setDecisions(APP, mapOf(readFilesKey(scope) to false))
        assertEquals("PERMISSION_DENIED", request("""{"operation":"read","id":"$scope","path":["notes.md"]}""").error)
        assertEquals("denied", request("""{"operation":"queryPermission","id":"$scope"}""").result.jsonPrimitive.content)
    }

    @Test
    fun grantsListsOnlyThisPlugins() = bridgeTest { request ->
        val mine = vault()
        fileSystem.file("theirs.txt", "x")
        access.grantPluginFile(otherPlugin, "theirs.txt", FileAccessMode.Read, "theirs.txt")
        val listed = request("""{"operation":"grants"}""").result.jsonArray
        assertEquals(listOf(mine), listed.map { it.jsonObject["id"]?.jsonPrimitive?.content })
        assertEquals("readwrite", listed.single().jsonObject["mode"]?.jsonPrimitive?.content)
    }

    @Test
    fun queryPermissionHonoursTheGrantedModeAndPlatformAccess() = bridgeTest { request ->
        val scope = vault(FileAccessMode.Read)
        assertEquals("granted", request("""{"operation":"queryPermission","id":"$scope"}""").result.jsonPrimitive.content)
        assertEquals(
            "denied",
            request("""{"operation":"queryPermission","id":"$scope","mode":"readwrite"}""").result.jsonPrimitive.content,
        )
        fileSystem.accessible = false
        assertEquals("denied", request("""{"operation":"queryPermission","id":"$scope"}""").result.jsonPrimitive.content)
    }

    @Test
    fun statListAndReadWalkTheGrantedTree() = bridgeTest { request ->
        val scope = vault(FileAccessMode.Read)
        val root = request("""{"operation":"stat","id":"$scope","path":[]}""").result.jsonObject
        assertEquals("directory", root["kind"]?.jsonPrimitive?.content)

        val names = request("""{"operation":"list","id":"$scope","path":[]}""").result.jsonArray
            .map { it.jsonObject["name"]?.jsonPrimitive?.content }
        assertEquals(listOf("notes.md", "sub", "empty"), names)

        val deep = request("""{"operation":"stat","id":"$scope","path":["sub","deep.txt"]}""").result.jsonObject
        assertEquals("file", deep["kind"]?.jsonPrimitive?.content)
        assertEquals(4, deep["size"]?.jsonPrimitive?.content?.toInt())

        assertEquals(
            "# hello",
            request("""{"operation":"read","id":"$scope","path":["notes.md"]}""").result.jsonPrimitive.content,
        )
        assertEquals(
            Base64.encode("deep".encodeToByteArray()),
            request("""{"operation":"read","id":"$scope","path":["sub","deep.txt"],"encoding":"base64"}""")
                .result.jsonPrimitive.content,
        )
        assertEquals(PluginFileErrors.NOT_FOUND, request("""{"operation":"stat","id":"$scope","path":["missing"]}""").error)
        assertEquals(PluginFileErrors.TYPE_MISMATCH, request("""{"operation":"list","id":"$scope","path":["notes.md"]}""").error)
    }

    @Test
    fun pathsCannotEscapeTheEntry() = bridgeTest { request ->
        val scope = vault()
        for (segment in listOf("..", ".", "", "a/b", "a\\b")) {
            val reply = request("""{"operation":"stat","id":"$scope","path":[${Json.encodeToString(segment)}]}""")
            assertEquals("INVALID_ARGS", reply.error, "segment '$segment'")
        }
        assertEquals("INVALID_ARGS", request("""{"operation":"stat","id":"$scope","path":"notes.md"}""").error)
    }

    @Test
    fun aFileHasNoChildren() = bridgeTest { request ->
        fileSystem.file("one.txt", "one")
        access.grantPluginFile(APP, "one.txt", FileAccessMode.Read, "one.txt")
        assertEquals("one", request("""{"operation":"read","id":"one.txt","path":[]}""").result.jsonPrimitive.content)
        assertEquals(PluginFileErrors.TYPE_MISMATCH, request("""{"operation":"stat","id":"one.txt","path":["child"]}""").error)
    }

    @Test
    fun mutationsNeedWriteFiles() = bridgeTest { request ->
        val scope = vault(FileAccessMode.Read)
        val data = Base64.encode("x".encodeToByteArray())
        assertEquals("PERMISSION_DENIED", request("""{"operation":"write","id":"$scope","path":["notes.md"],"data":"$data"}""").error)
        assertEquals("PERMISSION_DENIED", request("""{"operation":"mkdir","id":"$scope","path":["new"]}""").error)
        assertEquals("PERMISSION_DENIED", request("""{"operation":"remove","id":"$scope","path":["notes.md"]}""").error)
        assertEquals("# hello", request("""{"operation":"read","id":"$scope","path":["notes.md"]}""").result.jsonPrimitive.content)
        // Nothing was asked of the user: a scoped file permission is never prompted for.
        assertEquals(PermissionDecision.Denied, permissions.check(APP, "WriteFiles/$scope"))
    }

    @Test
    fun writeReplacesOrCreatesAFile() = bridgeTest { request ->
        val scope = vault()
        val data = Base64.encode("replaced".encodeToByteArray())
        request("""{"operation":"write","id":"$scope","path":["notes.md"],"data":"$data"}""")
        assertEquals("replaced", request("""{"operation":"read","id":"$scope","path":["notes.md"]}""").result.jsonPrimitive.content)

        assertEquals(PluginFileErrors.NOT_FOUND, request("""{"operation":"write","id":"$scope","path":["new.txt"],"data":""}""").error)
        request("""{"operation":"write","id":"$scope","path":["new.txt"],"data":"","create":true}""")
        assertEquals(0, request("""{"operation":"stat","id":"$scope","path":["new.txt"]}""").result.jsonObject["size"]?.jsonPrimitive?.content?.toInt())
        assertEquals("INVALID_ARGS", request("""{"operation":"write","id":"$scope","path":["new.txt"],"data":"not base64!"}""").error)
    }

    @Test
    fun aWritableBuffersChunksOnTheHostAndWritesOnClose() = bridgeTest { request ->
        val scope = vault()
        val stream = request("""{"operation":"openWritable","id":"$scope","path":["out.txt"]}""").result.jsonPrimitive.content
        request("""{"operation":"streamWrite","stream":$stream,"text":"héllo "}""")
        request("""{"operation":"streamWrite","stream":$stream,"data":"${Base64.encode("wörld!".encodeToByteArray())}"}""")
        // Nothing lands until close.
        assertEquals(PluginFileErrors.NOT_FOUND, request("""{"operation":"stat","id":"$scope","path":["out.txt"]}""").error)
        request("""{"operation":"streamTruncate","stream":$stream,"size":13}""")
        request("""{"operation":"streamClose","stream":$stream}""")
        assertEquals("héllo wörld", request("""{"operation":"read","id":"$scope","path":["out.txt"]}""").result.jsonPrimitive.content)
        assertEquals("INVALID_ARGS", request("""{"operation":"streamWrite","stream":$stream,"text":"late"}""").error)
    }

    @Test
    fun aWritableCanKeepExistingDataAndBeAborted() = bridgeTest { request ->
        val scope = vault()
        val stream = request("""{"operation":"openWritable","id":"$scope","path":["notes.md"],"keepExistingData":true}""").result.jsonPrimitive.content
        request("""{"operation":"streamTruncate","stream":$stream,"size":1}""")
        request("""{"operation":"streamWrite","stream":$stream,"text":"!"}""")
        request("""{"operation":"streamClose","stream":$stream}""")
        assertEquals("#!", request("""{"operation":"read","id":"$scope","path":["notes.md"]}""").result.jsonPrimitive.content)

        val aborted = request("""{"operation":"openWritable","id":"$scope","path":["notes.md"]}""").result.jsonPrimitive.content
        request("""{"operation":"streamWrite","stream":$aborted,"text":"discarded"}""")
        request("""{"operation":"streamAbort","stream":$aborted}""")
        assertEquals("#!", request("""{"operation":"read","id":"$scope","path":["notes.md"]}""").result.jsonPrimitive.content)
        assertEquals("INVALID_ARGS", request("""{"operation":"streamClose","stream":$aborted}""").error)
    }

    @Test
    fun aWritableNeedsWriteFilesToOpenAndToClose() = bridgeTest { request ->
        val scope = vault(FileAccessMode.Read)
        assertEquals("PERMISSION_DENIED", request("""{"operation":"openWritable","id":"$scope","path":["out.txt"]}""").error)

        permissions.setDecisions(APP, mapOf(writeFilesKey(scope) to true))
        val stream = request("""{"operation":"openWritable","id":"$scope","path":["out.txt"]}""").result.jsonPrimitive.content
        request("""{"operation":"streamWrite","stream":$stream,"text":"x"}""")
        permissions.setDecisions(APP, mapOf(writeFilesKey(scope) to false))
        assertEquals("PERMISSION_DENIED", request("""{"operation":"streamClose","stream":$stream}""").error)
        assertEquals(PluginFileErrors.NOT_FOUND, request("""{"operation":"stat","id":"$scope","path":["out.txt"]}""").error)
    }

    @Test
    fun mkdirAndRemoveFollowTheBrowserRules() = bridgeTest { request ->
        val scope = vault()
        request("""{"operation":"mkdir","id":"$scope","path":["made"]}""")
        assertEquals("directory", request("""{"operation":"stat","id":"$scope","path":["made"]}""").result.jsonObject["kind"]?.jsonPrimitive?.content)
        assertEquals(PluginFileErrors.TYPE_MISMATCH, request("""{"operation":"mkdir","id":"$scope","path":["notes.md"]}""").error)

        assertEquals(PluginFileErrors.INVALID_MODIFICATION, request("""{"operation":"remove","id":"$scope","path":["sub"]}""").error)
        request("""{"operation":"remove","id":"$scope","path":["sub"],"recursive":true}""")
        request("""{"operation":"remove","id":"$scope","path":["empty"]}""")
        val names = request("""{"operation":"list","id":"$scope","path":[]}""").result.jsonArray
            .map { it.jsonObject["name"]?.jsonPrimitive?.content }
        assertEquals(listOf("notes.md", "made"), names)
    }

    private companion object {
        const val VAULT = "content://docs/tree/Vault"
    }
}
