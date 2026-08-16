package coredevices.ring.database

import com.russhwolf.settings.MapSettings
import coredevices.ring.agent.LlmMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LlmModePreferenceTest {

    @Test
    fun defaultsToCloudOnly() {
        assertEquals(LlmMode.RemoteOnly, PreferencesImpl(MapSettings()).llmMode.value)
    }

    @Test
    fun migratesTheLegacyLocalLlmSwitch() {
        val settings = MapSettings("use_cactus_agent" to true)
        assertEquals(LlmMode.LocalOnly, PreferencesImpl(settings).llmMode.value)
    }

    @Test
    fun storedModeWinsOverTheLegacySwitch() {
        val settings = MapSettings("use_cactus_agent" to true, "llm_mode" to LlmMode.RemoteFirst.id)
        assertEquals(LlmMode.RemoteFirst, PreferencesImpl(settings).llmMode.value)
    }

    @Test
    fun persistsAndPublishesTheSelectedMode() = runTest {
        val settings = MapSettings()
        val preferences = PreferencesImpl(settings)

        preferences.setLlmMode(LlmMode.RemoteFirst)

        assertEquals(LlmMode.RemoteFirst, preferences.llmMode.value)
        assertEquals(LlmMode.RemoteFirst, PreferencesImpl(settings).llmMode.value)
    }
}
