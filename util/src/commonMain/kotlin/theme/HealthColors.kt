package theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class HealthColors(
    val steps: Color,
    val heartRate: Color,
    val lightSleep: Color,
    val deepSleep: Color,
)

val localHealthColors = staticCompositionLocalOf {
    HealthColors(
        steps = Color.Unspecified,
        heartRate = Color.Unspecified,
        lightSleep = Color.Unspecified,
        deepSleep = Color.Unspecified,
    )
}