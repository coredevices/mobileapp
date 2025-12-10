class XMLHttpRequest {
    static _instances = new Map();

    static UNSENT = 0;
    static OPENED = 1;
    static HEADERS_RECEIVED = 2;
    static LOADING = 3;
    static DONE = 4;

    readyState = XMLHttpRequest.UNSENT;
    /**
     * @type {("arraybuffer" | "blob" | "document" | "json" | "text" | "")}
     */
    responseType = "";
    status = 0;
    statusText = "";
    _listeners = new Map();

    constructor() {
        this._instanceID = _XMLHTTPRequestManager.getXHRInstanceID();
        XMLHttpRequest._instances.set(this._instanceID, this);
    }

    addEventListener(type, listener) {
        if (!this._listeners.has(type)) {
            this._listeners.set(type, []);
        }
        this._listeners.get(type).push(listener);
    }

    removeEventListener(type, listener) {
        if (this._listeners.has(type)) {
            const listeners = this._listeners.get(type);
            const index = listeners.indexOf(listener);
            if (index !== -1) {
                listeners.splice(index, 1);
            }
        }
    }

    open(method, url, async = true, user, password) {
        if (!user) {
            user = "";
        }
        if (!password) {
            password = "";
        }
        if (!method) {
            throw new Error("SyntaxError: Method is required.");
        }
        if (!url) {
            throw new Error("SyntaxError: URL is required.");
        }
        _XMLHTTPRequestManager.open(this._instanceID, method, url, async, user, password);
    }

    setRequestHeader(header, value) {
        _XMLHTTPRequestManager.setRequestHeader(this._instanceID, header, value);
    }

    getAllResponseHeaders() {
        if (this.readyState < XMLHttpRequest.HEADERS_RECEIVED) {
            throw new Error("InvalidStateError: The object is in an invalid state (not sent).");
        }
        return Object.entries(this.responseHeaders)
            .map(([key, value]) => `${key}: ${value}`)
            .join("\r\n");
    }

    getResponseHeader(header) {
        if (this.readyState < XMLHttpRequest.HEADERS_RECEIVED) {
            throw new Error("InvalidStateError: The object is in an invalid state (not sent).");
        }
        return this.responseHeaders[header.toLowerCase()] || null;
    }

    send(data) {
        if (this.readyState !== XMLHttpRequest.OPENED) {
            throw new Error("InvalidStateError: The object is in an invalid state (not opened).");s
        }
        if (this.responseType === "blob" || this.responseType === "document") {
            throw new Error("Blob and document response types are not supported.");
        }
        _XMLHTTPRequestManager.send(this._instanceID, this.responseType, data);
    }

    abort() {
        _XMLHTTPRequestManager.abort(this._instanceID);
    }

    _onResponseComplete(responseHeaders, status, statusText, body) {
        this.responseHeaders = responseHeaders;
        this.status = status;
        this.statusText = statusText;
        switch (this.responseType) {
            case "arraybuffer":
                this.responseText = null;
                this.response = Uint8Array.fromBase64(body);
                break;
            case "blob":
                this.response = null; // no blob in JSContext
                break;
            case "document":
                this.response = null; // no document in JSContext
                break;
            case "json":
                this.responseText = body;
                try {
                    this.response = JSON.parse(body);
                } catch (e) {
                    console.error("JSON parse error:", e);
                    this._dispatchEvent("error", {
                        type: "error",
                        target: this,
                        message: "JSON parse error: " + e.message,
                    });
                    this.response = null;
                }
                break;
            default: // "text" or ""
                this.response = body;
                this.responseText = body;
                break;
        }
    }

    _dispatchEvent(type, event) {
        if (this._listeners.has(type)) {
            const listeners = this._listeners.get(type);
            for (const listener of listeners) {
                listener(event);
            }
        }
        switch (type) {
            case "load":
                this.onload && this.onload(event);
                break;
            case "loadend":
                this.onloadend && this.onloadend(event);
                break;
            case "loadstart":
                this.onloadstart && this.onloadstart(event);
                break;
            case "error":
                this.onerror && this.onerror(event);
                break;
            case "abort":
                this.onabort && this.onabort(event);
                break;
            case "progress":
                this.onprogress && this.onprogress(event);
                break;
            case "readystatechange":
                this.onreadystatechange && this.onreadystatechange(event);
                break;
            case "timeout":
                this.ontimeout && this.ontimeout(event);
                break;
            default:
                console.warn("XHR - Unknown event type:", type);
                break;
        }
    }
}

globalThis.XMLHttpRequest = XMLHttpRequest;