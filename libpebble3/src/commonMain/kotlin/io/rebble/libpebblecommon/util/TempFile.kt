package io.rebble.libpebblecommon.util

import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.io.files.Path

expect fun getTempFilePath(appContext: AppContext, name: String, subdir: String? = null): Path