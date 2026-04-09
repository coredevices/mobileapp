package coredevices.ring.agent.builtin_servlets.js

import co.touchlab.kermit.Logger
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class EvaluateJSTool : BuiltInMcpTool(
    definition = Tool(
        name = TOOL_NAME,
        description = TOOL_DESCRIPTION,
        inputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "js" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "The JavaScript code to evaluate and execute"
                        ).toJson()
                    )
                )
            ),
            required = listOf("js")
        ),
        outputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "console_output" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "The console output from the evaluated script"
                        ).toJson()
                    )
                )
            ),
            required = listOf("console_output")
        )
    ),
    extraContext = CONTEXT,
), KoinComponent {
    companion object {
        private val logger = Logger.withTag(EvaluateJSTool::class.simpleName!!)
        const val TOOL_NAME = "evaluate_js"
        const val TOOL_DESCRIPTION =
            "Evaluate a JavaScript expression and get the resulting console output"
        val CONTEXT = """
            When you need to perform a calculation, for example math, date, or string manipulation, you can use JavaScript to evaluate the expression.
            Within a script, use `console.log()` to output any data you need, as you will be provided with the console output only.
            Examples of basic scripts include:
            - `console.log(1 + 2);`
            - `console.log((new Date()).toLocaleString());`
            - ```
                // Get the date and time 4 hours from now
                const relativeHours = 4;
                const now = new Date();
                now.setHours(now.getHours() + relativeHours);
                console.log(now.toISOString());
              ```
            Avoid the use of inline comments in your scripts.
        """.trimIndent()
    }

    @Serializable
    private data class EvaluateJSArgs(
        val js: String
    )

    @Serializable
    private data class EvaluateJSResult(
        @SerialName("console_output")
        val consoleOutput: String
    )

    override suspend fun call(jsonInput: String): ToolCallResult {
        val evaluateJSArgs = JsonSnake.decodeFromString<EvaluateJSArgs>(jsonInput)
        logger.v { "Evaluating JavaScript: ${evaluateJSArgs.js}" }
        val jsEngine: JsEngine = get()
        val consoleOutput = jsEngine.evaluate(evaluateJSArgs.js)
        val result = JsonSnake.encodeToString(EvaluateJSResult(consoleOutput))
        logger.v { "JavaScript evaluation result: $result" }
        return ToolCallResult(
            result,
            semanticResult = SemanticResult.SupportingData("JavaScript evaluation result")
        )
    }
}