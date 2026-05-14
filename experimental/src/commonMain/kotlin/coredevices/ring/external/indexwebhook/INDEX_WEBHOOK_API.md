# Index Webhook API

Send Index ring recording data to any HTTP endpoint.

## Setup

1. In Index Settings, tap **Webhook**
2. Enter your webhook URL and auth token
3. Choose what to send: Recording only, Transcription only, or Both
4. Set **Double click and hold** action to "Webhook"

## Request Format

```
POST <your webhook URL>
Content-Type: multipart/form-data; boundary=<uuid>
X-Widget-Token: <your auth token>
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

## Authentication

The auth token is sent as the `X-Widget-Token` HTTP header. Your server should validate this token to authenticate requests.

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
