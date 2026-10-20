# Secure iOS notification forwarding (draft integration)

This change adds an opt-in iOS 26.5 notification bridge with a data provider,
transport security, and transport extension. It is disabled in all four bundle
Info.plists by `PebbleNotificationsEnabled = false`. The ordinary Bluetooth path
and existing Pebble watches continue to use the current connection flow.

## Requirements before enabling

1. Use Xcode and an iOS SDK with AccessoryNotifications and
   AccessoryTransportExtension. Apple permits development testing in any region;
   customer installation requires an iPhone in the EU with an EU Apple Account.
2. Configure the app and three extension identifiers for the same signing team.
   Add the App Group named by `PebbleNotificationAppGroup` and Keychain Sharing
   group named by `PebbleNotificationKeychainGroup` to the app and all three
   extensions. These values must resolve identically in every process. Configure
   the extension signatures with their respective Apple entitlements:
   `com.apple.developer.accessory-data-provider`,
   `com.apple.developer.accessory-transport-security`, and
   `com.apple.developer.accessory-transport-extension`, each set to `true`.
   Set each extension target's `CODE_SIGN_ENTITLEMENTS` to its matching file in
   `iosApp/PairedIntegration/Signing/`. Merge the App Group and Keychain Sharing
   entries from one of those files into the existing `iosApp/iosApp.entitlements`
   for the containing app; keep its existing capabilities. Update provisioning
   profiles accordingly. Do not commit signing assets.
3. Add `Bluetooth` to the app's `NSAccessorySetupKitSupports` and set
   `PebbleNotificationsEnabled` to `true` in the app and extension Info.plists
   only for a compatible test build. The ordinary phone hosted PPoG flow is
   disabled for that ASK build; its watch must provide the compatible watch
   hosted GATT role. Keep regular builds at the default `false` setting.
4. Use compatible Pebble firmware implementing the enrollment, key exchange,
   encrypted transport, and receipt protocol. The app alone cannot make stock
   Pebble or recovery firmware support AccessorySetupKit. Existing watches need
   a compatible firmware path before forwarding can be exercised end to end.

The extensions share a P-256 pairing identity, perform an explicit physical
comparison during enrollment, then send notification payloads through Apple's
transport security session. The watch must validate and decrypt the messages
and send authenticated receipts. Removal, reply, and clear actions are bound
to the same session. No plaintext notification content or private key should
be logged or stored in this repository.

`PebbleNotificationNamespace` scopes local notifications and keychain service
names. It must match across the app and extensions. The `group.*` container
stores bridge state shared across processes. Configure all four keys together;
changing one after enrollment requires a new enrollment.

## Verification boundary

The host regression scripts in `tests/notification_forwarding_host.py`,
`tests/transport_message_queue.py`, and
`tests/watch_accessory_link_timeout.py` exercise protocol, ordering, session,
receipt, and timeout behavior with mocks. Xcode target builds prove SDK source
compatibility. Neither proves signed entitlements, system delivery, or watch
firmware behavior. Those require a provisioned iOS 26.5 phone and compatible
physical Pebble.

The original protocol design and portions of this implementation were adapted
from shinvou/NotifBridge; see `NOTIFBRIDGE-NOTICE.md` for the MIT notice and
source revision. This draft also incorporates AI-assisted code adaptation and
review, which should be disclosed in the pull request.

References: [Apple forwarding guide](https://developer.apple.com/documentation/accessorytransportextension/receiving-ios-notifications-on-an-accessory),
[Apple availability](https://developer.apple.com/documentation/accessorynotifications),
and [upstream compatibility discussion](https://github.com/coredevices/mobileapp/issues/221).
