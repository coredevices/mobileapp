package coredevices.util.models

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

fun promoteSingleRootDir(dir: Path) {
    val root = SystemFileSystem.list(dir).singleOrNull() ?: return
    if (SystemFileSystem.metadataOrNull(root)?.isDirectory != true) return
    SystemFileSystem.list(root).forEach { child ->
        SystemFileSystem.atomicMove(child, Path(dir, child.name))
    }
    SystemFileSystem.delete(root)
}

enum class CactusSTTMode(val id: Int) {
    RemoteOnly(0),
    LocalOnly(1),
    RemoteFirst(2),
    LocalFirst(3),
    RebbleOnly(4),
    RebbleFirst(5),
    RebbleFallback(6),

    /** OS-native on-device engine: Apple SpeechAnalyzer (iOS 26+) / ML Kit GenAI (Android). */
    PlatformOnly(7);

    companion object {
        fun fromId(id: Int): CactusSTTMode {
            return entries.firstOrNull { it.id == id } ?: RemoteOnly
        }
    }

    fun usesLocalCactus(): Boolean {
        return this in setOf(RemoteFirst, LocalOnly, LocalFirst, RebbleFirst, RebbleFallback)
    }
}