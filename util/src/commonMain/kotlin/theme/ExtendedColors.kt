package theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ExtendedColors(
    val primary20: Color,
    val onPrimary20: Color,
    val warning: Color,
    val onWarning: Color,
    val success: Color,
    val onSuccess: Color,
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        primary20 = Color.Unspecified,
        onPrimary20 = Color.Unspecified,
        warning = Color.Unspecified,
        onWarning = Color.Unspecified,
        success = Color.Unspecified,
        onSuccess = Color.Unspecified,
    )
}