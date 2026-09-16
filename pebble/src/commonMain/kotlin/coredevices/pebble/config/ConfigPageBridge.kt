package coredevices.pebble.config

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The native half of a config page's `Pebble` API.
 *
 * Installed when the WebView is built rather than after it loads: a page's own `<script>` runs
 * while the document is still parsing, so anything injected on page-finished arrives too late
 * for it to use.
 */
class ConfigPageBridge(
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("ConfigPageBridge")

    /** Set as the page's scripts are resolved; the bridge itself outlives them. */
    var scripts: Map<String, ConfigScript> = emptyMap()

    private val readyTargets = mutableSetOf<String>()

    /**
     * Set when the WebView is built. Evaluating straight into it rather than through
     * [com.multiplatform.webview.web.WebViewNavigator] keeps ordering: the navigator's event
     * flow only replays one event, so a burst sent before the page is up loses all but the last.
     */
    private var eval: ((String) -> Unit)? = null

    fun attach(eval: (String) -> Unit) {
        this.eval = eval
    }

    /** Everything the page sends, as one JSON string. */
    fun onCall(json: String) {
        val call = try {
            Json.decodeFromString(Call.serializer(), json)
        } catch (e: Exception) {
            logger.w(e) { "malformed call from config page" }
            return
        }
        // Payloads can carry API keys, so only the shape of the call is logged.
        logger.d { "page -> ${call.method} ${call.target}" }
        when (call.method) {
            // Sent by the shim as the page starts parsing. The answer carries whatever is
            // already up, so a script that became ready before the page existed isn't missed.
            "hello" -> reply(call.id, Json.encodeToString(HELLO, Hello(readyTargets.toList())))
            "targets" -> reply(call.id, Json.encodeToString(TARGETS, scripts.map { (id, script) ->
                Target(id, script.name)
            }))
            "sendMessage" -> {
                val script = scripts[call.target]
                if (script == null) {
                    reply(call.id, error("no script called '${call.target}' (have: ${scripts.keys.joinToString()})"))
                    return
                }
                scope.launch {
                    val answer = script.target.onConfigMessage(call.message.toString())
                        ?: error("${script.name} did not answer")
                    reply(call.id, answer)
                }
            }
            else -> reply(call.id, error("unknown method '${call.method}'"))
        }
    }

    /** A script pushed something at the page. */
    fun push(target: String, json: String) =
        evaluate("window.__pebbleConfig.message(${quote(target)}, ${quote(json)})")

    /** A script is up and will answer messages. */
    fun ready(target: String) {
        logger.d { "ready: $target" }
        readyTargets += target
        evaluate("window.__pebbleConfig.ready(${quote(target)})")
    }

    fun forget(target: String) {
        readyTargets -= target
    }

    private fun reply(id: Int, json: String) =
        evaluate("window.__pebbleConfig.reply($id, ${quote(json)})")

    private fun evaluate(js: String) {
        val eval = eval
        if (eval == null) {
            logger.w { "no webview attached; dropped $js" }
            return
        }
        // A page that hasn't started parsing has no shim to call into, and a bare call would
        // throw inside someone else's config page. Whatever is missed, `hello` replays.
        eval("if (window.__pebbleConfig) { $js; }")
    }

    private fun error(message: String) =
        Json.encodeToString(JsonObject.serializer(), buildJsonObject { put("error", message) })

    private fun quote(value: String) = Json.encodeToString(STRING, value)

    @Serializable
    private data class Call(
        val id: Int,
        val method: String,
        val target: String = "",
        val message: JsonElement = JsonNull,
    )

    @Serializable
    private data class Target(val id: String, val name: String)

    @Serializable
    private data class Hello(val ready: List<String>)

    private companion object {
        val TARGETS = ListSerializer(Target.serializer())
        val HELLO = Hello.serializer()
        val STRING = String.serializer()
    }
}

/** The name the page-side shim calls into. */
const val CONFIG_NATIVE_NAMESPACE = "_PebbleConfigNative"

/**
 * The page-side `Pebble` API, evaluated before any of the page's own script. Pages wait for a
 * `ready` event per script rather than assuming one is up: a plugin is only started when its
 * page opens.
 */
val CONFIG_PAGE_SHIM_JS = """
(function (global) {
  var nextId = 1;
  var pending = {};
  var listeners = { message: [], ready: [] };
  var readyTargets = [];

  // Android exposes the native side as a global object; WebKit as a message handler.
  function post(payload) {
    var android = global.$CONFIG_NATIVE_NAMESPACE;
    if (android && android.call) return android.call(payload);
    var handlers = global.webkit && global.webkit.messageHandlers;
    if (handlers && handlers.$CONFIG_NATIVE_NAMESPACE) {
      return handlers.$CONFIG_NATIVE_NAMESPACE.postMessage(payload);
    }
    throw new Error('no native config bridge');
  }

  function call(method, target, message) {
    var id = nextId++;
    return new Promise(function (resolve) {
      pending[id] = resolve;
      post(JSON.stringify({
        id: id,
        method: method,
        target: target === undefined ? '' : target,
        message: message === undefined ? null : message,
      }));
    });
  }

  function emit(type, event) {
    listeners[type].forEach(function (callback) { callback(event); });
  }

  global.Pebble = global.Pebble || {};

  /** Send to one of this pbw's scripts: 'pkjs' or 'plugin'. */
  global.Pebble.sendMessage = function (target, message) {
    if (typeof target !== 'string') {
      return Promise.reject(new Error('sendMessage(target, message): target is required'));
    }
    return call('sendMessage', target, message);
  };

  /** [{ id, name }] — what this page is allowed to talk to. */
  global.Pebble.targets = function () { return call('targets'); };

  /** Scripts that have already reported ready, for a page that listens late. */
  global.Pebble.readyTargets = function () { return readyTargets.slice(); };

  global.Pebble.addEventListener = function (type, callback) {
    if (!listeners[type] || typeof callback !== 'function') return;
    listeners[type].push(callback);
    if (type === 'ready') {
      readyTargets.forEach(function (target) { callback({ type: 'ready', target: target }); });
    }
  };

  global.Pebble.removeEventListener = function (type, callback) {
    if (!listeners[type]) return;
    var index = listeners[type].indexOf(callback);
    if (index !== -1) listeners[type].splice(index, 1);
  };

  global.__pebbleConfig = {
    reply: function (id, json) {
      var resolve = pending[id];
      if (!resolve) return;
      delete pending[id];
      try { resolve(JSON.parse(json)); } catch (e) { resolve({ error: 'bad reply' }); }
    },
    message: function (target, json) {
      var data = null;
      try { data = JSON.parse(json); } catch (e) { /* leave null */ }
      emit('message', { type: 'message', target: target, data: data });
    },
    ready: function (target) {
      if (readyTargets.indexOf(target) === -1) readyTargets.push(target);
      emit('ready', { type: 'ready', target: target });
    },
  };
  // Announce ourselves the moment the document starts parsing, and pick up anything that was
  // already running before the page existed.
  call('hello').then(function (hello) {
    (hello && hello.ready ? hello.ready : []).forEach(function (target) {
      global.__pebbleConfig.ready(target);
    });
  });

})(window);
""".trimIndent()
