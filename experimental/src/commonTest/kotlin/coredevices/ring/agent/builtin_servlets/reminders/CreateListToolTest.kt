package coredevices.ring.agent.builtin_servlets.reminders

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CreateListToolTest {

    @Test
    fun acceptsExactTriggerPhrases() {
        assertTrue(CreateListTool.isTriggerPhrase("Create a new list travel"))
        assertTrue(CreateListTool.isTriggerPhrase("Create a new list called travel"))
        assertTrue(CreateListTool.isTriggerPhrase("create a new list Travel."))
        assertTrue(CreateListTool.isTriggerPhrase(" Create a new list called packing"))
        assertTrue(CreateListTool.isTriggerPhrase("Please create a new list travel"))
        assertTrue(CreateListTool.isTriggerPhrase("Hey, can you create a new list called packing"))
    }

    @Test
    fun rejectsAnyOtherPhrasing() {
        assertFalse(CreateListTool.isTriggerPhrase(null))
        assertFalse(CreateListTool.isTriggerPhrase(""))
        assertFalse(CreateListTool.isTriggerPhrase("Make a new list travel"))
        assertFalse(CreateListTool.isTriggerPhrase("I want a new list for travel"))
        assertFalse(CreateListTool.isTriggerPhrase("Add milk to my shopping list"))
        assertFalse(CreateListTool.isTriggerPhrase("Start a packing list"))
        assertFalse(CreateListTool.isTriggerPhrase("Create a list called travel"))
        assertFalse(CreateListTool.isTriggerPhrase("New list travel"))
        assertFalse(CreateListTool.isTriggerPhrase("Create a new listing for the house"))
    }
}
