package coredevices.indexai.data.entity

import coredevices.indexai.util.JsonSnake
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MessageTagSerializationTest {

    @Test
    fun answerTagRoundTripsWithItemId() {
        val message = ConversationMessageDocument(
            role = MessageRole.assistant,
            content = "Sunny",
            messageTags = listOf(MessageTag.AnswerResponse(itemId = "item-1")),
        )
        val json = JsonSnake.encodeToString(ConversationMessageDocument.serializer(), message)
        val decoded = JsonSnake.decodeFromString(ConversationMessageDocument.serializer(), json)
        assertEquals(message, decoded)
        assertEquals("item-1", decoded.answerTag?.itemId)
    }

    @Test
    fun untaggedMessageOmitsTheField() {
        val json = JsonSnake.encodeToString(
            ConversationMessageDocument.serializer(),
            ConversationMessageDocument(role = MessageRole.assistant, content = "Done"),
        )
        assertFalse(json.contains("message_tags"))
        assertNull(JsonSnake.decodeFromString(ConversationMessageDocument.serializer(), json).messageTags)
    }

    @Test
    fun unknownTagTypesAreDroppedWithoutFailingTheMessage() {
        val json = """{"role":"assistant","content":"Sunny","message_tags":[{"type":"FromTheFuture","x":1},{"type":"AnswerResponse","item_id":"item-1"}]}"""
        val decoded = JsonSnake.decodeFromString(ConversationMessageDocument.serializer(), json)
        assertEquals("Sunny", decoded.content)
        assertEquals(listOf(MessageTag.AnswerResponse(itemId = "item-1")), decoded.messageTags)
    }

    @Test
    fun malformedTagListIsDropped() {
        val json = """{"role":"assistant","content":"Sunny","message_tags":"nope"}"""
        assertNull(JsonSnake.decodeFromString(ConversationMessageDocument.serializer(), json).messageTags)
    }

    @Test
    fun onlyAssistantMessagesCountAsAnswers() {
        val tagged = listOf(MessageTag.AnswerResponse())
        assertNull(ConversationMessageDocument(role = MessageRole.user, content = "?", messageTags = tagged).answerTag)
        assertEquals(
            MessageTag.AnswerResponse(),
            ConversationMessageDocument(role = MessageRole.assistant, content = "!", messageTags = tagged).answerTag,
        )
    }
}
