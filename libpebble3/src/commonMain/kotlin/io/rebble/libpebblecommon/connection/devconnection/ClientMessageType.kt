package io.rebble.libpebblecommon.connection.devconnection

enum class ClientMessageType(val value: Byte) {
    RelayToWatch(0x01),
    InstallBundle(0x04),
    //PhoneInfo(0x06),
    ConnectionStatus(0x08),
    //ProxyAuthenticationRequest(0x09),
    //PhonesimAppConfig(0x0A),
    //RelayQemu(0x0B),
    TimelinePin(0x0C),
    InstallFirmware(0x0D);

    companion object {
        fun fromValue(value: Byte): ClientMessageType? {
            return entries.find { it.value == value }
        }
    }
}