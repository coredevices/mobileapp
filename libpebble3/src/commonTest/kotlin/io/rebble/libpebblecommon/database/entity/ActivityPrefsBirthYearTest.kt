package io.rebble.libpebblecommon.database.entity

import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityPrefsBirthYearTest {
    @Test
    fun ageFollowsBirthYear() {
        val prefs = ActivityPrefsValue(ageYears = 52, birthYear = 1973)
        assertEquals(53, prefs.withBirthYear(currentYear = 2026).ageYears)
        assertEquals(54, prefs.withBirthYear(currentYear = 2027).ageYears)
    }

    @Test
    fun missingBirthYearIsDerivedFromAge() {
        val migrated = ActivityPrefsValue(ageYears = 52).withBirthYear(currentYear = 2026)
        assertEquals(1974, migrated.birthYear)
        assertEquals(52, migrated.ageYears)
        assertEquals(53, migrated.withBirthYear(currentYear = 2027).ageYears)
    }

    @Test
    fun alreadySyncedPrefsAreUnchanged() {
        val prefs = ActivityPrefsValue(ageYears = 53, birthYear = 1973)
        assertEquals(prefs, prefs.withBirthYear(currentYear = 2026))
    }
}
