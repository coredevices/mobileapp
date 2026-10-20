package theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
actual fun setStatusBarTheme(colorScheme: CoreAppColorScheme) {
}

@Composable
actual fun getMaterialColorScheme(theme: CoreAppTheme, colorScheme: CoreAppColorScheme): ColorScheme {
    return when (colorScheme) {
        CoreAppColorScheme.Light -> lightScheme
        CoreAppColorScheme.Grey -> greyScheme
        CoreAppColorScheme.Black -> blackScheme
    }
}
