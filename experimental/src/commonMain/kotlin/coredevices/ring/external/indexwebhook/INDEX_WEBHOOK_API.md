# Index Webhook API

Send Index ring recording data to any HTTP endpoint.

## Setup

1. In Index 01 Settings, tap **Webhook**
2. Pick the gesture to configure: **Hold & talk** or **Double click & hold**. Each gesture has its own endpoint, headers and payload mode
3. Enter your webhook URL
4. Add any request headers you need (e.g. an auth header)
5. Optionally enable **Sign requests** and enter the same signing secret used by your server
6. Choose what to send: Recording only, Transcription only, or Both
7. Optionally tap **Send test event** to verify the endpoint, then **Save**

A gesture only sends once its config is saved with a URL. The switch turns sending off without discarding the URL and headers, and the last 20 runs per gesture are listed under **Recent runs**.

## Request Format

```
POST <your webhook URL>
Content-Type: multipart/form-data; boundary=<uuid>
<each user-configured header>
X-Audio-Size: <byte count>  (when audio is included)
X-Index-Webhook-Version: 1
X-Index-Trigger: single-click-hold | double-click-hold | test-event
X-Index-Test: true  (test events only)
X-Index-Signature: <lowercase hex HMAC-SHA256>  (when signing is enabled)
X-Index-Timestamp: <Unix time in seconds>  (when signing is enabled)
X-Index-Delivery: <stable delivery ID>  (when signing is enabled)
```

## Multipart Fields

### `audio` (conditional)

Included when payload mode is **Recording only** or **Both**.

- Content-Type: `audio/mp4`
- Filename: `<recordingId>.m4a`
- Format: AAC-LC encoded in M4A container, mono, 16kHz

### `transcription` (conditional)

Included when payload mode is **Transcription only** or **Both**.

- Plain text transcription of the recording

### `test` (test events only)

Set to `"true"` for payloads sent by **Send test event**, so they cannot be mistaken for a recording. Test events carry no `audio` part and a fixed `transcription`.

### `recordedAt` (always)

Unix timestamp in milliseconds when the recording was captured.

### `client` (always)

Always set to `"ring"`.

## Payload Modes

| Mode               | `audio` | `transcription` | `recordedAt` | `client` |
|--------------------|---------|-----------------|--------------|----------|
| Recording only     | Yes     | No              | Yes          | Yes      |
| Transcription only | No      | Yes             | Yes          | Yes      |
| Both               | Yes     | Yes             | Yes          | Yes      |

## Headers

Headers are fully user-configurable in the webhook settings — add as many name/value pairs as you need. They are sent verbatim on every request, so use them for authentication (e.g. an `Authorization` or `X-Widget-Token` header) or any other metadata your server expects.

`X-Audio-Size` is still added automatically when audio is included (it carries the audio byte count) and cannot be overridden.

`X-Index-Trigger` is added automatically to identify the gesture that started the recording — `single-click-hold`, `double-click-hold`, or `test-event` for a manually sent test. The gesture is persisted with the processing task and preserved when a failed recording is retried. Recordings with no known gesture do not fire a webhook at all.

`X-Index-Webhook-Version` is sent on every request and versions the complete webhook protocol, including its automatic headers, multipart fields, and signing rules. This document describes version `1`. A missing version identifies a request from an older, pre-version app build; receivers that depend on a particular contract should require a supported explicit version.

User-configured headers cannot override `X-Audio-Size`, `X-Index-Webhook-Version`, `X-Index-Trigger`, `X-Index-Test`, `X-Index-Signature`, `X-Index-Timestamp`, or `X-Index-Delivery`. Header-name matching is case-insensitive.

> Migration note: a previously configured single webhook is copied to both recording gestures, and an auth token configured before headers were user-settable is carried over as an `X-Widget-Token` header.

## Protocol versioning

Increment `X-Index-Webhook-Version` only for receiver-visible breaking changes, such as renaming or removing automatic headers or multipart fields, changing field semantics or encoding, or changing the signed-byte format. Backward-compatible additions remain on the current version; receivers should ignore headers and multipart fields they do not use.

Record every version change in this document, including migration guidance when applicable.

- **Version 1:** Current multipart request contract and optional HMAC-SHA256 signing protocol documented below.

