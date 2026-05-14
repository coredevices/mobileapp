globalThis._LibPebbleTimeoutCallbacks = new Map();
globalThis._LibPebbleTriggerTimeout = function (timeoutId) {
    if (globalThis._LibPebbleTimeoutCallbacks.has(timeoutId)) {
        const { callback, args } = globalThis._LibPebbleTimeoutCallbacks.get(timeoutId);
        callback(...args);
        globalThis._LibPebbleTimeoutCallbacks.delete(timeoutId);
    }
}
globalThis._LibPebbleTriggerInterval = function (intervalId) {
    if (globalThis._LibPebbleTimeoutCallbacks.has(intervalId)) {
        const { callback, args } = globalThis._LibPebbleTimeoutCallbacks.get(intervalId);
        callback(...args);
    }
}
// Match browser behavior: a string callback is compiled with `new Function`,
// and any other non-function value is coerced to a string and compiled the
// same way (so `setTimeout(undefined, 0)` is a no-op rather than a TypeError).
globalThis._LibPebbleCoerceTimerCallback = function (callback) {
    if (typeof callback === 'function') return callback;
    return new Function(String(callback));
}
// Match browser behavior: missing/NaN/negative/non-numeric delays clamp to 0.
globalThis._LibPebbleCoerceTimerDelay = function (delay) {
    const n = Number(delay);
    return (Number.isFinite(n) && n > 0) ? n : 0;
}
globalThis.setTimeout = function (callback, delay, ...args) {
    callback = globalThis._LibPebbleCoerceTimerCallback(callback);
    const timeoutId = _Timeout.setTimeout(globalThis._LibPebbleCoerceTimerDelay(delay));
    globalThis._LibPebbleTimeoutCallbacks.set(timeoutId, { callback, args });
    return timeoutId;
}
globalThis.clearTimeout = function (timeoutId) {
    if (timeoutId === undefined || timeoutId === null) {
        return;
    }
    if (typeof timeoutId !== 'number') {
        throw new TypeError('First argument must be a number');
    }

    if (globalThis._LibPebbleTimeoutCallbacks.has(timeoutId)) {
        globalThis._Timeout.clearTimeout(timeoutId);
        globalThis._LibPebbleTimeoutCallbacks.delete(timeoutId);
    }
}
globalThis.setInterval = function (callback, delay, ...args) {
    callback = globalThis._LibPebbleCoerceTimerCallback(callback);
    const intervalId = _Timeout.setInterval(globalThis._LibPebbleCoerceTimerDelay(delay));
    globalThis._LibPebbleTimeoutCallbacks.set(intervalId, { callback, args });
    return intervalId;
}
globalThis.clearInterval = function (intervalId) {
    if (intervalId === undefined || intervalId === null) {
        return;
    }
    if (typeof intervalId !== 'number') {
        throw new TypeError('First argument must be a number');
    }

    if (globalThis._LibPebbleTimeoutCallbacks.has(intervalId)) {
        globalThis._Timeout.clearInterval(intervalId);
        globalThis._LibPebbleTimeoutCallbacks.delete(intervalId);
    }
}