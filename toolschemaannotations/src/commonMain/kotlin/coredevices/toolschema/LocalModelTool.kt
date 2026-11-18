package coredevices.toolschema

/** The built-in tools as the on-device model version [modelVersion] was trained on them. */
data class LocalModelSchemaCheckpoint(
    val modelVersion: String,
    val excludedServlets: Set<String>,
    val tools: List<LocalModelTool>,
)

data class LocalModelTool(
    val servlet: String,
    val name: String,
    val description: String,
    val parameters: List<LocalModelToolParameter>,
)

data class LocalModelToolParameter(
    val name: String,
    val type: String,
    val description: String?,
    val required: Boolean,
)
