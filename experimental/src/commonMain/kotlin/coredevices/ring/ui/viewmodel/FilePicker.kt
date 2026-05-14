package coredevices.ring.ui.viewmodel

import PlatformUiContext
import kotlinx.io.files.Path

expect suspend fun pickZipFile(uiContext: PlatformUiContext): Path?
