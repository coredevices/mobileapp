# Index Webhook API

Send Index ring recording data to any HTTP endpoint.

## Setup

1. In Index 01 Settings, tap **Webhook**
2. Pick the gesture to configure: **Hold & talk** or **Double click & hold**. Each gesture has its own endpoint, headers and payload mode
3. Enter your webhook URL
4. Add any request headers you need (e.g. an auth header)
5. Choose what to send: Recording only, Transcription only, or Both
6. Optionally tap **Send test event** to verify the endpoint, then **Save**

A gesture only sends once its config is saved with a URL. The switch turns sending off without discarding the URL and headers, and the last 20 runs per gesture are listed under **Recent runs**.

## Request Format

```
POST <your webhook URL>
Content-Type: multipart/form-data; boundary=<uuid>
<each user-configured header>
X-Audio-Size: <byte count>  (when audio is included)
X-Index-Trigger: single-click-hold | double-click-hold | test-event
X-Index-Delivery: <stable recording ID>  (recording events only)
X-Index-Test: true  (test events only)
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

`X-Index-Trigger` is added automatically to identify the gesture that started the recording — `single-click-hold`, `double-click-hold`, or `test-event` for a manually sent test. The gesture is persisted with the processing task and preserved when a failed recording is retried. Recordings with no known gesture do not fire a webhook at all. Neither `X-Index-Trigger` nor `X-Index-Test` can be overridden by a user-configured header.

`X-Index-Delivery` identifies a recording delivery and stays the same across automatic and manual retries. Receivers should use it as an idempotency key: if the same value arrives more than once, return success without archiving a second copy. It cannot be overridden by a user-configured header.

> Migration note: a previously configured single webhook is copied to both recording gestures, and an auth token configured before headers were user-settable is carried over as an `X-Widget-Token` header.

## Authentication

Authentication is whatever your headers say it is. The original integration used an `X-Widget-Token` header; the example below keeps that convention, but any scheme works.

## Example: Receiving with a simple server

```python
from flask import Flask, request

app = Flask(__name__)

@app.route('/webhook', methods=['POST'])
def receive():
    token = request.headers.get('X-Widget-Token')
    if token != 'your-secret-token':
        return 'Unauthorized', 401

    audio = request.files.get('audio')
    transcription = request.form.get('transcription')
    recorded_at = request.form.get('recordedAt')
    trigger = request.headers.get('X-Index-Trigger')
    delivery_id = request.headers.get('X-Index-Delivery')

    if audio:
        audio.save(f'/tmp/{audio.filename}')
        print(f'Received audio: {audio.filename}')

    if transcription:
        print(f'Transcription: {transcription}')

    print(f'Recorded at: {recorded_at}')
    print(f'Trigger: {trigger}')
    print(f'Delivery ID: {delivery_id}')
    return 'OK', 200
```

## Notes

- Network delivery is async and does not re-run transcription or agent processing
- Recording deliveries are persisted before sending and resume after an app restart
- Network failures, HTTP 408/425/429 responses, and 5xx responses retry with exponential backoff (one minute to one hour, plus up to 30 seconds of jitter) and honor a longer `Retry-After`
- Other HTTP failures remain available through **Retry** in **Recent runs**
- Successful deliveries keep only their delivery ID for deduplication; queued payload data is removed from the phone
- The webhook fires as early as its payload allows, in parallel with the rest of the pipeline: `RecordingOnly` sends as soon as the audio is on disk (before transcription); modes that include the transcript send once it is transcribed, concurrently with agent processing. The webhook therefore fires even if agent processing (or, for the recording-only mode, transcription) later fails.
- Audio is the same 16kHz resampled version used for transcription
