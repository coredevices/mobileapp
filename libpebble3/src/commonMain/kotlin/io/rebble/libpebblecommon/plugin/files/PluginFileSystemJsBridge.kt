package io.rebble.libpebblecommon.plugin.files

import io.rebble.libpebblecommon.plugin.PluginErrors
import io.rebble.libpebblecommon.plugin.PluginNativeException
import io.rebble.libpebblecommon.plugin.PromiseJsBridge
import io.rebble.libpebblecommon.plugin.promiseBridgeJs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid

/**
 * `Pebble.fileSystem` in a plugin session: the File System Access API's handles over entries the
 * user granted from the settings page. Every request names a scope and a path under it; reads
 * need `ReadFiles/<scope>` and mutations `WriteFiles/<scope>`, checked on each call.
 */
@OptIn(ExperimentalEncodingApi::class)
internal class PluginFileSystemJsBridge(
    scope: CoroutineScope,
    eval: (String) -> Unit,
    private val pluginUuid: Uuid,
    private val access: PluginFileAccess,
    private val fileSystem: PluginFileSystem,
) : PromiseJsBridge(scope, eval, NAME, REPLY_FUNCTION, FAILURE_MESSAGE) {

    // Defaults included, so an empty file still reports `size: 0` rather than nothing.
    private val json = Json { encodeDefaults = true }

    override suspend fun handle(request: JsonObject): JsonElement {
        val operation = request.string("operation")
            ?: throw IllegalArgumentException("operation is required")
        if (operation == "grants") {
            return json.encodeToJsonElement(HANDLE_INFOS, access.pluginFileHandles(pluginUuid))
        }
        if (operation.startsWith("stream")) return streamOperation(operation, request)
        val scope = request.string("id") ?: throw IllegalArgumentException("id is required")
        if (operation == "queryPermission") {
            val granted = access.check(pluginUuid, scope, request.mode()) && fileSystem.hasAccess(scope)
            return JsonPrimitive(if (granted) "granted" else "denied")
        }
        val handle = access.handleInfo(pluginUuid, scope)
            ?: throw PluginNativeException(PluginErrors.PERMISSION_DENIED, "this plugin was not given '$scope'")
        return when (operation) {
            "getHandle" -> json.encodeToJsonElement(FileHandleInfo.serializer(), handle)
            else -> entryOperation(operation, handle, request)
        }
    }

    private suspend fun entryOperation(operation: String, handle: FileHandleInfo, request: JsonObject): JsonElement {
        val scope = handle.id
        val path = request.path()
        if (handle.kind == FileEntryKind.File && path.isNotEmpty()) {
            throw typeMismatch("'${handle.name}' is a file, not a directory")
        }
        return when (operation) {
            "stat" -> fileSystem.stat(scope, path)
                ?.let { json.encodeToJsonElement(FileEntryInfo.serializer(), it) }
                ?: throw notFound(path)
            "list" -> json.encodeToJsonElement(ENTRY_INFOS, fileSystem.list(scope, path))
            "read" -> {
                val bytes = fileSystem.read(scope, path)
                when (val encoding = request.string("encoding") ?: "utf8") {
                    "utf8" -> JsonPrimitive(bytes.decodeToString())
                    "base64" -> JsonPrimitive(Base64.encode(bytes))
                    else -> throw IllegalArgumentException("unknown encoding '$encoding'")
                }
            }
            "write" -> {
                requireWrite(handle)
                val data = (request["data"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: throw IllegalArgumentException("data is required")
                fileSystem.write(scope, path, decodeBase64(data), create = request.flag("create"))
                JsonNull
            }
            "openWritable" -> {
                requireWrite(handle)
                val stream = WritableStream(handle, path)
                if (request.flag("keepExistingData")) stream.buffer.write(fileSystem.read(scope, path))
                JsonPrimitive(streamsLock.withLock { (nextStream++).also { streams[it] = stream } })
            }
            "mkdir" -> {
                requireWrite(handle)
                fileSystem.createDirectory(scope, path)
                JsonNull
            }
            "remove" -> {
                requireWrite(handle)
                fileSystem.remove(scope, path, recursive = request.flag("recursive"))
                JsonNull
            }
            else -> throw IllegalArgumentException("unknown file system operation '$operation'")
        }
    }

    /**
     * A writable's chunks accumulate here and land in one piece on close, as the browser's do.
     * The JS side queues its calls, so one stream never sees two operations at once; the lock
     * only guards against a misbehaving page.
     */
    private class WritableStream(val handle: FileHandleInfo, val path: List<String>) {
        val buffer = Buffer()
        val lock = Mutex()

        fun truncate(size: Long) {
            if (size < buffer.size) {
                val kept = buffer.readByteArray(size.toInt())
                buffer.clear()
                buffer.write(kept)
            } else if (size > buffer.size) {
                buffer.write(ByteArray((size - buffer.size).toInt()))
            }
        }
    }

    private val streams = mutableMapOf<Int, WritableStream>()
    private val streamsLock = Mutex()
    private var nextStream = 1

    private suspend fun streamOperation(operation: String, request: JsonObject): JsonElement {
        val id = (request["stream"] as? JsonPrimitive)?.content?.toIntOrNull()
            ?: throw IllegalArgumentException("stream is required")
        val stream = streamsLock.withLock { streams[id] }
            ?: throw IllegalArgumentException("stream $id is closed")
        stream.lock.withLock {
            when (operation) {
                "streamWrite" -> {
                    val text = (request["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    val data = (request["data"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    when {
                        text != null -> stream.buffer.write(text.encodeToByteArray())
                        data != null -> stream.buffer.write(decodeBase64(data))
                        else -> throw IllegalArgumentException("text or data is required")
                    }
                }
                "streamTruncate" -> {
                    val size = (request["size"] as? JsonPrimitive)?.content?.toLongOrNull()?.takeIf { it >= 0 }
                        ?: throw IllegalArgumentException("size must be a non-negative integer")
                    stream.truncate(size)
                }
                "streamClose" -> {
                    streamsLock.withLock { streams.remove(id) }
                    // Checked again: the user may have withdrawn WriteFiles since the stream opened.
                    requireWrite(stream.handle)
                    fileSystem.write(stream.handle.id, stream.path, stream.buffer.readByteArray(), create = true)
                }
                "streamAbort" -> {
                    streamsLock.withLock { streams.remove(id) }
                    stream.buffer.clear()
                }
                else -> throw IllegalArgumentException("unknown stream operation '$operation'")
            }
        }
        return JsonNull
    }

    private fun decodeBase64(data: String): ByteArray = try {
        Base64.decode(data)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("data must be base64", e)
    }

    private suspend fun requireWrite(handle: FileHandleInfo) {
        if (!access.check(pluginUuid, handle.id, FileAccessMode.ReadWrite)) {
            throw PluginNativeException(PluginErrors.PERMISSION_DENIED, "'${handle.name}' was granted read-only")
        }
    }

    private fun JsonObject.path(): List<String> {
        val raw = get("path") ?: return emptyList()
        val segments = try {
            raw.jsonArray.map { it.jsonPrimitive.content }
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("path must be an array of names", e)
        }
        return PluginFilePath.validate(segments)
    }

    private fun JsonObject.mode(): FileAccessMode = when (val mode = string("mode") ?: "read") {
        "read" -> FileAccessMode.Read
        "readwrite" -> FileAccessMode.ReadWrite
        else -> throw IllegalArgumentException("unknown mode '$mode'")
    }

    private fun JsonObject.flag(name: String) =
        (get(name) as? JsonPrimitive)?.content?.toBoolean() ?: false

    companion object {
        private const val NAME = "_PluginFileSystemBridge"
        private const val REPLY_FUNCTION = "__pluginFileSystemReply"
        private const val FAILURE_MESSAGE = "file system operation failed"
        private val HANDLE_INFOS = ListSerializer(FileHandleInfo.serializer())
        private val ENTRY_INFOS = ListSerializer(FileEntryInfo.serializer())

        val INSTALL_JS = """
(function (global) {
${promiseBridgeJs(NAME, REPLY_FUNCTION, FAILURE_MESSAGE)}
  var ERROR_NAMES = {
    NOT_FOUND: 'NotFoundError',
    PERMISSION_DENIED: 'NotAllowedError',
    TYPE_MISMATCH: 'TypeMismatchError',
    INVALID_MODIFICATION: 'InvalidModificationError',
    INVALID_ARGS: 'TypeError'
  };
  function fs(operation, args) {
    return call(operation, args).catch(function (e) {
      if (e && ERROR_NAMES[e.code]) e.name = ERROR_NAMES[e.code];
      throw e;
    });
  }
  function fail(name, message) {
    var e = new Error(message);
    e.name = name;
    return e;
  }
  function checkName(name) {
    name = String(name);
    if (!name || name === '.' || name === '..' || /[\/\\\u0000]/.test(name)) {
      throw new TypeError("'" + name + "' is not a valid entry name");
    }
    return name;
  }
  // Strings cross the bridge as they are and are UTF-8 encoded on the host; only bytes need base64.
  function chunkOf(data) {
    if (typeof data === 'string') return { text: data };
    return { data: btoa(toBinaryString(data)) };
  }
  function toBinaryString(data) {
    var bytes;
    if (data instanceof ArrayBuffer) bytes = new Uint8Array(data);
    else if (ArrayBuffer.isView(data)) bytes = new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
    else throw new TypeError('write() takes a string, ArrayBuffer or typed array');
    var out = '';
    for (var i = 0; i < bytes.length; i += 8192) {
      out += String.fromCharCode.apply(null, bytes.subarray(i, i + 8192));
    }
    return out;
  }
  function fromBase64(base64) {
    var binary = atob(base64);
    var bytes = new Uint8Array(binary.length);
    for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes.buffer;
  }
  function modeOf(descriptor) {
    return descriptor && descriptor.mode === 'readwrite' ? 'readwrite' : 'read';
  }

  function Handle(id, name, path) {
    Object.defineProperty(this, '_id', { value: id });
    Object.defineProperty(this, '_path', { value: path });
    this.name = name;
  }
  Handle.prototype.queryPermission = function (descriptor) {
    return fs('queryPermission', { id: this._id, mode: modeOf(descriptor) });
  };
  // A plugin has no UI: only its settings page can ask the user for more.
  Handle.prototype.requestPermission = Handle.prototype.queryPermission;
  Handle.prototype.isSameEntry = function (other) {
    return Promise.resolve(other instanceof Handle && other._id === this._id &&
      other._path.join('/') === this._path.join('/'));
  };
  Handle.prototype.remove = function (options) {
    return fs('remove', { id: this._id, path: this._path, recursive: !!(options && options.recursive) });
  };

  function FileHandle(id, name, path) { Handle.call(this, id, name, path); }
  FileHandle.prototype = Object.create(Handle.prototype);
  FileHandle.prototype.constructor = FileHandle;
  FileHandle.prototype.kind = 'file';
  FileHandle.prototype.getFile = function () {
    var id = this._id, path = this._path;
    return fs('stat', { id: id, path: path }).then(function (info) {
      if (info.kind !== 'file') throw fail('TypeMismatchError', info.name + ' is a directory');
      var file = {
        name: info.name,
        size: info.size,
        lastModified: info.lastModified,
        type: info.type || '',
        text: function () { return fs('read', { id: id, path: path, encoding: 'utf8' }); },
        arrayBuffer: function () { return fs('read', { id: id, path: path, encoding: 'base64' }).then(fromBase64); },
        bytes: function () { return file.arrayBuffer().then(function (buffer) { return new Uint8Array(buffer); }); }
      };
      return file;
    });
  };
  // Chunks go straight to the host, which buffers them and writes the file on close(). Calls are
  // queued so they land in order, as a browser's writable stream does.
  FileHandle.prototype.createWritable = function (options) {
    var id = this._id, path = this._path;
    var keepExistingData = !!(options && options.keepExistingData);
    return fs('openWritable', { id: id, path: path, keepExistingData: keepExistingData }).then(function (stream) {
      var queue = Promise.resolve();
      var closed = false;
      function enqueue(operation, args) {
        if (closed) return Promise.reject(fail('InvalidStateError', 'the stream is closed'));
        var next = queue.then(function () { return fs(operation, Object.assign({ stream: stream }, args)); });
        queue = next.then(null, function () {});
        return next;
      }
      var writable = {
        write: function (data) {
          if (data && typeof data === 'object' && typeof data.type === 'string' && !ArrayBuffer.isView(data) &&
              !(data instanceof ArrayBuffer)) {
            if (data.type === 'truncate') return writable.truncate(data.size);
            if (data.type !== 'write') return Promise.reject(new TypeError(data.type + ' is not supported'));
            data = data.data;
          }
          var chunk;
          try { chunk = chunkOf(data); } catch (e) { return Promise.reject(e); }
          return enqueue('streamWrite', chunk);
        },
        truncate: function (size) { return enqueue('streamTruncate', { size: Number(size) || 0 }); },
        seek: function () { return Promise.reject(new TypeError('seek is not supported')); },
        close: function () {
          var done = enqueue('streamClose', {});
          closed = true;
          return done;
        },
        abort: function () {
          if (closed) return Promise.resolve();
          closed = true;
          return queue.then(function () { return fs('streamAbort', { stream: stream }); });
        }
      };
      return writable;
    });
  };

  function DirectoryHandle(id, name, path) { Handle.call(this, id, name, path); }
  DirectoryHandle.prototype = Object.create(Handle.prototype);
  DirectoryHandle.prototype.constructor = DirectoryHandle;
  DirectoryHandle.prototype.kind = 'directory';
  function child(parent, info) {
    var path = parent._path.concat([info.name]);
    return info.kind === 'directory'
      ? new DirectoryHandle(parent._id, info.name, path)
      : new FileHandle(parent._id, info.name, path);
  }
  function getChild(parent, name, options, kind, createOperation) {
    var path = parent._path.concat([checkName(name)]);
    var id = parent._id;
    return fs('stat', { id: id, path: path }).then(function (info) {
      if (info.kind !== kind) throw fail('TypeMismatchError', name + ' is not a ' + kind);
      return child(parent, info);
    }, function (e) {
      if (e.code !== 'NOT_FOUND' || !(options && options.create)) throw e;
      return fs(createOperation, { id: id, path: path, data: '', create: true }).then(function () {
        return child(parent, { name: path[path.length - 1], kind: kind });
      });
    });
  }
  DirectoryHandle.prototype.getFileHandle = function (name, options) {
    return getChild(this, name, options, 'file', 'write');
  };
  DirectoryHandle.prototype.getDirectoryHandle = function (name, options) {
    return getChild(this, name, options, 'directory', 'mkdir');
  };
  DirectoryHandle.prototype.removeEntry = function (name, options) {
    return fs('remove', {
      id: this._id, path: this._path.concat([checkName(name)]), recursive: !!(options && options.recursive)
    });
  };
  DirectoryHandle.prototype.resolve = function (possibleDescendant) {
    if (!(possibleDescendant instanceof Handle) || possibleDescendant._id !== this._id) return Promise.resolve(null);
    var prefix = this._path, path = possibleDescendant._path;
    for (var i = 0; i < prefix.length; i++) if (path[i] !== prefix[i]) return Promise.resolve(null);
    return Promise.resolve(path.slice(prefix.length));
  };
  function iterate(parent, project) {
    var listing = null, index = 0;
    var iterator = {
      next: function () {
        if (!listing) listing = fs('list', { id: parent._id, path: parent._path });
        return listing.then(function (entries) {
          if (index >= entries.length) return { done: true, value: undefined };
          return { done: false, value: project(entries[index++]) };
        });
      },
      'return': function () { index = Infinity; return Promise.resolve({ done: true, value: undefined }); }
    };
    if (typeof Symbol !== 'undefined' && Symbol.asyncIterator) {
      iterator[Symbol.asyncIterator] = function () { return iterator; };
    }
    return iterator;
  }
  DirectoryHandle.prototype.entries = function () {
    var parent = this;
    return iterate(parent, function (info) { return [info.name, child(parent, info)]; });
  };
  DirectoryHandle.prototype.keys = function () {
    return iterate(this, function (info) { return info.name; });
  };
  DirectoryHandle.prototype.values = function () {
    var parent = this;
    return iterate(parent, function (info) { return child(parent, info); });
  };
  if (typeof Symbol !== 'undefined' && Symbol.asyncIterator) {
    DirectoryHandle.prototype[Symbol.asyncIterator] = DirectoryHandle.prototype.entries;
  }

  global.FileSystemHandle = Handle;
  global.FileSystemFileHandle = FileHandle;
  global.FileSystemDirectoryHandle = DirectoryHandle;

  Pebble.fileSystem = Object.freeze({
    /** The handle behind an id the settings page stored for you. */
    getHandle: function (id) {
      return fs('getHandle', { id: String(id) }).then(function (grant) {
        return grant.kind === 'directory'
          ? new DirectoryHandle(grant.id, grant.name, [])
          : new FileHandle(grant.id, grant.name, []);
      });
    },
    /** [{ id, kind, name, mode }] for everything this plugin has been granted. */
    grants: function () { return fs('grants', {}); }
  });
})(globalThis);
"""
    }
}
