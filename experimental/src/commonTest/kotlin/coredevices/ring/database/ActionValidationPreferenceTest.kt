package coredevices.ring.database

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionValidationPreferenceTest {

    @Test
    fun defaultsToOnAndPersistsBeingTurnedOff() {
        val settings = MapSettings()
        val preferences = PreferencesImpl(settings)
        assertTrue(preferences.actionValidationEnabled.value)

        preferences.setActionValidationEnabled(false)

        assertFalse(preferences.actionValidationEnabled.value)
        assertFalse(PreferencesImpl(settings).actionValidationEnabled.value)
    }
}
