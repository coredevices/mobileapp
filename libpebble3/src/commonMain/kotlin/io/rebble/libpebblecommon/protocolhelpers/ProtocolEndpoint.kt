package io.rebble.libpebblecommon.protocolhelpers

import co.touchlab.kermit.Logger

enum class ProtocolEndpoint(val value: UShort) {
    RECOVERY(0u),
    TIME(11u),
    WATCH_VERSION(16u),
    PHONE_VERSION(17u),
    SYSTEM_MESSAGE(18u),
    MUSIC_CONTROL(32u),
    PHONE_CONTROL(33u),
    IMAGING(53u /* 0x35 */),
    APP_MESSAGE(48u),
    LEGACY_APP_LAUNCH(49u),
    APP_CUSTOMIZE(50u),
    BLE_CONTROL(51u),
    APP_RUN_STATE(52u),
    LOGS(2000u),
    PING(2001u),
    LOG_DUMP(2002u),
    RESET(2003u),
    APP_LOGS(2006u),
    SYS_REG(5000u),
    FCT_REG(5001u),
    APP_FETCH(6001u),
    PUT_BYTES(48879u /* 0xbeef */),
    DATA_LOG(6778u),
    SCREENSHOT(8000u),
    FILE_INSTALL_MANAGER(8181u),
    GET_BYTES(9000u),
    AUDIO_STREAMING(10000u),
    APP_REORDER(43981u /* 0xabcd */),
    BLOBDB_V1(45531u /* 0xb1db */),
    BLOBDB_V2(45787u /* 0xb2db */),
    TIMELINE_ACTIONS(11440u),
    VOICE_CONTROL(11000u),
    HEALTH_SYNC(911u),
    INVALID_ENDPOINT(0xffffu);

    companion object {
        private val values = entries.toTypedArray()
        fun getByValue(value: UShort) = values.firstOrNull { it.value == value }
            ?: INVALID_ENDPOINT.also {
                Logger.e {
                    "Received unknown packet endpoint: 0x${value.toInt().toString(16)}"
                }
            }
    }
}