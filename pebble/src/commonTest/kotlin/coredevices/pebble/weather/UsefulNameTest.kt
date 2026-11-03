package coredevices.pebble.weather

import dev.jordond.compass.Coordinates
import dev.jordond.compass.Place
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsefulNameTest {

    @Test
    fun prefersLocality() {
        assertEquals("Portland", place(locality = "Portland", administrativeArea = "Oregon").usefulName())
    }

    @Test
    fun fallsBackToAdministrativeAreasBeforeStreet() {
        val place = place(
            street = "5R58+F8C, 87 Đ. Vân Hồ",
            subAdministrativeArea = "Mộc Châu",
            administrativeArea = "Sơn La",
        )
        assertEquals("Mộc Châu", place.usefulName())
        assertEquals("Sơn La", place(street = "5R58+F8C", administrativeArea = "Sơn La").usefulName())
    }

    @Test
    fun ignoresPlusCodeStreet() {
        assertNull(place(street = "5R58+F8C, 87 Đ. Vân Hồ").usefulName())
        assertNull(place(street = "7FG9VXHR+X2").usefulName())
    }

    @Test
    fun keepsOrdinaryStreet() {
        assertEquals("1 Main St", place(street = "1 Main St").usefulName())
    }
}

private fun place(
    street: String? = null,
    administrativeArea: String? = null,
    subAdministrativeArea: String? = null,
    locality: String? = null,
) = Place(
    coordinates = Coordinates(0.0, 0.0),
    name = null,
    street = street,
    isoCountryCode = null,
    country = null,
    postalCode = null,
    administrativeArea = administrativeArea,
    subAdministrativeArea = subAdministrativeArea,
    locality = locality,
    subLocality = null,
    thoroughfare = null,
    subThoroughfare = null,
)
