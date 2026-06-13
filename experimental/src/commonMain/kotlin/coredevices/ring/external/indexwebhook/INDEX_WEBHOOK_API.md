# Index Webhook API

Send Index ring recording data to any HTTP endpoint.

## Setup

1. In Index Settings, tap **Webhook**
2. Enter your webhook URL
3. Add any request headers you need (e.g. an auth header)
4. Choose what to send: Recording only, Transcription only, or Both
5. Set **Double click and hold** action to "Webhook"

## Request Format

```
POST <your webhook URL>
Content-Type: multipart/form-data; boundary=<uuid>
<each user-configured header>
X-Audio-Size: <byte count>  (when audio is included)
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

> Migration note: users who previously configured an auth token have it automatically carried over as an `X-Widget-Token` header, preserving existing behaviour.

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

    if audio:
        audio.save(f'/tmp/{audio.filename}')
        print(f'Received audio: {audio.filename}')

    if transcription:
        print(f'Transcription: {transcription}')

    print(f'Recorded at: {recorded_at}')
    return 'OK', 200
```

## Notes

- Uploads are async and non-blocking — they don't delay the normal recording pipeline
- Failed uploads are retried on the next recording (no persistent retry queue)
- The recording is always processed normally (transcription + agent) before the webhook fires
- Audio is the same 16kHz resampled version used for transcription
