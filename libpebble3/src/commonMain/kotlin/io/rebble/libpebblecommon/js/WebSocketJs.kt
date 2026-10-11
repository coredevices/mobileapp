package io.rebble.libpebblecommon.js

/**
 * `WebSocket` backed by [WebSocketManager], for every context whose engine has none of its own:
 * PKJS on iOS and plugins everywhere. Binary frames cross as base64 and need [BASE64_JS]
 * evaluated first. `binaryType` is always `'arraybuffer'`: there is no `Blob`.
 */
internal const val WEBSOCKET_JS = """
(function (global) {
  // defineProperty, not assignment: the Android engine locks `WebSocket` down to a throwing
  // getter so the WebView's own never leaks, and a plain assignment onto a getter is dropped.
  function install(name, value) {
    Object.defineProperty(global, name, {
      value: value, writable: true, configurable: true, enumerable: false,
    });
  }

  function bytesToBase64(bytes) {
    var binary = '';
    for (var i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
    return btoa(binary);
  }

  function base64ToBuffer(base64) {
    var binary = atob(base64);
    var bytes = new Uint8Array(binary.length);
    for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes.buffer;
  }

  function WebSocket(url, protocols) {
    if (!url) throw new SyntaxError('WebSocket: URL is required');
    this.url = String(url);
    this.readyState = WebSocket.CONNECTING;
    this.protocol = '';
    this.extensions = '';
    this.bufferedAmount = 0;
    this.binaryType = 'arraybuffer';
    this.onopen = null;
    this.onmessage = null;
    this.onerror = null;
    this.onclose = null;
    this._listeners = {};

    var protocolStr = '';
    if (Array.isArray(protocols)) {
      protocolStr = protocols.join(',');
    } else if (protocols) {
      protocolStr = String(protocols);
    }
    this._id = _WebSocketManager.createInstance(this.url, protocolStr);
    WebSocket._instances.set(this._id, this);
  }

  WebSocket.CONNECTING = 0;
  WebSocket.OPEN = 1;
  WebSocket.CLOSING = 2;
  WebSocket.CLOSED = 3;
  WebSocket.prototype.CONNECTING = 0;
  WebSocket.prototype.OPEN = 1;
  WebSocket.prototype.CLOSING = 2;
  WebSocket.prototype.CLOSED = 3;

  // What the host evaluates into: `WebSocket._instances.get(id)._on*`.
  WebSocket._instances = new Map();

  WebSocket.prototype.send = function (data) {
    if (this.readyState === WebSocket.CONNECTING) {
      throw new Error('InvalidStateError: WebSocket is still connecting');
    }
    if (this.readyState !== WebSocket.OPEN) return;
    if (data instanceof ArrayBuffer) {
      _WebSocketManager.send(this._id, bytesToBase64(new Uint8Array(data)), true);
    } else if (ArrayBuffer.isView(data)) {
      _WebSocketManager.send(
        this._id, bytesToBase64(new Uint8Array(data.buffer, data.byteOffset, data.byteLength)), true);
    } else {
      _WebSocketManager.send(this._id, String(data), false);
    }
  };

  WebSocket.prototype.close = function (code, reason) {
    if (this.readyState === WebSocket.CLOSING || this.readyState === WebSocket.CLOSED) return;
    this.readyState = WebSocket.CLOSING;
    _WebSocketManager.close(this._id, code || 1000, reason || '');
  };

  WebSocket.prototype.addEventListener = function (type, listener) {
    if (!this._listeners[type]) this._listeners[type] = [];
    this._listeners[type].push(listener);
  };

  WebSocket.prototype.removeEventListener = function (type, listener) {
    var listeners = this._listeners[type];
    if (!listeners) return;
    var index = listeners.indexOf(listener);
    if (index !== -1) listeners.splice(index, 1);
  };

  WebSocket.prototype._onOpen = function (protocol) {
    this.protocol = protocol || '';
    this.readyState = WebSocket.OPEN;
    this._dispatchEvent('open', { type: 'open', target: this });
  };

  WebSocket.prototype._onMessage = function (data, isBinary) {
    this._dispatchEvent('message', {
      type: 'message',
      target: this,
      data: isBinary ? base64ToBuffer(data) : data,
      origin: this.url,
      lastEventId: '',
      source: null,
      ports: [],
    });
  };

  WebSocket.prototype._onClose = function (code, reason, wasClean) {
    this.readyState = WebSocket.CLOSED;
    WebSocket._instances.delete(this._id);
    this._dispatchEvent('close', {
      type: 'close', target: this, code: code, reason: reason, wasClean: wasClean,
    });
  };

  WebSocket.prototype._onError = function () {
    this._dispatchEvent('error', { type: 'error', target: this });
  };

  WebSocket.prototype._dispatchEvent = function (type, event) {
    var listeners = this._listeners[type];
    if (listeners) {
      listeners.slice().forEach(function (listener) { listener(event); });
    }
    var handler = this['on' + type];
    if (typeof handler === 'function') handler.call(this, event);
  };

  install('WebSocket', WebSocket);
})(globalThis);
"""
