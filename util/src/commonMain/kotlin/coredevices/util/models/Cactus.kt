package coredevices.util.models

import com.cactus.TranscriptionMode

enum class CactusSTTMode(val cactusValue: TranscriptionMode, val id: Int) {
    RemoteOnly(TranscriptionMode.REMOTE, 0),
    LocalOnly(TranscriptionMode.LOCAL, 1), // Whisper
    RemoteFirst(TranscriptionMode.REMOTE_FIRST, 2); // Wispr/Whisper

    companion object {
        fun fromId(id: Int): CactusSTTMode {
            return entries.firstOrNull { it.id == id } ?: RemoteOnly
        }
    }
}