## Signed requests

Request signing is optional and configured independently for each recording gesture. The secret is stored in Android Keystore-backed encrypted storage or Apple Keychain, rather than in the normal webhook settings JSON. If signing is enabled but the secret cannot be read, the request fails without sending an unsigned fallback.

The app signs these exact bytes:

```text
UTF8(
  "v1\n" +
  timestamp + "\n" +
  deliveryId + "\n" +
  trigger + "\n" +
  (isTest ? "1" : "0") + "\n"
) || rawMultipartBodyBytes
```

The signing key is the UTF-8 byte sequence of the secret exactly as entered. The signature header is:

```text
X-Index-Signature: <lowercase hex HMAC-SHA256(signingKey, bytesAbove)>
```

The leading `v1` in the signed bytes authenticates `X-Index-Webhook-Version: 1`; a receiver must use the header value to reconstruct that prefix. For a recording, `X-Index-Delivery` is the stable recording delivery identifier. A test event gets a fresh UUID. `X-Index-Timestamp` is generated when the request is sent.

Receivers should:

1. Require HTTPS.
2. Read the raw request body before parsing multipart fields.
3. Reject unsupported webhook versions and malformed timestamps.
4. Reconstruct the signed bytes and compare signatures in constant time.
5. Reject timestamps outside a short window; five minutes is a reasonable default.
6. Cache accepted delivery IDs for at least that window and reject replays.

Use a unique, cryptographically random secret for each endpoint. At least 32 random bytes encoded as base64url or hex is recommended. Do not use a memorable password or reuse an API token.

HMAC authenticates the request and detects modification; it does not encrypt the recording or transcription. HTTPS is still required.

## Other authentication

Custom headers can still carry another authentication scheme alongside request signing. The original integration used an `X-Widget-Token` header, but any scheme works.

## Example: Verifying a signed request

```python
import hashlib
import hmac
import os
import time

from flask import Flask, request

app = Flask(__name__)
secret = os.environ["INDEX_WEBHOOK_SECRET"].encode("utf-8")
seen_deliveries = {}

@app.route('/webhook', methods=['POST'])
def receive():
    raw_body = request.get_data(cache=True)
    webhook_version = request.headers.get('X-Index-Webhook-Version', '')
    signature = request.headers.get('X-Index-Signature', '')
    timestamp_text = request.headers.get('X-Index-Timestamp', '')
    delivery_id = request.headers.get('X-Index-Delivery', '')
    trigger = request.headers.get('X-Index-Trigger', '')
    is_test = request.headers.get('X-Index-Test') == 'true'

    if webhook_version != '1':
        return 'Unsupported webhook version', 400

    try:
        timestamp = int(timestamp_text)
    except ValueError:
        return 'Invalid timestamp', 400

    now = int(time.time())
    if abs(now - timestamp) > 300:
        return 'Expired request', 401

    prefix = f"v{webhook_version}\n{timestamp}\n{delivery_id}\n{trigger}\n{1 if is_test else 0}\n"
    expected = hmac.new(
        secret,
        prefix.encode('utf-8') + raw_body,
        hashlib.sha256,
    ).hexdigest()
    if not hmac.compare_digest(expected, signature):
        return 'Invalid signature', 401

    if delivery_id in seen_deliveries:
        return 'Duplicate delivery', 409
    seen_deliveries[delivery_id] = now

    audio = request.files.get('audio')
    transcription = request.form.get('transcription')
    recorded_at = request.form.get('recordedAt')
    trigger = request.headers.get('X-Index-Trigger')

    if audio:
        audio.save(f'/tmp/{audio.filename}')
        print(f'Received audio: {audio.filename}')

    if transcription:
        print(f'Transcription: {transcription}')

    print(f'Recorded at: {recorded_at}')
    print(f'Trigger: {trigger}')
    return 'OK', 200
```

The in-memory replay cache above is only illustrative. A production service should expire entries and use a shared TTL store when more than one server instance accepts webhooks.

## Notes

- Uploads are async and non-blocking — they don't delay the normal recording pipeline
- Failed uploads are retried on the next recording (no persistent retry queue)
- The recording is always processed normally (transcription + agent) before the webhook fires
- Audio is the same 16kHz resampled version used for transcription
