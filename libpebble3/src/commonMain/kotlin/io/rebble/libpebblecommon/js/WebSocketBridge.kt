package io.rebble.libpebblecommon.js

internal object WebSocketBridge {
    private val protocolPattern = Regex("^[!#\$%&'*+\\-.^_`|~0-9A-Za-z]+$")

    fun validateUrl(url: String) {
        require(url.startsWith("ws://", ignoreCase = true) || url.startsWith("wss://", ignoreCase = true)) { "WebSocket URL must use ws: or wss:." }
        require('#' !in url) { "WebSocket URL must not include a fragment." }
    }

    fun parseProtocols(protocols: String?): List<String> {
        if (protocols.isNullOrEmpty()) {
            return emptyList()
        }
        val seen = mutableSetOf<String>()
        return protocols.split(',').map { it.trim() }.also { protocolList ->
            protocolList.forEach { protocol ->
                require(protocolPattern.matches(protocol) && seen.add(protocol.lowercase())) {
                    "Invalid WebSocket subprotocol."
                }
            }
        }
    }

    fun validateClose(code: Int, reason: String) {
        require(code == 1000 || code in 3000..4999) { "Invalid WebSocket close code." }
        require(reason.encodeToByteArray().size <= 123) { "WebSocket close reason is too long." }
    }

    fun acceptsProtocol(negotiated: String, requested: List<String>) = negotiated.isEmpty() || negotiated in requested
}

internal val WEB_SOCKET_SHIM = """
class WebSocket {
    static _instances = new Map();
    static CONNECTING = 0; static OPEN = 1; static CLOSING = 2; static CLOSED = 3;

    readyState = WebSocket.CONNECTING; protocol = ''; extensions = ''; bufferedAmount = 0;
    binaryType = 'arraybuffer'; _listeners = new Map();
    onopen = null; onmessage = null; onerror = null; onclose = null;
    constructor(url, protocols) {
        if (url === undefined || url === null) {
            throw new Error("SyntaxError: URL is required.");
        }
        this.url = String(url);
        if (!/^wss?:\/\//i.test(this.url)) throw new Error("SyntaxError: WebSocket URL must use ws: or wss:.");
        if (this.url.indexOf('#') !== -1) throw new Error("SyntaxError: WebSocket URL must not include a fragment.");
        const protocolList = WebSocket._normalizeProtocols(protocols);
        this._instanceID = _WebSocketManager.createInstance(this.url, protocolList.join(','));
        WebSocket._instances.set(this._instanceID, this);
        _WebSocketManager.connect(this._instanceID);
    }
    static _normalizeProtocols(protocols) {
        if (protocols === undefined || protocols === null) return [];
        const protocolList = Array.isArray(protocols) ? protocols.map(String) : [String(protocols)];
        const seen = new Set();
        const tokenPattern = /^[!#${'$'}%&'*+\-.^_`|~0-9A-Za-z]+$/;
        protocolList.forEach(protocol => {
            const lower = protocol.toLowerCase();
            if (!tokenPattern.test(protocol) || seen.has(lower)) throw new Error("SyntaxError: Invalid WebSocket subprotocol.");
            seen.add(lower);
        });
        return protocolList;
    }
    static _arrayBufferToBase64(buffer) {
        const bytes = new Uint8Array(buffer);
        if (typeof bytes.toBase64 === 'function') return bytes.toBase64();
        let binary = '';
        bytes.forEach(byte => binary += String.fromCharCode(byte));
        return btoa(binary);
    }
    static _base64ToArrayBuffer(base64) {
        if (Uint8Array.fromBase64) return Uint8Array.fromBase64(base64).buffer;
        const binary = atob(base64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        return bytes.buffer;
    }
    send(data) {
        if (this.readyState === WebSocket.CONNECTING) throw new Error("InvalidStateError: WebSocket is still connecting.");
        if (this.readyState !== WebSocket.OPEN) return;
        if (data instanceof ArrayBuffer) {
            _WebSocketManager.send(this._instanceID, WebSocket._arrayBufferToBase64(data), true);
        } else if (ArrayBuffer.isView(data)) {
            const buffer = data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength);
            _WebSocketManager.send(this._instanceID, WebSocket._arrayBufferToBase64(buffer), true);
        } else {
            _WebSocketManager.send(this._instanceID, String(data), false);
        }
    }
    close(code, reason) {
        if (this.readyState === WebSocket.CLOSING || this.readyState === WebSocket.CLOSED) return;
        code = code === undefined || code === null ? 1000 : Number(code);
        reason = reason === undefined || reason === null ? '' : String(reason);
        if (!Number.isInteger(code) || (code !== 1000 && (code < 3000 || code > 4999))) throw new Error("InvalidAccessError: Invalid WebSocket close code.");
        if (unescape(encodeURIComponent(reason)).length > 123) throw new Error("SyntaxError: WebSocket close reason is too long.");
        this.readyState = WebSocket.CLOSING;
        _WebSocketManager.close(this._instanceID, code, reason);
    }
    addEventListener(type, listener) {
        if (!this._listeners.has(type)) this._listeners.set(type, []);
        if (this._listeners.get(type).indexOf(listener) === -1) this._listeners.get(type).push(listener);
    }
    removeEventListener(type, listener) {
        const listeners = this._listeners.get(type);
        if (!listeners) return;
        const index = listeners.indexOf(listener);
        if (index !== -1) listeners.splice(index, 1);
    }
    _onOpen(protocol) { this.protocol = protocol || ''; this.readyState = WebSocket.OPEN; this._dispatchEvent('open', { type: 'open' }); }
    _onMessage(data, isBinary) {
        this._dispatchEvent('message', {
            type: 'message',
            data: isBinary ? WebSocket._base64ToArrayBuffer(data) : data,
            origin: this.url,
            lastEventId: '',
            source: null,
            ports: []
        });
    }
    _onClose(code, reason, wasClean) {
        this.readyState = WebSocket.CLOSED;
        this._dispatchEvent('close', { type: 'close', code: code, reason: reason, wasClean: wasClean });
        WebSocket._instances.delete(this._instanceID);
    }
    _onError() { this._dispatchEvent('error', { type: 'error' }); }
    _dispatchEvent(type, event) {
        (this._listeners.get(type) || []).slice().forEach(listener => listener(event));
        const handler = this['on' + type];
        if (handler) handler(event);
    }
}

globalThis.WebSocket = WebSocket;
""".trimIndent()
