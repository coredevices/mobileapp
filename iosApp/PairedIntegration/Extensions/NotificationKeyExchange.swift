import AccessoryTransportExtension
import ExtensionFoundation
import PebbleForwarding

@main
struct NotificationKeyExchange: AccessoryTransportSecurity {
    @AppExtensionPoint.Bind
    static var boundExtensionPoint: AppExtensionPoint {
        Identifier("com.apple.accessory-transport-security")
    }

    init() { PairedCapabilityProbe.emit(.securityInitialized) }
    func accept(
        sessionRequest: AccessorySecuritySession.Request
    ) -> AccessorySecuritySession.Request.Decision {
        PairedCapabilityProbe.emit(.securityAcceptEntered)
        return KeyExchange.accept(sessionRequest)
    }
}
