package io.rebble.libpebblecommon.js

/**
 * `atob` / `btoa`, which JavaScriptCore does not have — they are browser APIs, so PKJS on
 * Android gets them from the WebView and PKJS on iOS gets nothing. The plugin API hands bitmaps
 * over as base64, so a consumer that decodes one has to work on both.
 */
internal const val BASE64_JS = """
(function (global) {
  if (typeof global.atob === 'function' && typeof global.btoa === 'function') return;

  var CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  var VALUES = {};
  for (var i = 0; i < CHARS.length; i++) VALUES[CHARS.charAt(i)] = i;

  /** Binary string (one byte per char) -> base64. */
  global.btoa = function (input) {
    var binary = String(input);
    var out = '';
    for (var i = 0; i < binary.length; i += 3) {
      var a = binary.charCodeAt(i);
      var b = binary.charCodeAt(i + 1);
      var c = binary.charCodeAt(i + 2);
      if (a > 255 || b > 255 || c > 255) throw new Error('btoa: not a binary string');
      var chunk = (a << 16) | ((b || 0) << 8) | (c || 0);
      out += CHARS.charAt((chunk >> 18) & 63) +
             CHARS.charAt((chunk >> 12) & 63) +
             (isNaN(b) ? '=' : CHARS.charAt((chunk >> 6) & 63)) +
             (isNaN(c) ? '=' : CHARS.charAt(chunk & 63));
    }
    return out;
  };

  /** base64 -> binary string. */
  global.atob = function (input) {
    var base64 = String(input).replace(/[\s=]/g, '');
    if (base64.length % 4 === 1) throw new Error('atob: bad base64');
    var out = '';
    var bits = 0;
    var held = 0;
    for (var i = 0; i < base64.length; i++) {
      var value = VALUES[base64.charAt(i)];
      if (value === undefined) throw new Error('atob: bad base64');
      bits = (bits << 6) | value;
      held += 6;
      if (held >= 8) {
        held -= 8;
        out += String.fromCharCode((bits >> held) & 255);
      }
    }
    return out;
  };
})(typeof globalThis !== 'undefined' ? globalThis : this);
"""
