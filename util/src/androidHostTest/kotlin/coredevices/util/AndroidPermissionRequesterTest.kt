package coredevices.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class AndroidPermissionRequesterTest {

    private val coarse = "android.permission.ACCESS_COARSE_LOCATION"
    private val fine = "android.permission.ACCESS_FINE_LOCATION"
    private val noRationale: (String) -> Boolean = { false }
    private val notAsked: (String) -> Boolean = { fail("rationale consulted for $it") }

    @Test
    fun approximateGrantSatisfiesLocation() {
        val approximate = mapOf(coarse to true, fine to false)
        assertEquals(PermissionResult.Granted, Permission.Location.resultFor(approximate, notAsked))
    }

    @Test
    fun emptyResultIsRejectedNotGranted() {
        assertEquals(PermissionResult.Rejected, Permission.Location.resultFor(emptyMap(), notAsked))
    }

    @Test
    fun fullDenialIsClassifiedByRationale() {
        val denied = mapOf(coarse to false, fine to false)
        assertEquals(PermissionResult.Rejected, Permission.Location.resultFor(denied) { true })
        assertEquals(PermissionResult.RejectedForever, Permission.Location.resultFor(denied, noRationale))
    }

    @Test
    fun rationaleIsCheckedOnAPermissionThatWasDenied() {
        var asked: String? = null
        val result = Permission.Bluetooth.resultFor(mapOf("scan" to true, "connect" to false)) {
            asked = it
            true
        }
        assertEquals(PermissionResult.Rejected, result)
        assertEquals("connect", asked)
    }

    @Test
    fun partialGrantOnlySatisfiesLocation() {
        assertTrue(Permission.Location.isGrantedBy(listOf(false, true)))
        assertFalse(Permission.Calendar.isGrantedBy(listOf(true, false)))
        assertTrue(Permission.Calendar.isGrantedBy(listOf(true, true)))
    }

    @Test
    fun nothingToGrantIsSatisfied() {
        Permission.entries
            .filter { it != Permission.Location }
            .forEach { assertTrue(it.isGrantedBy(emptyList()), "$it") }
    }
}
