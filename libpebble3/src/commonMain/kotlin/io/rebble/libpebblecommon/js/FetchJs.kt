package io.rebble.libpebblecommon.js

/** The JS object [XMLHTTPRequestManager] delivers a plugin's responses into. */
internal const val FETCH_REGISTRY = "_PluginFetch"

/**
 * `fetch` for plugins, talking to `_XMLHTTPRequestManager` directly — the same host-backed
 * request path PKJS uses, so the network permission is still enforced in one place, but without
 * an `XMLHttpRequest` in front of it. Plugins are new API surface with nothing to stay
 * compatible with, and a plugin that can't reach XHR can't accidentally depend on it.
 *
 * A useful subset rather than the whole spec: no streaming bodies, no `Request`, and no `mode` /
 * `credentials` / `cache` — a plugin has no origin or cookie jar for any of it to mean anything.
 * `AbortSignal` is honoured. Binary responses need `responseType: 'arraybuffer'` on the request;
 * there is no sniffing, so a text response is never re-encoded behind your back.
 */
internal const val FETCH_JS = """
(function (global) {
  function Headers(init) {
    this._entries = [];
    if (!init) return;
    var self = this;
    if (init instanceof Headers) {
      init.forEach(function (value, name) { self.append(name, value); });
    } else if (Array.isArray(init)) {
      init.forEach(function (pair) { self.append(pair[0], pair[1]); });
    } else {
      Object.keys(init).forEach(function (name) { self.append(name, init[name]); });
    }
  }

  Headers.prototype.append = function (name, value) {
    this._entries.push([String(name).toLowerCase(), String(value)]);
  };
  Headers.prototype.set = function (name, value) {
    var key = String(name).toLowerCase();
    this._entries = this._entries.filter(function (e) { return e[0] !== key; });
    this.append(key, value);
  };
  Headers.prototype.get = function (name) {
    var key = String(name).toLowerCase();
    var hit = this._entries.filter(function (e) { return e[0] === key; });
    // Per spec, a repeated header reads back as one comma-joined value.
    return hit.length ? hit.map(function (e) { return e[1]; }).join(', ') : null;
  };
  Headers.prototype.has = function (name) { return this.get(name) !== null; };
  Headers.prototype.forEach = function (callback, thisArg) {
    var self = this;
    this._entries.slice().forEach(function (e) { callback.call(thisArg, e[1], e[0], self); });
  };
  Headers.prototype.keys = function () { return this._entries.map(function (e) { return e[0]; }); };
  Headers.prototype.entries = function () { return this._entries.slice(); };

  function Response(body, init) {
    this._body = body;
    this.status = init.status;
    this.statusText = init.statusText || '';
    this.headers = init.headers;
    this.url = init.url || '';
    this.ok = this.status >= 200 && this.status < 300;
    this.bodyUsed = false;
  }

  Response.prototype._consume = function () {
    if (this.bodyUsed) return Promise.reject(new TypeError('body already read'));
    this.bodyUsed = true;
    return Promise.resolve(this._body);
  };
  Response.prototype.text = function () {
    return this._consume().then(function (body) { return body === null ? '' : String(body); });
  };
  Response.prototype.json = function () { return this.text().then(JSON.parse); };
  /** Bytes, for a request that asked for `responseType: 'arraybuffer'`. */
  Response.prototype.arrayBuffer = function () {
    return this._consume().then(function (body) {
      if (body instanceof Uint8Array) return body.buffer;
      var text = body === null ? '' : String(body);
      var bytes = new Uint8Array(text.length);
      for (var i = 0; i < text.length; i++) bytes[i] = text.charCodeAt(i) & 0xff;
      return bytes.buffer;
    });
  };

  // What the host evaluates into: `<FETCH_REGISTRY>._instances.get(id)`, one entry per request
  // in flight. The shape is the host's contract — readyState, _dispatchEvent, _onResponseComplete.
  var registry = { _instances: new Map() };
  // defineProperty, not assignment: the Android engine locks `fetch` down to a throwing getter
  // so the WebView's own never leaks, and a plain assignment onto a getter is silently dropped.
  function install(name, value) {
    Object.defineProperty(global, name, {
      value: value, writable: true, configurable: true, enumerable: false,
    });
  }
  install('$FETCH_REGISTRY', registry);

  function Pending(id, responseType, url) {
    this.readyState = 0;
    this._id = id;
    this._responseType = responseType;
    this._url = url;
    this._settled = false;
  }

  Pending.prototype._onResponseComplete = function (headers, status, statusText, body) {
    var decoded = body;
    if (this._responseType === 'arraybuffer' && typeof body === 'string') {
      var binary = atob(body);
      decoded = new Uint8Array(binary.length);
      for (var i = 0; i < binary.length; i++) decoded[i] = binary.charCodeAt(i);
    }
    var responseHeaders = new Headers();
    if (headers) {
      Object.keys(headers).forEach(function (name) { responseHeaders.append(name, headers[name]); });
    }
    this._response = new Response(decoded, {
      status: status,
      statusText: statusText,
      headers: responseHeaders,
      url: this._url,
    });
  };

  Pending.prototype._dispatchEvent = function (type) {
    if (this._settled) return;
    if (type === 'load' && this._response) {
      this._settled = true;
      registry._instances.delete(this._id);
      this._resolve(this._response);
    } else if (type === 'error' || type === 'timeout' || type === 'abort') {
      this._settled = true;
      registry._instances.delete(this._id);
      // A blocked host, a DNS failure and a dropped connection are one thing to a caller: the
      // request did not happen. A non-2xx is a resolved Response with ok === false.
      this._reject(new TypeError('fetch ' + (type === 'error' ? 'failed' : type) + ': ' + this._url));
    }
  };

  install('Headers', Headers);
  install('Response', Response);

  install('fetch', function (url, options) {
    options = options || {};
    var target = String(url);
    var responseType = options.responseType === 'arraybuffer' ? 'arraybuffer' : '';

    return new Promise(function (resolve, reject) {
      var id = _XMLHTTPRequestManager.getXHRInstanceID();
      var pending = new Pending(id, responseType, target);
      pending._resolve = resolve;
      pending._reject = reject;
      registry._instances.set(id, pending);

      _XMLHTTPRequestManager.open(id, options.method || 'GET', target, true, '', '');
      if (options.headers) {
        new Headers(options.headers).forEach(function (value, name) {
          _XMLHTTPRequestManager.setRequestHeader(id, name, value);
        });
      }
      var signal = options.signal;
      if (signal) {
        if (signal.aborted) {
          pending._dispatchEvent('abort');
          return;
        }
        if (typeof signal.addEventListener === 'function') {
          signal.addEventListener('abort', function () { _XMLHTTPRequestManager.abort(id); });
        }
      }
      _XMLHTTPRequestManager.send(id, responseType, options.body === undefined ? null : options.body);
    });
  });
})(globalThis);
"""
