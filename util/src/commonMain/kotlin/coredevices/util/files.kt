package coredevices.util

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

fun deleteRecursive(path: Path) {
    if (SystemFileSystem.exists(path)) {
        if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) {
            SystemFileSystem.list(path).forEach {
                deleteRecursive(it)
            }
        }
        SystemFileSystem.delete(path, mustExist = false)
    }
}