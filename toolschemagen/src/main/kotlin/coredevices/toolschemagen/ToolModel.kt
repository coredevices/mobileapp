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
