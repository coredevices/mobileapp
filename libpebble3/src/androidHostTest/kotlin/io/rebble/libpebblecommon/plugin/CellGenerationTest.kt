package io.rebble.libpebblecommon.plugin

import android.telephony.TelephonyManager
import kotlin.test.Test
import kotlin.test.assertEquals

class CellGenerationTest {

    @Test
    fun `network types map to the generation they belong to`() {
        assertEquals(CellGenerations.TWO_G, TelephonyManager.NETWORK_TYPE_EDGE.generation())
        assertEquals(CellGenerations.THREE_G, TelephonyManager.NETWORK_TYPE_HSPAP.generation())
        assertEquals(CellGenerations.FOUR_G, TelephonyManager.NETWORK_TYPE_LTE.generation())
        assertEquals(CellGenerations.FIVE_G, TelephonyManager.NETWORK_TYPE_NR.generation())
    }

    /** A phone with no data connection reports the same unknown type as one with no SIM. */
    @Test
    fun `an unknown network type is no service`() {
        assertEquals(CellGenerations.NONE, TelephonyManager.NETWORK_TYPE_UNKNOWN.generation())
    }
}
