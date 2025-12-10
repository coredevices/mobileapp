package theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.russhwolf.settings.Settings
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.dark
import coreapp.util.generated.resources.light
import coreapp.util.generated.resources.system
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.koinInject
import theme.CoreAppTheme.Companion.asCoreAppTheme

val coreOrange = Color(0xFFFA4A36)
val coreGrey = Color(0xFF333333)
val coreDarkGrey = Color(0xFF2B2930)
private val error = Color(0xFFFA6B66)
val greyScheme = darkColorScheme(
    primary = coreOrange,
    onPrimary = Color.White,
    primaryContainer = coreOrange,
    onPrimaryContainer = Color.White,
    secondary = coreOrange,
    onSecondary = Color.White,
    secondaryContainer = coreOrange,
    onSecondaryContainer = Color.White,
    tertiary = coreOrange,
    onTertiary = Color.White,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = error,
    onError = Color.White,
    errorContainer = error,
    onErrorContainer = Color.White,
    background = coreGrey,
    onBackground = onBackgroundDark,
    surface = coreGrey,
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = coreGrey,
    surfaceContainer = coreGrey,
    surfaceContainerHighest = coreDarkGrey,
)

val lightScheme = lightColorScheme(
    primary = coreOrange,
    onPrimary = Color.White,
    primaryContainer = coreOrange,
    onPrimaryContainer = Color.Black,
    secondary = coreOrange,
    onSecondary = Color.Black,
    secondaryContainer = coreOrange,
    onSecondaryContainer = Color.White,
    tertiary = coreOrange,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = error,
    onError = Color.White,
    errorContainer = error,
    onErrorContainer = Color.White,
    background = Color.White,
    onBackground = onBackgroundLight,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHighest = Color(0xFFF0F0F0),
)

val lightExtendedColors = ExtendedColors(
    primary20 = primary20Light,
    onPrimary20 = onPrimaryLight,
    warning = warningLight,
    onWarning = onWarningLight,
    success = successLight,
    onSuccess = onSuccessLight,
)

val darkExtendedColors = ExtendedColors(
    primary20 = primary20Dark,
    onPrimary20 = onPrimaryDark,
    warning = warningDark,
    onWarning = onWarningDark,
    success = successDark,
    onSuccess = onSuccessDark,
)

@Immutable
data class ColorFamily(
    val color: Color,
    val onColor: Color,
    val colorContainer: Color,
    val onColorContainer: Color
)

enum class CoreAppTheme(val resource: StringResource, val key: String) {
    Light(Res.string.light, "light"),
    Dark(Res.string.dark, "dark"),
    System(Res.string.system, "system"),
    ;

    companion object {
        fun String?.asCoreAppTheme(): CoreAppTheme =
            entries.firstOrNull { it.key == this } ?: System
    }
}

enum class CoreAppColorScheme {
    Light,
    Grey,
}

@Composable
fun currentColorScheme(): CoreAppColorScheme {
    val themeProvider: ThemeProvider = koinInject()
    val theme by themeProvider.theme.collectAsState()
    val systemInDarkTheme = isSystemInDarkTheme()
    val colorScheme = remember(theme, systemInDarkTheme) {
        when (theme) {
            CoreAppTheme.Light -> CoreAppColorScheme.Light
            CoreAppTheme.Dark -> CoreAppColorScheme.Grey
            CoreAppTheme.System -> if (systemInDarkTheme) CoreAppColorScheme.Grey else CoreAppColorScheme.Light
        }
    }
    return colorScheme
}

@Composable
fun AppTheme(
    content: @Composable() () -> Unit
) {
    val colorScheme = currentColorScheme()
    val extendedColors = if (colorScheme == CoreAppColorScheme.Grey) darkExtendedColors else lightExtendedColors
    setStatusBarTheme(colorScheme)
    val materialColorScheme = when (colorScheme) {
        CoreAppColorScheme.Light -> lightScheme
        CoreAppColorScheme.Grey -> greyScheme
    }
    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = AppTypography(),
            content = content
        )
    }
}

expect @Composable fun setStatusBarTheme(colorScheme: CoreAppColorScheme)

interface ThemeProvider {
    val theme: StateFlow<CoreAppTheme>
    fun setTheme(theme: CoreAppTheme)
}

class RealThemeProvider(
    private val settings: Settings,
) : ThemeProvider {
    private val _theme = MutableStateFlow(getTheme())
    override val theme: StateFlow<CoreAppTheme> = _theme.asStateFlow()

    private fun getTheme(): CoreAppTheme {
        val key = settings.getStringOrNull(THEME_SETTINGS_KEY)
        return key.asCoreAppTheme()
    }

    override fun setTheme(theme: CoreAppTheme) {
        settings.putString(THEME_SETTINGS_KEY, theme.key)
        _theme.value = theme
    }

    companion object {
        private const val THEME_SETTINGS_KEY = "current_theme"
    }
}

object ExtendedTheme {
    val colors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}