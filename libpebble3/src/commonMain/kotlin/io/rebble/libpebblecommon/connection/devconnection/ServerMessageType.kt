package io.rebble.libpebblecommon.connection.devconnection

enum class ServerMessageType(val value: Byte) {
    RelayFromWatch(0x00),
    RelayToWatch(0x01),
    PhoneAppLog(0x02),
    PhoneServerLog(0x03),
    InstallStatus(0x05),
    PhoneInfo(0x06),
    ConnectionStatusUpdate(0x07),
    ProxyConnectionStatusUpdate(0x08),
    ProxyAuthentication(0x09),
    PhonesimAppConfigResponse(0x0A),
    TimelinePinResponse(0x0C),
    ProxyAuthenticationV2(0x19),
}