package coredevices.toolschemagen

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import kotlin.io.path.writeText

@OptIn(ExperimentalSerializationApi::class)
private val checkpointJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

/**
 * Returns the checkpoint committed for [modelVersion] in [dir]. One is only written, from [tools],
 * when that version has none yet, so a version's checkpoint never follows later schema changes.
 */
internal fun loadOrCreateCheckpoint(
    dir: File,
    modelVersion: String,
    excludedServlets: List<String>,
    tools: List<ToolModel>,
): Checkpoint {
    val file = File(dir, "$modelVersion.json")
    if (file.exists()) return checkpointJson.decodeFromString(file.readText())
    val checkpoint = checkpointOf(modelVersion, excludedServlets, tools)
    dir.mkdirs()
    // Each target's KSP task runs this; whichever lands first wins and the rest read it back.
    val temp = Files.createTempFile(dir.toPath(), modelVersion, ".tmp")
    temp.writeText(checkpointJson.encodeToString(checkpoint) + "\n")
    try {
        Files.move(temp, file.toPath())
    } catch (e: FileAlreadyExistsException) {
        Files.delete(temp)
    }
    return checkpointJson.decodeFromString(file.readText())
}
