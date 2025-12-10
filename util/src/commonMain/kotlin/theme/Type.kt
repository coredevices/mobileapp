package theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import coreapp.util.generated.resources.Inter
import coreapp.util.generated.resources.Res

@Composable
fun displayFontFamily() = FontFamily(
    Font(Res.font.Inter)
)

// Default Material 3 typography values
val baseline = Typography()

@Composable
fun AppTypography() = Typography().run {
    copy(
        displayLarge = baseline.displayLarge.copy(fontFamily = displayFontFamily()),
        displayMedium = baseline.displayMedium.copy(fontFamily = displayFontFamily()),
        displaySmall = baseline.displaySmall.copy(fontFamily = displayFontFamily()),
        headlineLarge = baseline.headlineLarge.copy(fontFamily = displayFontFamily()),
        headlineMedium = baseline.headlineMedium.copy(fontFamily = displayFontFamily(), fontWeight = FontWeight.SemiBold),
        headlineSmall = baseline.headlineSmall.copy(fontFamily = displayFontFamily()),
        titleLarge = baseline.titleLarge.copy(fontFamily = displayFontFamily()),
        //titleMedium = baseline.titleMedium.copy(fontFamily = displayFontFamily()),
        titleSmall = baseline.titleSmall.copy(fontFamily = displayFontFamily()),
    )
}

