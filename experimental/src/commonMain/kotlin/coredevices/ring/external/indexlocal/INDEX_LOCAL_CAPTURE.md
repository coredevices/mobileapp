# Index Local Capture API

Send Index 01 recordings to another app on the same Android phone using an
**explicit intent**. No internet, no webhook, no `INTERNET` permission on the
receiver. Audio and transcription travel through a `FileProvider` content URI
and string extras.

This is the local counterpart of the Index Webhook API. The webhook still
exists. Notesnook is a Notes destination (like Notion, Obsidian, and Tasker),
not a separate sidecar.

## Why not ACTION_SEND?

`ACTION_SEND` / share-sheet delivery:

- Requires an Activity, so Android's background-activity-launch rules drop it
  when the screen is locked or Pebble is a connected-device foreground service.
- Cannot grant a content URI to a specific package without a chooser.
- Notesnook's share extension is a UI (`ShareActivity`) — the user would have
  to tap Save on every capture.

Local capture instead uses `startForegroundService` + an explicit component
name, which a connected-device FGS (Pebble) is allowed to start, and a
manifest-registered receiver as fallback.

## Setup (Pebble Core)

1. Index 01 Settings → **Add integration** → **Notesnook** → Connect
   (or Notes → **Where notes save** → Notesnook)
2. Hold & Talk (and other Index-agent recordings) deliver audio + transcription
   to Notesnook on this phone

The send happens when Notesnook is the selected notes destination. Webhook
delivery is independent. A gesture routed to **Nothing** or **Web search**
does not send.

If you previously used **Local apps → Notesnook**, that toggle is migrated:
Notesnook becomes the selected notes destination automatically.

## Setup (Notesnook)

1. Settings → **Productivity** → **Pebble Index 01**
2. Leave **Receive Index captures** on (default)
3. Optionally enable **Keep ready in background** — a long-lived foreground
   service so Headless JS is already warm when a capture arrives (and so
   Android is less likely to kill the process between captures)

## Intent contract

```
startForegroundService
  Component: com.streetwriters.notesnook / com.streetwriters.notesnook.pebble.PebbleIndexCaptureService
  Action:    com.streetwriters.notesnook.action.INDEX_CAPTURE
  Type:      audio/mp4   (when audio is present)
             text/plain  (transcription-only)
  Flags:     FLAG_GRANT_READ_URI_PERMISSION
  ClipData:  content URI of the M4A (when audio is present)
```

Fallback if the service cannot be started: the same extras are broadcast to

```
com.streetwriters.notesnook.pebble.PebbleIndexCaptureReceiver
```

The receiver immediately starts the service.

The service is exported but permission-gated:

```
com.streetwriters.notesnook.permission.RECEIVE_INDEX_CAPTURE
```

Pebble holds that permission. The service also rejects callers whose UID is
not `coredevices.coreapp` (or Notesnook itself, for the receiver hop).

### Extras

| Extra | Type | When |
|---|---|---|
| `transcription` | String | Transcription / Both, and STT succeeded |
| `recordedAt` | long | Always. Unix epoch milliseconds |
| `client` | String | Always. `"ring"` |
| `recordingId` | String | Always. Same id used for the M4A filename |
| `trigger` | String | Always. `single-click-hold`, `double-click-hold`, or `test-event` |
| `payloadMode` | String | Always. `recording`, `transcription`, or `both` |
| `audioSize` | int | Audio is present. Byte count of the M4A |
| `test` | boolean | Test events only |
| `android.intent.extra.STREAM` | Uri | Audio is present. `content://coredevices.coreapp.fileprovider/index_local/<id>.m4a` |

Audio format matches the webhook: AAC-LC in M4A, mono, 16 kHz.

## Package visibility

Pebble declares:

```xml
<queries>
  <package android:name="com.streetwriters.notesnook" />
</queries>
```

so `PackageManager` can see Notesnook on Android 11+.
