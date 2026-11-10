package theme

import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun setStatusBarTheme(colorScheme: CoreAppColorScheme) {
    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(colorScheme) {
        if (activity != null) {
            val style = if (colorScheme.isDark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            }
            activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
    }
}

actual fun supportsDynamicTheme(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
actual fun getMaterialColorScheme(dynamicColor: Boolean, colorScheme: CoreAppColorScheme): ColorScheme {
    val context = LocalContext.current
    return if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        when (colorScheme) {
            CoreAppColorScheme.Light -> dynamicLightColorScheme(context)
            CoreAppColorScheme.Grey -> dynamicDarkColorScheme(context)
            CoreAppColorScheme.Black -> dynamicDarkColorScheme(context).copy(
                background = androidx.compose.ui.graphics.Color.Black,
                surface = androidx.compose.ui.graphics.Color.Black,
                scrim = androidx.compose.ui.graphics.Color.Black,
                surfaceDim = androidx.compose.ui.graphics.Color.Black,
                surfaceContainerLowest = androidx.compose.ui.graphics.Color.Black,
            )
        }
    } else {
        when (colorScheme) {
            CoreAppColorScheme.Light -> lightScheme
            CoreAppColorScheme.Grey -> greyScheme
            CoreAppColorScheme.Black -> blackScheme
        }
    }
}
