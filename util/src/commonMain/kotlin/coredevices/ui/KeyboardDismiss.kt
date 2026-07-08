package coredevices.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Dismisses the software keyboard when the user taps empty space.
 *
 * Apply to the root container of a screen, or to a dialog's surface: dialogs
 * render in their own window, so a screen-level modifier doesn't cover them.
 */
fun Modifier.dismissKeyboardOnTapOutside(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    pointerInput(focusManager, keyboard) {
        detectTapGestures {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
}
