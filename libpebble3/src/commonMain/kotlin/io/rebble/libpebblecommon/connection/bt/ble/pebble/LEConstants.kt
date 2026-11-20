package io.rebble.libpebblecommon.connection.bt.ble.pebble

import kotlin.uuid.Uuid

public object LEConstants {
    internal object UUIDs {
        // TODO lower-case all of these (android) or provide a util for comparing them
        // ... or use new kotlin Uuid everywhere
        internal val CHARACTERISTIC_CONFIGURATION_DESCRIPTOR: Uuid = Uuid.parse("00002902-0000-1000-8000-00805f9b34fb")

        internal val PAIRING_SERVICE_UUID: Uuid = Uuid.parse("0000fed9-0000-1000-8000-00805f9b34fb")
        internal val APPLAUNCH_SERVICE_UUID: Uuid = Uuid.parse("20000000-328E-0FBB-C642-1AA6699BDADA")

        internal val CONNECTIVITY_CHARACTERISTIC: Uuid = Uuid.parse("00000001-328E-0FBB-C642-1AA6699BDADA")
        internal val PAIRING_TRIGGER_CHARACTERISTIC: Uuid = Uuid.parse("00000002-328E-0FBB-C642-1AA6699BDADA")
        internal val META_CHARACTERISTIC_SERVER: Uuid = Uuid.parse("10000002-328E-0FBB-C642-1AA6699BDADA")
        internal val APPLAUNCH_CHARACTERISTIC: Uuid = Uuid.parse("20000001-328E-0FBB-C642-1AA6699BDADA")

        // "Reversed" PPoG V1 — shipped on Pebble 2 / silk (Dialog DA1468x).
        // Watch hosts the service, phone is the GATT client. No meta
        // characteristic; legacy firmware accepts data writes on either the
        // READ or WRITE characteristic (the original phone code wrote to READ).
        internal val PPOGATT_DEVICE_SERVICE_UUID_CLIENT: Uuid = Uuid.parse("30000003-328E-0FBB-C642-1AA6699BDADA")
        internal val PPOGATT_DEVICE_CHARACTERISTIC_READ: Uuid = Uuid.parse("30000004-328E-0FBB-C642-1AA6699BDADA")
        internal val PPOGATT_DEVICE_CHARACTERISTIC_WRITE: Uuid = Uuid.parse("30000006-328e-0fbb-c642-1aa6699bdada")

        // "Reversed" PPoG V2 — NimBLE-based watches (asterix / SiFli).
        // Watch hosts the service, phone is the GATT client. Phone sends the
        // first ResetRequest after the phone subscribes; no meta characteristic.
        internal val PPOGATT_WATCH_SERVER_V2_SERVICE: Uuid = Uuid.parse("40000000-328E-0FBB-C642-1AA6699BDADA")
        internal val PPOGATT_WATCH_SERVER_V2_DATA: Uuid = Uuid.parse("40000001-328E-0FBB-C642-1AA6699BDADA")
        internal val PPOGATT_WATCH_SERVER_V2_DATA_WR: Uuid = Uuid.parse("40000003-328E-0FBB-C642-1AA6699BDADA")

        // "Forward" PPoG — phone hosts the GATT service, watch is the client.
        internal val PPOGATT_DEVICE_SERVICE_UUID_SERVER: Uuid = Uuid.parse("10000000-328E-0FBB-C642-1AA6699BDADA")
        internal val PPOGATT_DEVICE_CHARACTERISTIC_SERVER: Uuid = Uuid.parse("10000001-328E-0FBB-C642-1AA6699BDADA")

        internal val CONNECTION_PARAMETERS_CHARACTERISTIC: Uuid = Uuid.parse("00000005-328E-0FBB-C642-1AA6699BDADA")
        internal val PPOG_RESET_CHARACTERISTIC: Uuid = Uuid.parse("00000006-328E-0FBB-C642-1AA6699BDADA")

//        val MTU_CHARACTERISTIC = Uuid.parse("00000003-328E-0FBB-C642-1AA6699BDADA")

        internal val FAKE_SERVICE_UUID: Uuid = Uuid.parse("BADBADBA-DBAD-BADB-ADBA-BADBADBADBAD")
    }

    internal val CHARACTERISTIC_SUBSCRIBE_VALUE: ByteArray = byteArrayOf(1, 0)
    internal val DEFAULT_MTU: Int = 23
    internal val TARGET_MTU: Int = 339
    internal val MAX_RX_WINDOW: Int = 25
    internal val MAX_TX_WINDOW: Int = 25

    internal const val PROPERTY_WRITE: Int = 0x08

    public const val BOND_NONE: Int = 10
    public const val BOND_BONDED: Int = 12 // TODO ios compatible?
    public const val UNBOND_REASON_AUTH_FAILED: Int = 1
    public const val UNBOND_REASON_AUTH_REJECTED: Int = 2
    public const val UNBOND_REASON_AUTH_CANCELLED: Int = 3
}