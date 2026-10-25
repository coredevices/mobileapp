package io.rebble.libpebblecommon.plugin.files

import io.rebble.libpebblecommon.plugin.PluginNativeException

/** An in-memory tree per scope, with the platform implementations' error behaviour. */
class FakePluginFileSystem : PluginFileSystem {
    sealed interface Node
    class File(var bytes: ByteArray) : Node
    class Dir(val children: MutableMap<String, Node> = mutableMapOf()) : Node

    val roots = mutableMapOf<String, Node>()
    val retained = mutableMapOf<String, String>()
    val released = mutableListOf<String>()
    var accessible = true

    fun dir(scope: String, build: Dir.() -> Unit = {}) = Dir().apply(build).also { roots[scope] = it }
    fun file(scope: String, content: String = "") = File(content.encodeToByteArray()).also { roots[scope] = it }

    override suspend fun retain(scope: String, handle: String) {
        retained[scope] = handle
    }

    override suspend fun release(scope: String) {
        released += scope
    }

    override suspend fun hasAccess(scope: String) = accessible && scope in roots

    override suspend fun stat(scope: String, path: List<String>): FileEntryInfo? =
        resolve(scope, path)?.info(path.lastOrNull() ?: scope.substringAfterLast('/'))

    override suspend fun list(scope: String, path: List<String>): List<FileEntryInfo> {
        val dir = resolve(scope, path) ?: throw notFound(path)
        if (dir !is Dir) throw typeMismatch("not a directory")
        return dir.children.map { (name, node) -> node.info(name) }
    }

    override suspend fun read(scope: String, path: List<String>): ByteArray {
        val node = resolve(scope, path) ?: throw notFound(path)
        if (node !is File) throw typeMismatch("is a directory")
        return node.bytes
    }

    override suspend fun write(scope: String, path: List<String>, data: ByteArray, create: Boolean) {
        when (val existing = resolve(scope, path)) {
            is File -> existing.bytes = data
            is Dir -> throw typeMismatch("is a directory")
            null -> {
                if (!create || path.isEmpty()) throw notFound(path)
                parent(scope, path).children[path.last()] = File(data)
            }
        }
    }

    override suspend fun createDirectory(scope: String, path: List<String>) {
        when (resolve(scope, path)) {
            is Dir -> return
            is File -> throw typeMismatch("is a file")
            null -> {
                if (path.isEmpty()) throw notFound(path)
                parent(scope, path).children[path.last()] = Dir()
            }
        }
    }

    override suspend fun remove(scope: String, path: List<String>, recursive: Boolean) {
        val node = resolve(scope, path) ?: throw notFound(path)
        if (node is Dir && !recursive && node.children.isNotEmpty()) {
            throw PluginNativeException(PluginFileErrors.INVALID_MODIFICATION, "not empty")
        }
        if (path.isEmpty()) roots.remove(scope) else parent(scope, path).children.remove(path.last())
    }

    private fun resolve(scope: String, path: List<String>): Node? {
        val root = roots[scope] ?: return null
        if (path.isNotEmpty() && root !is Dir) throw typeMismatch("is a file, not a directory")
        return path.fold<String, Node?>(root) { node, segment -> (node as? Dir)?.children?.get(segment) }
    }

    private fun parent(scope: String, path: List<String>): Dir =
        resolve(scope, path.dropLast(1)) as? Dir ?: throw notFound(path.dropLast(1))

    private fun Node.info(name: String) = when (this) {
        is File -> FileEntryInfo(name, FileEntryKind.File, size = bytes.size.toLong(), lastModified = 1000, type = "text/plain")
        is Dir -> FileEntryInfo(name, FileEntryKind.Directory)
    }
}

fun FakePluginFileSystem.Dir.file(name: String, content: String = "") =
    FakePluginFileSystem.File(content.encodeToByteArray()).also { children[name] = it }

fun FakePluginFileSystem.Dir.dir(name: String, build: FakePluginFileSystem.Dir.() -> Unit = {}) =
    FakePluginFileSystem.Dir().apply(build).also { children[name] = it }
