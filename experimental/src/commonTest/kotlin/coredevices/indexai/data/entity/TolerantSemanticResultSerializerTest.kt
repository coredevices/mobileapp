package coredevices.indexai.data.entity

import coredevices.indexai.util.JsonSnake
import coredevices.mcp.data.SemanticResult
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
    fun canonicalDiscriminatorStillDecodes() {
        val msg = decodeTree<ConversationMessageDocument>(
            """{"role":"tool","semantic_result":{"type":"ListItemCreation","content":"milk"}}"""
        )
        val sr = msg.semantic_result
        assertTrue(sr is SemanticResult.ListItemCreation)
        assertEquals("milk", sr.content)
    }
}
