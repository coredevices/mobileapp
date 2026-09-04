/* This is used in both iOS and Android, so make sure any changes are compatible with both */
const _global = typeof window !== 'undefined' ? window : globalThis;
window = _global; // For compatibility with existing code that expects `window`
_global.onerror = (message, source, lineno, colno, error) => {
    _Pebble.onError(message, source, lineno, colno);
};
_global.onunhandledrejection = (event) => {
    _Pebble.onUnhandledRejection(event.reason);
}
_global.navigator = _global.navigator || {};
_global._PebbleGeoCB = {
    _requestCallbacks: new Map(),
    _watchCallbacks: new Map(),
    _resultGetSuccess: (id, latitude, longitude, accuracy, altitude, heading, speed) => {
        const callback = _PebbleGeoCB._requestCallbacks.get(id);
        if (callback && callback.success) {
            _PebbleGeoCB._requestCallbacks.delete(id);
            callback.success({ coords: { latitude, longitude, accuracy, altitude, heading, speed } });
        }
    },
    _resultGetError: (id, message) => {
        const callback = _PebbleGeoCB._requestCallbacks.get(id);
        if (callback && callback.error) {
            _PebbleGeoCB._requestCallbacks.delete(id);
            callback.error({ message, code: 1 });
        }
    },
    _resultWatchSuccess: (id, latitude, longitude, accuracy, altitude, heading, speed) => {
        const callback = _PebbleGeoCB._watchCallbacks.get(id);
        if (callback && callback.success) {
            callback.success({ coords: { latitude, longitude, accuracy, altitude, heading, speed } });
        }
    },
    _resultWatchError: (id, message) => {
        const callback = _PebbleGeoCB._watchCallbacks.get(id);
        if (callback && callback.error) {
            callback.error({ message, code: 1 });
        }
    }
};
navigator.geolocation.getCurrentPosition = (success, error, options) => {
    const id = _PebbleGeo.getRequestCallbackID();
    _PebbleGeoCB._requestCallbacks.set(id, { success, error });
    const maxAge = (options && typeof options.maximumAge === 'number') ? options.maximumAge : -1;
    const timeout = (options && typeof options.timeout === 'number') ? options.timeout : -1;
    const highAccuracy = (options && options.enableHighAccuracy) ? 1 : 0;
    _PebbleGeo.getCurrentPosition(id, maxAge, timeout, highAccuracy);
};
navigator.geolocation.watchPosition = (success, error, options) => {
    const id = _PebbleGeo.getWatchCallbackID();
    _PebbleGeoCB._watchCallbacks.set(id, { success, error });
    const interval = options && options.frequency ? options.frequency : 500;
    const highAccuracy = (options && options.enableHighAccuracy) ? 1 : 0;
    _PebbleGeo.watchPosition(id, interval, highAccuracy);
    return id;
};
navigator.geolocation.clearWatch = (id) => {
    _PebbleGeo.clearWatch(id);
    if (_PebbleGeoCB._watchCallbacks.has(id)) {
        _PebbleGeoCB._watchCallbacks.delete(id);
    }
};

