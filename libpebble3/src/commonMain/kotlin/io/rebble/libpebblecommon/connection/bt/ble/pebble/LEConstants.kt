package io.rebble.libpebblecommon.connection.bt.ble.pebble

import kotlin.uuid.Uuid

object LEConstants {
    object UUIDs {
        // TODO lower-case all of these (android) or provide a util for comparing them
        // ... or use new kotlin Uuid everywhere
        val CHARACTERISTIC_CONFIGURATION_DESCRIPTOR = Uuid.parse("00002902-0000-1000-8000-00805f9b34fb")

        val PAIRING_SERVICE_UUID = Uuid.parse("0000fed9-0000-1000-8000-00805f9b34fb")
        val APPLAUNCH_SERVICE_UUID = Uuid.parse("20000000-328E-0FBB-C642-1AA6699BDADA")

        val CONNECTIVITY_CHARACTERISTIC = Uuid.parse("00000001-328E-0FBB-C642-1AA6699BDADA")
        val PAIRING_TRIGGER_CHARACTERISTIC = Uuid.parse("00000002-328E-0FBB-C642-1AA6699BDADA")
        val META_CHARACTERISTIC_SERVER = Uuid.parse("10000002-328E-0FBB-C642-1AA6699BDADA")
        val APPLAUNCH_CHARACTERISTIC = Uuid.parse("20000001-328E-0FBB-C642-1AA6699BDADA")

        // "Reversed" PPoG V1 — shipped on Pebble 2 / silk (Dialog DA1468x).
        // Watch hosts the service, phone is the GATT client. No meta
        // characteristic; legacy firmware accepts data writes on either the
        // READ or WRITE characteristic (the original phone code wrote to READ).
        val PPOGATT_DEVICE_SERVICE_UUID_CLIENT = Uuid.parse("30000003-328E-0FBB-C642-1AA6699BDADA")
        val PPOGATT_DEVICE_CHARACTERISTIC_READ = Uuid.parse("30000004-328E-0FBB-C642-1AA6699BDADA")
        val PPOGATT_DEVICE_CHARACTERISTIC_WRITE = Uuid.parse("30000006-328e-0fbb-c642-1aa6699bdada")

        // "Reversed" PPoG V2 — NimBLE-based watches (asterix / SiFli).
        // Watch hosts the service, phone is the GATT client. Phone sends the
        // first ResetRequest after the phone subscribes; no meta characteristic.
        val PPOGATT_WATCH_SERVER_V2_SERVICE = Uuid.parse("40000000-328E-0FBB-C642-1AA6699BDADA")
        val PPOGATT_WATCH_SERVER_V2_DATA = Uuid.parse("40000001-328E-0FBB-C642-1AA6699BDADA")
        val PPOGATT_WATCH_SERVER_V2_DATA_WR = Uuid.parse("40000003-328E-0FBB-C642-1AA6699BDADA")

        // "Forward" PPoG — phone hosts the GATT service, watch is the client.
        val PPOGATT_DEVICE_SERVICE_UUID_SERVER = Uuid.parse("10000000-328E-0FBB-C642-1AA6699BDADA")
        val PPOGATT_DEVICE_CHARACTERISTIC_SERVER = Uuid.parse("10000001-328E-0FBB-C642-1AA6699BDADA")

        val CONNECTION_PARAMETERS_CHARACTERISTIC = Uuid.parse("00000005-328E-0FBB-C642-1AA6699BDADA")
        val PPOG_RESET_CHARACTERISTIC = Uuid.parse("00000006-328E-0FBB-C642-1AA6699BDADA")

//        val MTU_CHARACTERISTIC = Uuid.parse("00000003-328E-0FBB-C642-1AA6699BDADA")

        val FAKE_SERVICE_UUID = Uuid.parse("BADBADBA-DBAD-BADB-ADBA-BADBADBADBAD")
    }

    val CHARACTERISTIC_SUBSCRIBE_VALUE = byteArrayOf(1, 0)
    val DEFAULT_MTU = 23
    val TARGET_MTU = 339
    val MAX_RX_WINDOW: Int = 25
    val MAX_TX_WINDOW: Int = 25

    const val PROPERTY_WRITE = 0x08

    const val BOND_NONE = 10
    const val BOND_BONDED = 12 // TODO ios compatible?
    const val UNBOND_REASON_AUTH_FAILED = 1
    const val UNBOND_REASON_AUTH_REJECTED = 2
    const val UNBOND_REASON_AUTH_CANCELLED = 3
}