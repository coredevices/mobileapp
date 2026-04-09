package coredevices.coreapp.ui.screens

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import coredevices.util.Permission
import coredevices.util.description

actual fun Permission.descriptionOnboarding(): AnnotatedString = when (this) {
    Permission.Location -> buildAnnotatedString {
        append("Tap ")
        withStyle(HighlightStyle) {
            append("While using the app")
        }
        append(" to get accurate weather, sunrise/sunset and other info")
    }
    Permission.BackgroundLocation -> buildAnnotatedString {
        append("Tap ")
        withStyle(HighlightStyle) {
            append("Allow All The Time")
        }
        append(" to keep Pebble working correctly while the app is in the background")
    }
    else -> AnnotatedString(description())
}