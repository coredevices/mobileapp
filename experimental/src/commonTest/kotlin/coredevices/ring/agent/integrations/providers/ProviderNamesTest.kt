package coredevices.ring.agent.integrations.providers

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderNamesTest {
    @Test
    fun matchesWholeWordsIgnoringCaseAndSpacing() {
        assertTrue("Set a reminder on TickTick to call mum".mentionsProviderName("TickTick"))
        assertTrue("set a reminder on tick tick to call mum".mentionsProviderName("TickTick"))
        assertTrue("add this to google tasks.".mentionsProviderName("Google Tasks"))
        assertTrue("Remind me in Things".mentionsProviderName("Things"))
    }

    @Test
    fun doesNotMatchInsideLongerWords() {
        assertFalse("beekeeper".mentionsProviderName("Keep"))
        assertFalse("the tickticker".mentionsProviderName("TickTick"))
        assertFalse("tick tock".mentionsProviderName("TickTick"))
        assertFalse("anything".mentionsProviderName(""))
    }
}
