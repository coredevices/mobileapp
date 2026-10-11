package coredevices.indexai.data.entity

import coredevices.indexai.util.JsonSnake
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.withItemId
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `semantic_result` on messages in the shared Firestore `recordings` collection has been
 * written with discriminators that don't match the sealed type's `@SerialName`s (e.g.
 * index-webapp's snake_case `list_item_creation`, MOB-9181). Decode via the tree-based path
 * (matching gitlive's map decoder) and assert an unknown discriminator degrades to null
 * instead of failing the whole message decode.
 */
class TolerantSemanticResultSerializerTest {

    private inline fun <reified T> decodeTree(json: String): T =
        JsonSnake.decodeFromJsonElement(JsonSnake.parseToJsonElement(json))

    @Test
    fun unknownSnakeCaseDiscriminatorDecodesToNull() {
        val msg = decodeTree<ConversationMessageDocument>(
            """{"role":"tool","content":"c","semantic_result":{"type":"list_item_creation","content":"milk"}}"""
        )
        assertEquals(MessageRole.tool, msg.role)
        assertEquals("c", msg.content)
        assertNull(msg.semantic_result)
    }

    @Test
    fun unknownDiscriminatorDecodesToNullOnTheStreamingPath() {
        // The Room converter decodes the bare result string; a removed type (e.g. "Response")
        // must not leave the lexer mid-object.
        val legacy = """{"type":"Response","text":"Hello!","question":"Hi"}"""
        assertNull(JsonSnake.decodeFromString(TolerantSemanticResultSerializer, legacy))

        val msg = JsonSnake.decodeFromString(
            ConversationMessageDocument.serializer(),
            """{"role":"tool","content":"c","semantic_result":$legacy,"tool_call_id":"call-1"}""",
        )
        assertNull(msg.semantic_result)
        assertEquals("call-1", msg.tool_call_id)
    }

    @Test
    fun canonicalDiscriminatorStillDecodes() {
        val msg = decodeTree<ConversationMessageDocument>(
            """{"role":"tool","semantic_result":{"type":"ListItemCreation","content":"milk"}}"""
        )
        val sr = msg.semantic_result
        assertTrue(sr is SemanticResult.ListItemCreation)
        assertEquals("milk", sr.content)
    }

    @Test
    fun resultsWrittenBeforeItemIdExistedDecodeWithNullItemId() {
        val msg = decodeTree<ConversationMessageDocument>(
            """{"role":"tool","semantic_result":{"type":"TaskCreation","title":"Call mom","deadline":null}}"""
        )
        val sr = msg.semantic_result
        assertTrue(sr is SemanticResult.TaskCreation)
        assertNull(sr.itemId)
        assertNull(sr.destination)
    }

    @Test
    fun itemIdRoundTripsAndIsAbsentForResultsThatCreateNoItem() {
        val stamped = SemanticResult.ListItemCreation("milk").withItemId("item-1")
        assertEquals("item-1", stamped.itemId)
        val json = JsonSnake.encodeToString(SemanticResult.serializer(), stamped)
        assertEquals("item-1", JsonSnake.decodeFromString(SemanticResult.serializer(), json).itemId)

        val lookup = SemanticResult.SupportingData("info").withItemId("item-2")
        assertNull(lookup.itemId)
        assertEquals(SemanticResult.SupportingData("info"), lookup)
    }
}
