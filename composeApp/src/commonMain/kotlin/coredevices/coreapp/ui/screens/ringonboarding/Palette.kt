package coredevices.coreapp.ui.screens.ringonboarding

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

internal data class Palette(
    val surface: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val error: Color,
)

internal val LightPalette = Palette(
    surface = Color(0xFFFDF8F6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F3F0),
    surfaceContainer = Color(0xFFF2EDEA),
    surfaceContainerHigh = Color(0xFFECE7E4),
    onSurface = Color(0xFF1C1B1A),
    onSurfaceVariant = Color(0xFF57524F),
    outline = Color(0xFF89847F),
    outlineVariant = Color(0xFFD4CFCC),
    primary = Color(0xFFFA4A36),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD4),
    onPrimaryContainer = Color(0xFF410001),
    error = Color(0xFFB3261E),
)

internal val DarkPalette = Palette(
    surface = Color(0xFF1F1D1C),
    surfaceContainerLowest = Color(0xFF161413),
    surfaceContainerLow = Color(0xFF252321),
    surfaceContainer = Color(0xFF2A2826),
    surfaceContainerHigh = Color(0xFF33302D),
    onSurface = Color(0xFFF2EDEA),
    onSurfaceVariant = Color(0xFFB5AFAB),
    outline = Color(0xFF6B6765),
    outlineVariant = Color(0xFF454240),
    primary = Color(0xFFFA4A36),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF5A1F17),
    onPrimaryContainer = Color(0xFFFFDAD4),
    error = Color(0xFFF2B8B5),
)

internal val LocalPalette = compositionLocalOf { LightPalette }