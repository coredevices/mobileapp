package coredevices.util

import PlatformUiContext
import kotlinx.io.files.Path

expect suspend fun writeToDownloads(uiContext: PlatformUiContext, path: Path, mimeType: String)
