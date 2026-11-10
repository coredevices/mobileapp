package io.rebble.libpebblecommon.js

import kotlinx.coroutines.CoroutineScope
import kotlin.uuid.Uuid

/** `navigator.geolocation` for a [JsEngine], as PKJS has it. Install [GEOLOCATION_JS] after it. */
class EngineGeolocation(
    scope: CoroutineScope,
    eval: suspend (String) -> Unit,
    appUuid: Uuid,
    prompt: Boolean,
) : GeolocationInterface(scope, eval, appUuid, prompt), JsEngineInterface, AutoCloseable {
    override val name = "_PebbleGeo"
    override val methods = listOf("getCurrentPosition", "watchPosition", "clearWatch", "getRequestCallbackID", "getWatchCallbackID")

    override fun dispatch(method: String, args: List<Any?>): Any? = dispatchCall(method, args)

    override fun close() = clearAllWatches()
}

internal const val GEOLOCATION_JS = """
(function (global) {
  var requests = new Map();
  var watches = new Map();
  function position(latitude, longitude, accuracy, altitude, heading, speed) {
    return { coords: { latitude: latitude, longitude: longitude, accuracy: accuracy, altitude: altitude, heading: heading, speed: speed } };
  }
  global._PebbleGeoCB = {
    _resultGetSuccess: function (id, latitude, longitude, accuracy, altitude, heading, speed) {
      var callback = requests.get(id);
      requests.delete(id);
      if (callback && callback.success) callback.success(position(latitude, longitude, accuracy, altitude, heading, speed));
    },
    _resultGetError: function (id, message) {
      var callback = requests.get(id);
      requests.delete(id);
      if (callback && callback.error) callback.error({ message: message, code: 1 });
    },
    _resultWatchSuccess: function (id, latitude, longitude, accuracy, altitude, heading, speed) {
      var callback = watches.get(id);
      if (callback && callback.success) callback.success(position(latitude, longitude, accuracy, altitude, heading, speed));
    },
    _resultWatchError: function (id, message) {
      var callback = watches.get(id);
      if (callback && callback.error) callback.error({ message: message, code: 1 });
    },
  };
  var geolocation = {
    getCurrentPosition: function (success, error, options) {
      var id = _PebbleGeo.getRequestCallbackID();
      requests.set(id, { success: success, error: error });
      var maxAge = (options && typeof options.maximumAge === 'number') ? options.maximumAge : -1;
      var timeout = (options && typeof options.timeout === 'number') ? options.timeout : -1;
      _PebbleGeo.getCurrentPosition(id, maxAge, timeout, (options && options.enableHighAccuracy) ? 1 : 0);
    },
    watchPosition: function (success, error, options) {
      var id = _PebbleGeo.getWatchCallbackID();
      watches.set(id, { success: success, error: error });
      var interval = (options && options.frequency) ? options.frequency : 500;
      _PebbleGeo.watchPosition(id, interval, (options && options.enableHighAccuracy) ? 1 : 0);
      return id;
    },
    clearWatch: function (id) {
      _PebbleGeo.clearWatch(id);
      watches.delete(id);
    },
  };
  // defineProperty: the Android engine locks `navigator` down to a throwing getter.
  Object.defineProperty(global, 'navigator', { value: { geolocation: geolocation }, configurable: true });
})(globalThis);
"""
