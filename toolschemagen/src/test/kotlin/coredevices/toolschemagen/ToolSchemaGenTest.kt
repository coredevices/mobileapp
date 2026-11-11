package coredevices.toolschemagen

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ToolSchemaGenTest {
    private fun tool(servlet: String, name: String, vararg parameters: ToolParameterModel) =
        ToolModel(servlet, name, "$name description", parameters.toList())

    private val tools = listOf(
        tool("kept", "a", ToolParameterModel("text", "string", "the text", required = true)),
        tool("dropped", "b"),
    )

    private fun tempDir(): File = Files.createTempDirectory("checkpoints").toFile().also { it.deleteOnExit() }

    @Test
    fun excludedServletsContributeNoTools() {
        val checkpoint = checkpointOf("v1", listOf("dropped"), tools)

        assertEquals(listOf("a"), checkpoint.tools.map { it.name })
    }

    @Test
    fun excludingAServletWithNoToolsFails() {
        assertFailsWith<IllegalArgumentException> { checkpointOf("v1", listOf("typo"), tools) }
    }

    @Test
    fun aVersionsCheckpointIsWrittenOnceThenFrozen() {
        val dir = tempDir()
        val first = loadOrCreateCheckpoint(dir, "v1", listOf("dropped"), tools)

        val changed = listOf(tool("kept", "a"), tool("kept", "new"))
        val second = loadOrCreateCheckpoint(dir, "v1", emptyList(), changed)

        assertEquals(first, second)
        assertEquals(listOf("v1.json"), dir.list()?.toList())
    }

    @Test
    fun aNewVersionSnapshotsTheCurrentTools() {
        val dir = tempDir()
        loadOrCreateCheckpoint(dir, "v1", listOf("dropped"), tools)

        val changed = listOf(tool("kept", "a"), tool("kept", "new"))
        val next = loadOrCreateCheckpoint(dir, "v2", emptyList(), changed)

        assertEquals(Checkpoint("v2", emptyList(), changed), next)
        assertEquals(setOf("v1.json", "v2.json"), dir.list()?.toSet())
    }

    @Test
    fun definitionsRenderEachToolsSchema() {
        val rendered = renderDefinitions(
            "pkg",
            listOf(
                tool(
                    "s", "set_thing",
                    ToolParameterModel("count", "number", "how \"many\" for \$5", required = true),
                    ToolParameterModel("label", "string", null, required = false),
                )
            ),
        )

        assertContains(rendered, """"set_thing" to Tool(""")
        assertContains(rendered, """"description" to JsonPrimitive("how \"many\" for \$5"),""")
        assertContains(rendered, """required = listOf("count"),""")
        assertFalse(rendered.contains("null"))
    }

    @Test
    fun outputSchemaIsRenderedButKeptOutOfTheCheckpoint() {
        val withOutput = tool("kept", "a").copy(
            outputParameters = listOf(ToolParameterModel("id", "string", "the id", required = false)),
        )

        assertContains(renderDefinitions("pkg", listOf(withOutput)), "outputSchema = ToolSchema(")
        assertFalse(renderDefinitions("pkg", listOf(tool("kept", "a"))).contains("outputSchema"))
        assertEquals(listOf(tool("kept", "a")), checkpointOf("v1", emptyList(), listOf(withOutput)).tools)
    }

    @Test
    fun checkpointRendersVersionAndNullDescriptions() {
        val rendered = renderCheckpoint(
            "pkg",
            Checkpoint("v9", listOf("x"), listOf(tool("s", "t", ToolParameterModel("p", "string", null, required = false)))),
        )

        assertContains(rendered, """modelVersion = "v9",""")
        assertContains(rendered, "description = null,")
    }

    @Test
    fun propertyNamesBecomeSnakeCase() {
        assertEquals("contact_name", "contactName".toSnakeCase())
        assertEquals("list_name", "list_name".toSnakeCase())
        assertEquals("text", "text".toSnakeCase())
    }
}
