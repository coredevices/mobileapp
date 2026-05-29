package coredevices.ring.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Design tokens scoped to the index feed screens. The shared M3 theme
 * is parameterised for the rest of the app — folding these tokens back
 * into it is a follow-up. New screens read colors via
 * `IndexColors.current` so hex literals stay in this one file.
 */
data class IndexColors(
    val isDark: Boolean,

    // Surfaces
    val surface: Color,
    val surfaceDim: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,

    // Foreground / text
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val scrim: Color,

    // Accent (Pebble red)
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    /** Tinted chip / source-card surface (#FFF1EE light / #2B1512 dark). */
    val redSurface: Color,
    /** Outline for action chips and red-tinted pills. Always the light-pink
     *  #FFDAD4 (matches the prototype's `border: #FFDAD4` for chips). */
    val chipOutline: Color,

    // Status
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
) {
    companion object {
        val Light = IndexColors(
            isDark = false,
            surface = Color(0xFFFDF8F6),
            surfaceDim = Color(0xFFEEE9E6),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF8F3F0),
            surfaceContainer = Color(0xFFF2EDEA),
            surfaceContainerHigh = Color(0xFFECE7E4),
            surfaceContainerHighest = Color(0xFFE6E1DE),
            onSurface = Color(0xFF1C1B1A),
            // Prototype "meta" (#7A756F) — section headers + subtle text.
            onSurfaceVariant = Color(0xFF7A756F),
            outline = Color(0xFF89847F),
            // Prototype divider (#ECE7E4) — list-row separators, card borders.
            outlineVariant = Color(0xFFECE7E4),
            scrim = Color(0x73000000),
            primary = Color(0xFFFA4A36),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDAD4),
            onPrimaryContainer = Color(0xFF410001),
            redSurface = Color(0xFFFFF1EE),
            chipOutline = Color(0xFFFFDAD4),
            error = Color(0xFFB3261E),
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
        )

        val Dark = IndexColors(
            isDark = true,
            surface = Color(0xFF1F1D1C),
            surfaceDim = Color(0xFF1A1917),
            surfaceContainerLowest = Color(0xFF161413),
            surfaceContainerLow = Color(0xFF252321),
            surfaceContainer = Color(0xFF2A2826),
            surfaceContainerHigh = Color(0xFF33302D),
            surfaceContainerHighest = Color(0xFF3B3835),
            onSurface = Color(0xFFF2EDEA),
            // Prototype "meta" (#8A8480) in dark mode.
            onSurfaceVariant = Color(0xFF8A8480),
            outline = Color(0xFF6B6765),
            // Prototype divider (#33302D) in dark mode.
            outlineVariant = Color(0xFF33302D),
            scrim = Color(0x99000000),
            primary = Color(0xFFFA4A36),
            onPrimary = Color(0xFFFFFFFF),
            // Prototype `#3a1812` for the dark-mode count chip background.
            primaryContainer = Color(0xFF3A1812),
            onPrimaryContainer = Color(0xFFFFDAD4),
            // Prototype `#2A1512` for chip / source-card surface in dark.
            redSurface = Color(0xFF2A1512),
            chipOutline = Color(0xFFFFDAD4),
            error = Color(0xFFF2B8B5),
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFF9DEDC),
        )
    }
}

val LocalIndexColors = staticCompositionLocalOf<IndexColors> { IndexColors.Light }

object IndexTheme {
    val colors: IndexColors
        @Composable
        @ReadOnlyComposable
        get() = LocalIndexColors.current
}

/**
 * Wrap a screen in [IndexThemeHost] to bind [IndexColors] to the system
 * dark/light setting (or a forced override). New screens use this; the rest
 * of the app keeps Material3 colors.
 */
@Composable
fun IndexThemeHost(
    forceDark: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val dark = forceDark ?: isSystemInDarkTheme()
    val colors = if (dark) IndexColors.Dark else IndexColors.Light
    androidx.compose.runtime.CompositionLocalProvider(
        LocalIndexColors provides colors,
        content = content,
    )
}
