import AccessoryTransportExtension
import ExtensionFoundation
import PebbleForwarding

@main
struct NotificationTransport: AccessoryTransportAppExtension {
    @AppExtensionPoint.Bind
    static var boundExtensionPoint: AppExtensionPoint {
        Identifier("com.apple.accessory-transport-extension")
    }

    func accept(
        sessionRequest: AccessoryTransportSession.Request
    ) -> AccessoryTransportSession.Request.Decision {
        Transport.accept(sessionRequest)
    }
}
