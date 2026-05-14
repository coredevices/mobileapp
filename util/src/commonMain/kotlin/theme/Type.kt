package theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import coreapp.util.generated.resources.Inter
import coreapp.util.generated.resources.Res

@Composable
fun displayFontFamily(): FontFamily {
    // Font() is itself @Composable so must be called in composition, but its result is
    // stable, so we memoize the FontFamily wrapping it across recompositions.
    val font = Font(Res.font.Inter)
    return remember(font) { FontFamily(font) }
}

// Default Material 3 typography values
val baseline = Typography()

@Composable
fun AppTypography(): Typography {
    val fam = displayFontFamily()
    return remember(fam) {
        baseline.copy(
            displayLarge = baseline.displayLarge.copy(fontFamily = fam),
            displayMedium = baseline.displayMedium.copy(fontFamily = fam),
            displaySmall = baseline.displaySmall.copy(fontFamily = fam),
            headlineLarge = baseline.headlineLarge.copy(fontFamily = fam),
            headlineMedium = baseline.headlineMedium.copy(fontFamily = fam, fontWeight = FontWeight.SemiBold),
            headlineSmall = baseline.headlineSmall.copy(fontFamily = fam),
            titleLarge = baseline.titleLarge.copy(fontFamily = fam),
            //titleMedium = baseline.titleMedium.copy(fontFamily = fam),
            titleSmall = baseline.titleSmall.copy(fontFamily = fam),
        )
    }
}

