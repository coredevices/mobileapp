# Pebble Mobile app

Welcome to the official source code for the Pebble mobile app. Download the app from the [iOS Appstore](https://apps.apple.com/us/app/pebble-core/id6743771967) or [Google Play](https://play.google.com/store/apps/details?id=coredevices.coreapp&hl=en_US). The app is entirely open source. 

This app supports ALL Pebble watches, and Pebble Index 01 rings.

# Architecture

**New to Pebble?** A Pebble watch runs its own firmware and its own apps/watchfaces, but has no internet connection of its own. This app is the watch's companion and gateway to the world: it holds a persistent Bluetooth connection (BLE, or Bluetooth Classic on older watches) to relay notifications, sync data (time, weather, calendar, contacts, health), install watchapps, and proxy network requests on the watch's behalf. Much of the app's job is to be a reliable background service that stays connected and answers the watch quickly.

The codebase is **Kotlin Multiplatform + Compose Multiplatform**: one shared codebase — including the UI — builds both the Android and iOS apps. Almost everything lives in `commonMain`; platform-specific pieces (BLE stack, notification access, background execution) sit behind `expect`/`actual` interfaces. On iOS, `iosApp` is a thin Swift shell that embeds the shared Kotlin code as a framework via CocoaPods.

Watch communication lives in `libpebble3` and follows a few core concepts:

- **Pebble Protocol** — a binary, endpoint-based message protocol spoken over the Bluetooth connection. Packet definitions live in `libpebble3` under `io/rebble/libpebblecommon/packets/`.
- **Services & endpoint managers** — both scoped to a single watch connection. Services translate raw protocol messages into typed APIs for the rest of the app; endpoint managers handle the more complex stateful flows on top of them.
- **BlobDB** — the watch keeps small key-value databases (notifications, timeline pins, installed apps, contacts, …). The phone keeps the canonical copy of each record in a Room database and reconciles with the watch over the protocol (mostly phone → watch, with some watch-originated writebacks). The `blobannotations` + `blobdbgen` modules generate the serialization/sync plumbing via KSP.
- **PebbleKit JS** — watchapps can include a JavaScript component that runs on the *phone*, inside this app (`js/`), giving watchapps network access and configuration UIs.

Module map:

| Module | What it is |
|---|---|
| `composeApp` | App entry point: Compose UI, navigation, DI wiring (Koin), Firebase |
| `libpebble3` | Everything needed to talk to a Pebble watch: BLE transport, protocol, services, BlobDB sync. Also usable as a standalone library |
| `pebble` | Pebble app features shared between platforms, above the library layer |
| `experimental` | Pebble Index 01 (ring) support: continuous BLE scanning, voice-note recording and ingestion |
| `index-ai` | The "Index" AI assistant and its data layer (transcription, notes) |
| `libindex` | Index device plumbing: pairing, transfer, storage |
| `mcp` | MCP (Model Context Protocol) client/tool integration |
| `cactus`, `resampler`, `krisp-stubs` | Audio/ML support: on-device LLM inference, audio resampling, and API stubs for the private Krisp noise-cancellation integration |
| `blobannotations`, `blobdbgen` | KSP annotations + code generator for BlobDB records |
| `util` | Shared utilities (logging, IO, …) |

Stack at a glance: Koin (DI), Ktor (HTTP), Room (storage), Kermit (logging), coroutines/Flow throughout.

# Mobile App

The cross-platform Pebble mobile app is located in `composeApp`.

Several features (e.g. bug reporting, google login, memfault, online transcription, github developer connection) will not work without tokens configured in `gradle.properties` (but all core features do work).

### Android
* Compile on Android with `./gradlew :composeApp:assembleRelease`.
* You will need a `google-services.json` in `composeApp/src` to compile on Android (an examples with dummy values is provided in `google-services-dummy.json`).
* You will need a keystore with some keys if you intend to do a release build on Android (unless you use `LOCAL_RELEASE_BUILD=true` in `gradle.properties`).

### iOS

#### Prerequisites

1. **Install Java 17**

   ```bash
   # Install
   brew install openjdk@17
   
   # Symlink (optional)
   sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
   
   # Verify installation
   /usr/libexec/java_home -v 17
   ```

2. **Install CocoaPods**

   ```bash
   # Install
   brew install cocoapods
   
   # Symlink (optional)
   sudo ln -s /opt/homebrew/bin/pod /usr/local/bin/pod
   ```

3. **Setup GitHub token for speex module**

    Create a `local.properties` file in the project root with your GitHub credentials.

#### Configuration

4. **Configure Entitlements**

   Set `iosApp/iosApp/iosApp.entitlements` to:

   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
   <plist version="1.0">
      <dict>
         <key>com.apple.developer.healthkit</key>
         <true/>
      </dict>
   </plist>
   ```

5. **Install CocoaPods dependencies**

   ```bash
   ./gradlew podInstall
   ```

   This will create `Podfile.lock` and set up all required dependencies.

6. **Configure Bundle ID and Signing**

   - Open `iosApp/iosApp.xcworkspace` in Xcode (⚠️ **Important**: use `.xcworkspace`, not `.xcodeproj`)
   - Go to the target `iosApp` → **Signing & Capabilities**
   - Set your **Team** (your Apple Developer account)
   - Set **Bundle Identifier** to your own (e.g., `com.yourname.coredevices.coreapp`)

7. **Configure Firebase**

   - Create a free Firebase account at https://console.firebase.google.com
   - Create a new iOS app with the same Bundle ID you set above
   - Download `GoogleService-Info.plist` and place it in `iosApp/iosApp/`
   - The file should be named exactly `GoogleService-Info.plist`

8. **Create a git tag for app version**

   Create a git tag that will be used as the version of the app:

   ```bash
   git tag 1.0.0
   ```

#### Build and Run

9. **Build and run in Xcode**

   - Open `iosApp/iosApp.xcworkspace` in Xcode
   - Select your target device or simulator and run.

   > **Tip**: If you encounter module not found errors (`Mixpanel`, `FirebaseCore`, etc.), make sure you:
   > - Opened the `.xcworkspace` file (not `.xcodeproj`)
   > - Ran `pod install` successfully
   > - Cleaned the build folder (`Product → Clean Build Folder`)




# Naming your project

In order to honour the Pebble trademark, you may not use "Pebble" in the name of your app, product or service, except in a referential manner. For example, "Awesome App for Pebble" is acceptable, but "Pebble Awesome" is not.

# Contributing

Pebble employs several (extremely busy) full time mobile developers to work on this app. If you'd like to contribute, we welcome PRs but caution you that it may take us some time before we can review your PR. Please be patient with us :)

# Reporting bugs

Please use the built-in bug report feature in the Pebble app by going to Settings > Get Help > New Bug Report instead of Github issues, as the internal bug reports contain more information for us to debug most issues. 

# Development Guidelines

(We don't follow all of these everywhere, yet, and need to document a lot more..)

- We share a version catalog with CoreApp to avoid duplicating definitions. This means a few extra library entries which are not used in libpebble (so they can share the version definition).
- Use `optIn` in `build.gradle.kts` rather than individual source files.
- Only use injected coroutine scopes: either LibPebbleCoroutineScope (instead of GlobalScope) or ConnectionCoroutineScope (scoped per-connection).

Connection:
- Services are scoped to the connection. Their main job is to translate raw pebble protocol messages to something readable by the rest of the app.
- Endpoint managers are also scoped to the connection, and manage complex state around services.

# Copyright and Licensing

See https://ericmigi.notion.site/Core-Devices-Software-Licensing-1c0fbb55ea8480f88d27ccf20fcb84a8

Copyright 2026 Core Devices LLC

This software is dual-licensed by Core Devices LLC. It can be used either:
  
(1) for free under the terms of the GNU GPLv3; OR
  
(2) under the terms of a paid-for Core Devices Commercial License agreement between you and Core Devices (the terms of which may vary depending on what you and Core Devices have agreed to).

Unless required by applicable law or agreed to in writing, software distributed under the Licenses is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the Licenses for the specific language governing permissions and limitations under the Licenses.

Additional Permissions For Submission to Apple App Store: Provided that you are otherwise in compliance with the GPLv3 for each covered work you convey (including without limitation making the Corresponding Source available in compliance with Section 6 of the GPLv3), Core Devices also grants you the additional permission to convey through the Apple App Store non-source executable versions of the Program as incorporated into each applicable covered work as Executable Versions only under the Mozilla Public License version 2.0 (https://www.mozilla.org/en-US/MPL/2.0/).
