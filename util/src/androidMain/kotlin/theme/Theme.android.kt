package theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
actual fun setStatusBarTheme(colorScheme: CoreAppColorScheme) {
    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(colorScheme) {
        if (activity != null) {
            when (colorScheme) {
                CoreAppColorScheme.Grey -> activity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(
                        Color.TRANSPARENT,
                    ),
                    navigationBarStyle = SystemBarStyle.dark(
                        Color.TRANSPARENT,
                    ),
                )

                CoreAppColorScheme.Light -> activity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.light(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ),
                    navigationBarStyle = SystemBarStyle.light(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ),
                )
            }
        }
    }
}