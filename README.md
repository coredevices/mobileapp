# libpebble3

libpebble3 is a kotlin multiplatform library for interacting with Pebble devices. It is designed to do everything that a Pebble/Core watch companion app needs to do, except for the UI and specific web services.

See https://github.com/coredevices/libpebble3/wiki/Roadmap

# Using libpebble3

See https://github.com/coredevices/libpebble3/wiki/Roadmap

## Enabling PebbleKit Android 2

To fully enable PebbleKit Android 2 support, you have to add this provider to your `AndroidManifest.xml`:

```xml
<provider
    android:authorities="[YOUR_PACKAGE].pebblekit"
    android:name="io.rebble.libpebblecommon.pebblekit.two.PebbleKitProvider"
    android:exported="true" />
```

Where the `[YOUR_PACKAGE]` is the package name of your application.

## Enabling PebbleKit Android Classic

To fully enable PebbleKit Android Classic, you have to add this provider to your `AndroidManifest.xml`:

```xml
<provider
    android:authorities="com.getpebble.android.provider.basalt"
    android:name="io.rebble.libpebblecommon.pebblekit.classic.PebbleKitProvider"
    tools:ignore="ExportedContentProvider"
    android:exported="true" />
```

Note that by doing so, your app will not be able to be installed alongside other apps that also do this.

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

   Set `iosApp/iosApp/iosApp.entitlements` to an empty dictionary:

   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
   <plist version="1.0">
   <dict>
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

We aren't actively encouraging contributions yet, while we are aggressively building out feature parity with the original Pebble apps. CI testing is not comprehensive, so changes need to be manually tested with real hardware using CoreApp, and our roadmap/bug tracker is not currently on github. We will update when this changes.

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

Copyright 2025 Core Devices LLC

This software is dual-licensed by Core Devices LLC. It can be used either:
  
(1) for free under the terms of the GNU GPLv3; OR
  
(2) under the terms of a paid-for Core Devices Commercial License agreement between you and Core Devices (the terms of which may vary depending on what you and Core Devices have agreed to).

Unless required by applicable law or agreed to in writing, software distributed under the Licenses is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the Licenses for the specific language governing permissions and limitations under the Licenses.

Additional Permissions For Submission to Apple App Store: Provided that you are otherwise in compliance with the GPLv3 for each covered work you convey (including without limitation making the Corresponding Source available in compliance with Section 6 of the GPLv3), Core Devices also grants you the additional permission to convey through the Apple App Store non-source executable versions of the Program as incorporated into each applicable covered work as Executable Versions only under the Mozilla Public License version 2.0 (https://www.mozilla.org/en-US/MPL/2.0/).