((global) => {
    const oldConsole = {
        log: console.log,
        warn: console.warn,
        error: console.error,
        info: console.info,
        debug: console.debug,
    }
    const sendLog = (level, ...args) => {
        // build args into a single string
        const message = args.map((arg) => {
            if (arg instanceof Error) {
                return "\n" + JSON.stringify({
                    message: arg.message,
                    lineNumber: arg.lineNumber,
                    columnNumber: arg.columnNumber,
                    name: arg.name,
                    stack: arg.stack,
                }, null, 2);
            } else if (typeof arg === 'object') {
                try {
                    return JSON.stringify(arg);
                } catch (e) {
                    return '[object]';
                }
            } else {
                return String(arg);
            }
        }).join(' ');
        const traceback = new Error().stack;
        _Pebble.onConsoleLog(level, message, traceback);
    }
    console.log = (...args) => {
        oldConsole.log.apply(console, args);
        sendLog('log', ...args);
    }
    console.warn = (...args) => {
        oldConsole.warn.apply(console, args);
        sendLog('warn', ...args);
    }
    console.error = (...args) => {
        oldConsole.error.apply(console, args);
        sendLog('error', ...args);
    }
    console.info = (...args) => {
        oldConsole.info.apply(console, args);
        sendLog('info', ...args);
    }
    console.debug = (...args) => {
        oldConsole.debug.apply(console, args);
        sendLog('debug', ...args);
    }
    console.trace = (...args) => {
        oldConsole.trace.apply(console, args);
        const message = args.map(arg => typeof arg === 'object' ? JSON.stringify(arg) : String(arg)).join(' ');
        const traceback = new Error().stack;
        const tracebackWithoutThis = traceback ? traceback.split('\n').slice(2).join('\n') : null;
        _Pebble.onConsoleLog('trace', message, "\n"+tracebackWithoutThis);
    }
    console.assert = (condition, ...args) => {
        if (!condition) {
            const message = "Assertion failed:" + args.map(arg => typeof arg === 'object' ? JSON.stringify(arg) : String(arg)).join(' ');
            const traceback = new Error().stack;
            const caller = traceback;
            _Pebble.onConsoleLog('assert', message, caller);
        }
    }
    const PebbleEventTypes = {
        READY: 'ready',
        SHOW_CONFIGURATION: 'showConfiguration',
        WEBVIEW_OPENED: 'webviewopened',
        WEBVIEW_CLOSED: 'webviewclosed',
        APP_MESSAGE: 'appmessage',
        APP_MESSAGE_ACK: 'appmessage_ack',
        APP_MESSAGE_NACK: 'appmessage_nack',
        GET_TIMELINE_TOKEN_SUCCESS: 'getTimelineTokenSuccess',
        GET_TIMELINE_TOKEN_FAILURE: 'getTimelineTokenFailure',
        CONFIG_MESSAGE: 'configmessage',
    };
    Object.freeze(PebbleEventTypes);
    const DEFAULT_TIMEOUT = 5000; // 5 seconds

    class PebbleEventListener {
        constructor() {
            this.events = new Map();
            this._eventInitializers = {};
        }

        addEventListener(type, callback, useCapture /* ignored */) {
            if (typeof callback !== 'function') {
                console.warn(`Pebble JS Bridge: addEventListener called with non-function callback for type "${type}"`);
                return;
            }

            if (!this.events.has(type)) {
                this.events.set(type, new Set());

                // Call the event initializer if this is the first time
                if (typeof this._eventInitializers[type] === 'function') {
                    try {
                        this._eventInitializers[type]();
                    } catch(e) {
                        console.error(`Pebble JS Bridge: Error in event initializer for "${type}"`, e);
                    }
                }
            }
            this.events.get(type).add(callback);
        }

        removeEventListener(type, callback) {
            const listeners = this.events.get(type);
            if (!listeners) {
                return;
            }
            listeners.delete(callback);
            if (listeners.size === 0) {
                this.events.delete(type);
            }
        }

        dispatchEvent(event) {
            const listeners = this.events.get(event.type);
            if (!listeners || listeners.size === 0) {
                return false; // Indicate no listeners were called
            }

            // Clone the listeners to avoid modifying the set while iterating
            const listenersCopy = [...listeners];
            let allSucceeded = true;

            listenersCopy.forEach(listener => {
                try {
                    const removeListener = listener(event);
                    if (removeListener === true) {
                        listeners.delete(listener);
                    }
                } catch (e) {
                    console.error(`Pebble JS Bridge: Error in listener for event "${event.type}"`, e);
                    allSucceeded = false;
                }
            });
             if (listeners.size === 0) {
                this.events.delete(event.type);
            }
            return allSucceeded;
        }
    }

    const pebbleEventHandler = new PebbleEventListener();
    const appMessageAckCallbacks = new Map();
    const appMessageNackCallbacks = new Map();

    // Source subscriptions (see new-plugin-api.md). subscriptionId is minted by the JS
    // side as a monotonically increasing integer; host echoes it back on every update.
    const sourceSubscriptions = new Map();
    let sourceSubscriptionIdCounter = 0;

    // Pending Pebble.invokeAction calls, keyed by the id echoed back by the host.
    const actionCalls = new Map();
    let actionCallIdCounter = 0;

    const dispatchPebbleEvent = (type, detail = {}) => {
        const event = {type: type, bubbles: false, cancelable: false};
        Object.assign(event, detail);
        return pebbleEventHandler.dispatchEvent(event);
    };
    const removeAppMessageCallbacksForTransactionId = (tid) => {
        const ackCallback = appMessageAckCallbacks.get(tid);
        if (ackCallback) {
            pebbleEventHandler.removeEventListener('appmessage_ack', ackCallback);
            appMessageAckCallbacks.delete(tid);
        }

        const nackCallback = appMessageNackCallbacks.get(tid);
        if (nackCallback) {
            pebbleEventHandler.removeEventListener('appmessage_nack', nackCallback);
            appMessageNackCallbacks.delete(tid);
        }
    }

    global.signalWebviewOpenedEvent = (data) => {
        dispatchPebbleEvent(PebbleEventTypes.WEBVIEW_OPENED, { opened: data });
    }
    global.signalWebviewClosedEvent = (data) => {
        dispatchPebbleEvent(PebbleEventTypes.WEBVIEW_CLOSED, { response: data });
    }
    // A message from this app's config page, while it is open. `respond` settles the page's
    // promise; calling it more than once, or not at all, is the app's problem to avoid.
    global.signalConfigMessageEvent = (requestId, data) => {
        let settled = false;
        const respond = (reply) => {
            if (settled) return;
            settled = true;
            try {
                _Pebble.configMessageReply(requestId, JSON.stringify(reply === undefined ? null : reply));
            } catch (e) {
                console.error("Pebble JS Bridge: Error replying to config message", e);
            }
        };
        const delivered = dispatchPebbleEvent(PebbleEventTypes.CONFIG_MESSAGE, { data, respond });
        if (!delivered) {
            respond({ error: 'no configmessage listener registered' });
        }
    }
    global.signalReady = (data) => {
        const success = dispatchPebbleEvent(PebbleEventTypes.READY, { ready: data });
        try {
            _Pebble.privateFnConfirmReadySignal(success);
        } catch (e) {
            console.error("Pebble JS Bridge: Error confirming ready signal", e);
        }
    }
    global.signalNewAppMessageData = (data) => {
        const payload = data ? JSON.parse(data) : {};
        dispatchPebbleEvent(PebbleEventTypes.APP_MESSAGE, { payload });
    }
    global.signalAppMessageAck = (data) => {
        const payload = data ? JSON.parse(data) : {};
        dispatchPebbleEvent(PebbleEventTypes.APP_MESSAGE_ACK, { payload });

        if (payload.data !== undefined && payload.data.transactionId !== undefined) {
            removeAppMessageCallbacksForTransactionId(payload.data.transactionId);
        }
    }
    global._signalSourceUpdate = (subscriptionId, payloadJson) => {
        const sub = sourceSubscriptions.get(subscriptionId);
        if (!sub) return;
        try {
            const envelope = JSON.parse(payloadJson);
            if (sub.onData) sub.onData(envelope);
        } catch (e) {
            console.error("Pebble JS Bridge: Error in source onData callback", e);
        }
    }
    global._signalSourceError = (subscriptionId, errorJson) => {
        const sub = sourceSubscriptions.get(subscriptionId);
        if (!sub) return;
        try {
            const err = JSON.parse(errorJson);
            if (sub.onError) sub.onError(err);
        } catch (e) {
            console.error("Pebble JS Bridge: Error in source onError callback", e);
        }
    }
    global._signalActionResult = (callId, resultJson) => {
        const call = actionCalls.get(callId);
        if (!call) return;
        actionCalls.delete(callId);
        try {
            call(JSON.parse(resultJson));
        } catch (e) {
            call({ ok: false, code: "UNKNOWN", message: "malformed host response" });
        }
    }
    global.signalAppMessageNack = (data) => {
        const payload = data ? JSON.parse(data) : {};
        dispatchPebbleEvent(PebbleEventTypes.APP_MESSAGE_NACK, { payload });

        if (payload.data !== undefined && payload.data.transactionId !== undefined) {
            removeAppMessageCallbacksForTransactionId(payload.data.transactionId);
        }
    }
    global.signalShowConfiguration = () => {
        dispatchPebbleEvent('showConfiguration', {});
        // Legacy event
        dispatchPebbleEvent('settings_webui_allowed', {});
    };
    global.signalTimelineTokenSuccess = (data) => {
        var payload;
        if (typeof data === 'string') {
            // Android
            payload = data ? JSON.parse(data) : {};
        } else {
            // iOS
            payload = data
        }
        dispatchPebbleEvent(PebbleEventTypes.GET_TIMELINE_TOKEN_SUCCESS, { payload });
    };
    global.signalTimelineTokenFailure = (data) => {
        var payload;
        if (typeof data === 'string') {
            // Android
            payload = data ? JSON.parse(data) : {};
        } else {
            // iOS
            payload = data
        }
        dispatchPebbleEvent(PebbleEventTypes.GET_TIMELINE_TOKEN_FAILURE, { payload });
    };

    const PebbleAPI = {
        addEventListener: (type, callback, useCapture) => {
            pebbleEventHandler.addEventListener(type, callback, useCapture);
        },
        removeEventListener: (type, callback) => {
            pebbleEventHandler.removeEventListener(type, callback);
        },
        sendAppMessage: (data, onSuccess, onFailure) => {
            const transactionId = _Pebble.sendAppMessageString(JSON.stringify(data));
            if (transactionId === -1) {
                if (onFailure) {
                    onFailure({error: "Failed to connect to Pebble"});
                }
                return -1;
            }
            if (onSuccess) {
                const callback = (e) => {
                    try {
                        if (e.payload.data.transactionId === transactionId) {
                            onSuccess(e.payload);
                        }
                    } catch (error) {
                        console.error("PKJS Error in app message success callback", error);
                    }
                }
                appMessageAckCallbacks.set(transactionId, callback);
                pebbleEventHandler.addEventListener(PebbleEventTypes.APP_MESSAGE_ACK, callback);
            }
            if (onFailure) {
                const callback = (e) => {
                    try {
                        if (e.payload.data.transactionId === transactionId) {
                            onFailure(e.payload, e.payload.error);
                        }
                    } catch (error) {
                        console.error("PKJS Error in app message failure callback", error);
                    }
                }
                appMessageNackCallbacks.set(transactionId, callback);
                pebbleEventHandler.addEventListener(PebbleEventTypes.APP_MESSAGE_NACK, callback);
            }
            return transactionId;
        },
        getTimelineToken: (onSuccess, onFailure) => {
            const callId = _Pebble.getTimelineTokenAsync();
            const successCallback = (e) => {
                if (e.payload.callId === callId) {
                    onSuccess(e.payload.userToken);
                    pebbleEventHandler.removeEventListener(PebbleEventTypes.GET_TIMELINE_TOKEN_SUCCESS, successCallback);
                    pebbleEventHandler.removeEventListener(PebbleEventTypes.GET_TIMELINE_TOKEN_FAILURE, failureCallback);
                }
            }
            const failureCallback = (e) => {
                if (e.payload.callId === callId) {
                    onFailure();
                    pebbleEventHandler.removeEventListener(PebbleEventTypes.GET_TIMELINE_TOKEN_SUCCESS, successCallback);
                    pebbleEventHandler.removeEventListener(PebbleEventTypes.GET_TIMELINE_TOKEN_FAILURE, failureCallback);
                }
            }
            pebbleEventHandler.addEventListener(PebbleEventTypes.GET_TIMELINE_TOKEN_SUCCESS, successCallback);
            pebbleEventHandler.addEventListener(PebbleEventTypes.GET_TIMELINE_TOKEN_FAILURE, failureCallback);
        },
        timelineSubscribe: (token, onSuccess, onFailure) => {
            //TODO
        },
        timelineUnsubscribe: (token, onSuccess, onFailure) => {
            //TODO
        },
        timelineSubscriptions: (onSuccess, onFailure) => {
            //TODO
        },
        getActiveWatchInfo: () => {
            const data = _Pebble.getActivePebbleWatchInfo();
            return data ? JSON.parse(data) : null;
        },
        appGlanceReload: (appGlanceSlices, onSuccess, onFailure) => {
            //TODO
        },
        insertTimelinePin: (pin) => {
            var pinString;
            if (typeof pin === 'string') {
                pinString = pin;
            } else {
                pinString = JSON.stringify(pin);
            }
            _Pebble.insertTimelinePin(pinString);
        },
        deleteTimelinePin: (id) => {
            _Pebble.deleteTimelinePin(id);
        },
        subscribeToSource: (config) => {
            if (!config || typeof config !== 'object') {
                throw new Error("Pebble.subscribeToSource: config object required");
            }
            if (!config.category || !config.item) {
                throw new Error("Pebble.subscribeToSource: category and item required");
            }
            const subscriptionId = ++sourceSubscriptionIdCounter;
            sourceSubscriptions.set(subscriptionId, {
                onData: config.onData,
                onError: config.onError,
            });
            const request = {
                category: config.category,
                item: config.item,
                properties: config.properties,
                plugin: config.plugin,
                maxStalenessMs: config.maxStalenessMs,
                iconPixelSize: config.iconPixelSize,
            };
            try {
                _Pebble.subscribeToSource(subscriptionId, JSON.stringify(request));
            } catch (e) {
                sourceSubscriptions.delete(subscriptionId);
                throw e;
            }
            return {
                unsubscribe: () => {
                    if (!sourceSubscriptions.has(subscriptionId)) return;
                    sourceSubscriptions.delete(subscriptionId);
                    try {
                        _Pebble.unsubscribeSource(subscriptionId);
                    } catch (e) {
                        console.error("Pebble JS Bridge: unsubscribe failed", e);
                    }
                },
            };
        },
        enumeratePlugins: () => {
            try {
                return JSON.parse(_Pebble.enumeratePlugins());
            } catch (e) {
                console.error("Pebble JS Bridge: enumeratePlugins failed", e);
                return [];
            }
        },
        invokeAction: (config) => {
            if (!config || typeof config !== 'object') {
                throw new Error("Pebble.invokeAction: config object required");
            }
            if (!config.plugin || !config.action) {
                throw new Error("Pebble.invokeAction: plugin and action required");
            }
            const callId = ++actionCallIdCounter;
            const request = {
                plugin: config.plugin,
                action: config.action,
                args: config.args || {},
                timeoutMs: config.timeoutMs,
            };
            return new Promise((resolve) => {
                actionCalls.set(callId, resolve);
                try {
                    _Pebble.invokeAction(callId, JSON.stringify(request));
                } catch (e) {
                    actionCalls.delete(callId);
                    resolve({ ok: false, code: "UNKNOWN", message: String(e) });
                }
            });
        },
    }
    global.Pebble.addEventListener = PebbleAPI.addEventListener;
    global.Pebble.removeEventListener = PebbleAPI.removeEventListener;
    global.Pebble.sendAppMessage = PebbleAPI.sendAppMessage;
    global.Pebble.getTimelineToken = PebbleAPI.getTimelineToken;
    global.Pebble.timelineSubscribe = PebbleAPI.timelineSubscribe;
    global.Pebble.timelineUnsubscribe = PebbleAPI.timelineUnsubscribe;
    global.Pebble.timelineSubscriptions = PebbleAPI.timelineSubscriptions;
    global.Pebble.getActiveWatchInfo = PebbleAPI.getActiveWatchInfo;
    global.Pebble.appGlanceReload = PebbleAPI.appGlanceReload;
    global.Pebble.insertTimelinePin = PebbleAPI.insertTimelinePin;
    global.Pebble.deleteTimelinePin = PebbleAPI.deleteTimelinePin;
    global.Pebble.subscribeToSource = PebbleAPI.subscribeToSource;
    global.Pebble.enumeratePlugins = PebbleAPI.enumeratePlugins;
    global.Pebble.invokeAction = PebbleAPI.invokeAction;
    /** Push at this app's config page, while it is open. No reply. */
    global.Pebble.sendConfigMessage = (message) => {
        _Pebble.sendConfigMessage(JSON.stringify(message === undefined ? null : message));
    };

    // Enable intercepting XHR calls (on Android - this doesn't work on iOS so we don't add
    // shouldIntercept to the PKJS interface there).
    XMLHttpRequest.prototype.originalOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url, async, user, password) {
        const xhr = this;
        xhr.pkjsUrl = url;
        xhr.pkjsMethod = method;
        xhr.pkjsIntercept = false;

        try {
            xhr.pkjsIntercept = _Pebble.shouldIntercept(url);
            if (xhr.pkjsIntercept) {
                console.log("XHR intercepting! returning from open")
                return;
            }
        } catch (error) {
            // iOS will fall through here and never intercept
        }

        if (arguments.length >= 5) {
            xhr.originalOpen(method, url, async, user, password);
        } else if (arguments.length >= 4) {
            xhr.originalOpen(method, url, async, user);
        } else if (arguments.length >= 3) {
            xhr.originalOpen(method, url, async);
        } else {
            xhr.originalOpen(method, url);
        }
    };

    XMLHttpRequest.prototype.originalSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
    XMLHttpRequest.prototype.setRequestHeader = function(header, value) {
        const xhr = this;
        if (xhr.pkjsIntercept) {
            // If we don't do this, later send call will fail
            return;
        }
        xhr.originalSetRequestHeader(header, value);
    };

    const pendingInterceptedXHRs = new Map();
    let interceptCallbackCount = 0;

    global.signalInterceptResponse = (result) => {
        const status = result.status;
        const response = result.response;
        const callbackId = result.callbackId;
        const xhr = pendingInterceptedXHRs.get(callbackId);
        if (!xhr) return;

        pendingInterceptedXHRs.delete(callbackId);

        // Define the read-only properties to simulate a successful response
        Object.defineProperty(xhr, 'readyState', { value: XMLHttpRequest.DONE, configurable: true });
        Object.defineProperty(xhr, 'status', { value: status, configurable: true });
        Object.defineProperty(xhr, 'statusText', { value: status === 200 ? 'OK' : 'Error', configurable: true });
        Object.defineProperty(xhr, 'response', { value: response, configurable: true });
        Object.defineProperty(xhr, 'responseText', { value: response, configurable: true });

        // Dispatch events to notify any listeners that the request is complete
        xhr.dispatchEvent(new Event('readystatechange'));
        xhr.dispatchEvent(new Event('load'));
        xhr.dispatchEvent(new Event('loadend'));
    };

    XMLHttpRequest.prototype.originalSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function(body) {
        const xhr = this;

        if (xhr.pkjsIntercept) {
            try {
                const callbackId = `intercept_${++interceptCallbackCount}`;
                pendingInterceptedXHRs.set(callbackId, xhr);

                // Call into Android, passing the callbackId
                // The Android side should now be asynchronous and eventually call signalInterceptResponse
                _Pebble.onIntercepted(callbackId, xhr.pkjsUrl, xhr.pkjsMethod, body);

                // We return here. The XHR is now "in flight" from the JS perspective.
                return;
            } catch (error) {
                console.error("Error initiating intercepted XHR", error);
                // Fallback or trigger error event
                xhr.dispatchEvent(new Event('error'));
                return;
            }
        }

        xhr.originalSend(body);
    };

    console.log("Pebble JS Bridge initialized.");
})(_global);