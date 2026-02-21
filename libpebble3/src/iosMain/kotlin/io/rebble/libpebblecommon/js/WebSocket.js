class WebSocket {
    static _instances = new Map();

    static CONNECTING = 0;
    static OPEN = 1;
    static CLOSING = 2;
    static CLOSED = 3;

    readyState = WebSocket.CONNECTING;
    protocol = '';
    extensions = '';
    bufferedAmount = 0;
    binaryType = 'arraybuffer';
    _listeners = new Map();

    onopen = null;
    onmessage = null;
    onerror = null;
    onclose = null;

    constructor(url, protocols) {
        if (!url) {
            throw new Error("SyntaxError: URL is required.");
        }
        this.url = url;

        var protocolStr = '';
        if (Array.isArray(protocols)) {
            protocolStr = protocols.join(',');
        } else if (protocols) {
            protocolStr = String(protocols);
        }

        this._instanceID = _WebSocketManager.createInstance(url, protocolStr);
        WebSocket._instances.set(this._instanceID, this);
    }

    send(data) {
        if (this.readyState === WebSocket.CONNECTING) {
            throw new Error("InvalidStateError: WebSocket is still connecting.");
        }
        if (this.readyState !== WebSocket.OPEN) {
            return;
        }
        _WebSocketManager.send(this._instanceID, String(data));
    }

    close(code, reason) {
        if (this.readyState === WebSocket.CLOSING || this.readyState === WebSocket.CLOSED) {
            return;
        }
        this.readyState = WebSocket.CLOSING;
        _WebSocketManager.close(this._instanceID, code || 1000, reason || '');
    }

    addEventListener(type, listener) {
        if (!this._listeners.has(type)) {
            this._listeners.set(type, []);
        }
        this._listeners.get(type).push(listener);
    }

    removeEventListener(type, listener) {
        if (this._listeners.has(type)) {
            var listeners = this._listeners.get(type);
            var index = listeners.indexOf(listener);
            if (index !== -1) {
                listeners.splice(index, 1);
            }
        }
    }

    _onOpen(protocol) {
        this.protocol = protocol || '';
        this.readyState = WebSocket.OPEN;
        this._dispatchEvent('open', { type: 'open' });
    }

    _onMessage(data, isBinary) {
        var msgData;
        if (isBinary) {
            msgData = Uint8Array.fromBase64(data).buffer;
        } else {
            msgData = data;
        }
        this._dispatchEvent('message', {
            type: 'message',
            data: msgData,
            origin: this.url,
            lastEventId: '',
            source: null,
            ports: []
        });
    }

    _onClose(code, reason, wasClean) {
        this.readyState = WebSocket.CLOSED;
        this._dispatchEvent('close', {
            type: 'close',
            code: code,
            reason: reason,
            wasClean: wasClean
        });
        WebSocket._instances.delete(this._instanceID);
    }

    _onError() {
        this._dispatchEvent('error', { type: 'error' });
    }

    _dispatchEvent(type, event) {
        if (this._listeners.has(type)) {
            var listeners = this._listeners.get(type);
            for (var i = 0; i < listeners.length; i++) {
                listeners[i](event);
            }
        }
        switch (type) {
            case 'open':
                this.onopen && this.onopen(event);
                break;
            case 'message':
                this.onmessage && this.onmessage(event);
                break;
            case 'error':
                this.onerror && this.onerror(event);
                break;
            case 'close':
                this.onclose && this.onclose(event);
                break;
        }
    }
}

globalThis.WebSocket = WebSocket;
