package coredevices.coreapp.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import coreapp.composeapp.generated.resources.Res
import coreapp.composeapp.generated.resources.onboarding_android_background_location
import coreapp.composeapp.generated.resources.onboarding_android_background_location_highlight
import coreapp.composeapp.generated.resources.onboarding_android_location
import coreapp.composeapp.generated.resources.onboarding_android_location_highlight
import coredevices.util.Permission
import coredevices.util.description
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun Permission.descriptionOnboarding(): AnnotatedString = when (this) {
    Permission.Location -> {
        val highlight = stringResource(Res.string.onboarding_android_location_highlight)
        highlighted(stringResource(Res.string.onboarding_android_location, highlight), highlight)
    }
    Permission.BackgroundLocation -> {
        val highlight = stringResource(Res.string.onboarding_android_background_location_highlight)
        highlighted(stringResource(Res.string.onboarding_android_background_location, highlight), highlight)
    }
    else -> AnnotatedString(description())
}
