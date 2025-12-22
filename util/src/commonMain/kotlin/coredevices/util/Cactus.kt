package coredevices.util

import com.cactus.TranscriptionMode

expect fun getModelDirectories(): List<String>

enum class CactusSTTMode(val cactusValue: TranscriptionMode, val id: Int) {
    Disabled(TranscriptionMode.LOCAL, 0),
    Local(TranscriptionMode.LOCAL, 1), // Vosk
    RemoteFirst(TranscriptionMode.REMOTE_FIRST, 2); // Wispr/Vosk

    companion object {
        fun fromId(id: Int): CactusSTTMode {
            return entries.firstOrNull { it.id == id } ?: Disabled
        }
    }
}
expect fun calculateDefaultSTTModel(): String