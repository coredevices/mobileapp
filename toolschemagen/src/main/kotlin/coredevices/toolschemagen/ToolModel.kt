package coredevices.toolschemagen

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
internal data class ToolModel(
    val servlet: String,
    val name: String,
    val description: String,
    val parameters: List<ToolParameterModel>,
    /** Only cloud agents get an output schema, so it stays out of the checkpoint. */
    @Transient val outputParameters: List<ToolParameterModel>? = null,
    /** Where a checkpoint taken before the tool moved has it. */
    @Transient val localModelServlet: String? = null,
)

@Serializable
internal data class ToolParameterModel(
    val name: String,
    val type: String,
    val description: String? = null,
    val required: Boolean,
)

/** The committed `<modelVersion>.json` snapshot of the tools the on-device model was trained on. */
@Serializable
internal data class Checkpoint(
    val modelVersion: String,
    val excludedServlets: List<String>,
    val tools: List<ToolModel>,
)

internal fun checkpointOf(
    modelVersion: String,
    excludedServlets: List<String>,
    tools: List<ToolModel>,
): Checkpoint {
    val unknown = excludedServlets - tools.map { it.servlet }.toSet()
    require(unknown.isEmpty()) { "Excluded servlets that have no tools: $unknown" }
    return Checkpoint(
        modelVersion,
        excludedServlets,
        tools.filterNot { it.servlet in excludedServlets }.map { it.copy(outputParameters = null) },
    )
}

/** The checkpoint with each of the [tools] that moved since it was taken under the servlet serving it now. */
internal fun Checkpoint.servedBy(tools: List<ToolModel>): Checkpoint {
    val moved = tools.mapNotNull { tool -> tool.localModelServlet?.let { (it to tool.name) to tool.servlet } }.toMap()
    val stale = moved.keys - this.tools.map { it.servlet to it.name }.toSet()
    require(stale.isEmpty()) { "Checkpoint $modelVersion has no $stale: drop their localModelServlet" }
    return copy(tools = this.tools.map { it.copy(servlet = moved[it.servlet to it.name] ?: it.servlet) })
}
