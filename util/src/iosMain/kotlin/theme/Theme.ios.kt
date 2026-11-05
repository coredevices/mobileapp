package theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
actual fun setStatusBarTheme(colorScheme: CoreAppColorScheme) {
}

actual fun supportsDynamicTheme(): Boolean = false

@Composable
actual fun getMaterialColorScheme(dynamicColor: Boolean, colorScheme: CoreAppColorScheme): ColorScheme {
    return when (colorScheme) {
        CoreAppColorScheme.Light -> lightScheme
        CoreAppColorScheme.Grey -> greyScheme
        CoreAppColorScheme.Black -> blackScheme
    }
}
