package coredevices.ring.ui.theme

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle

internal actual fun TextStyle.indexTextEntryStyle(): TextStyle {
    return copy(platformStyle = PlatformTextStyle(includeFontPadding = true))
}
