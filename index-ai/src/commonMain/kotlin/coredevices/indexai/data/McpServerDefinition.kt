package coredevices.indexai.data

data class McpServerDefinition(
    val name: String,
    val title: String,
    /** Non-null when this servlet cannot run on the current platform; shown to the user. */
    val unavailableReason: String? = null,
) {
    val available: Boolean get() = unavailableReason == null
}
