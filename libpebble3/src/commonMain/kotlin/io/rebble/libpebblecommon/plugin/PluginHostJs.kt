package io.rebble.libpebblecommon.plugin

/**
 * The JS shim evaluated into every plugin session before the plugin's own script. Defines the
 * surface a plugin author writes against — `Pebble.registerSourceHandler`,
 * `Pebble.registerActionHandler` and `console`. `XMLHttpRequest` and `localStorage` come from
 * the engine itself and are already in scope — a plugin persists state exactly the way a PKJS
 * app does, in the same store.
 *
 * Kept as a string rather than an asset so both platforms get it identically with no bundling
 * step; only the plugin's own script is loaded from disk.
 */
internal const val PLUGIN_HOST_JS = """
(function (global) {
  var sourceHandler = null;
  var actionHandler = null;
  var configHandler = null;

  global.console = {
    log:   function () { _JsBridge.log('info',  join(arguments)); },
    info:  function () { _JsBridge.log('info',  join(arguments)); },
    warn:  function () { _JsBridge.log('warn',  join(arguments)); },
    error: function () { _JsBridge.log('error', join(arguments)); },
  };

  function join(args) {
    var parts = [];
    for (var i = 0; i < args.length; i++) {
      var a = args[i];
      parts.push(typeof a === 'string' ? a : safeStringify(a));
    }
    return parts.join(' ');
  }

  function safeStringify(v) {
    try { return JSON.stringify(v); } catch (e) { return String(v); }
  }

  global.Pebble = global.Pebble || {};
  global.Pebble.registerSourceHandler = function (fn) { sourceHandler = fn; };
  global.Pebble.registerActionHandler = function (fn) { actionHandler = fn; };

  /**
   * Messages from this plugin's config page, while it is open. Nothing else can reach this —
   * it is the plugin's own conversation with its own settings UI, so the payloads are whatever
   * the two of them agree on.
   */
  global.Pebble.registerConfigHandler = function (fn) { configHandler = fn; };

  /**
   * Report `"<category>/<item>"` sources as stale, so anything watching them re-reads now
   * rather than at the end of its poll interval. For changes made outside a source or action
   * request — the config page adding a stock, say.
   */
  global.Pebble.refreshSources = function (keys) {
    _JsBridge.refreshSources(JSON.stringify(keys || []));
  };

  /** Push at the open config page. No reply. */
  global.Pebble.sendConfigMessage = function (message) {
    _JsBridge.configMessage(JSON.stringify(message === undefined ? null : message));
  };

  global.__pluginConfigMessage = function (requestId, message) {
    var settled = false;
    function respond(reply) {
      if (settled) return;
      settled = true;
      _JsBridge.respond(requestId, JSON.stringify(reply === undefined ? null : reply));
    }
    if (!configHandler) {
      respond({ error: 'no config handler registered' });
      return;
    }
    try {
      var maybePromise = configHandler(message, respond);
      if (maybePromise && typeof maybePromise.catch === 'function') {
        maybePromise.catch(function (e) { respond({ error: String(e) }); });
      }
    } catch (e) {
      respond({ error: String(e) });
    }
  };

  global.__pluginDispatch = function (requestId, request) {
    var settled = false;
    function send(payload) {
      if (settled) return;
      settled = true;
      _JsBridge.respond(requestId, JSON.stringify(payload));
    }
    var respond = {
      data: function (envelope) { send(envelope || { instances: [] }); },
      ok: function (result) {
        result = result || {};
        send({ ok: true, text: result.text || null, refreshed: result.refreshed || [] });
      },
      error: function (code, message) {
        if (request.kind === 'action') {
          send({ ok: false, code: code || 'UNKNOWN', message: message || null });
        } else {
          send({ error: (code || 'UNKNOWN') + (message ? ': ' + message : '') });
        }
      },
    };

    try {
      var handler = request.kind === 'action' ? actionHandler : sourceHandler;
      if (!handler) {
        respond.error('PLUGIN_UNAVAILABLE', 'no ' + request.kind + ' handler registered');
        return;
      }
      var maybePromise = handler(request, respond);
      if (maybePromise && typeof maybePromise.catch === 'function') {
        maybePromise.catch(function (e) { respond.error('UNKNOWN', String(e)); });
      }
    } catch (e) {
      respond.error('UNKNOWN', String(e));
    }
  };
})(this);
"""
